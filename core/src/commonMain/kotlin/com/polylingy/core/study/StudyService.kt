package com.polylingy.core.study

import com.polylingy.content.Exercise
import com.polylingy.content.ItemCheck
import com.polylingy.content.Response
import com.polylingy.core.content.Course
import com.polylingy.core.content.CourseUnit
import com.polylingy.core.content.Topic
import com.polylingy.core.journal.AnswerSubmitted
import com.polylingy.core.journal.EventEnvelope
import com.polylingy.core.learning.Candidate
import com.polylingy.core.learning.CourseRules
import com.polylingy.core.learning.TopicStatus
import com.polylingy.core.learning.replay
import com.polylingy.core.learning.status
import com.polylingy.core.learning.toRules
import com.polylingy.core.ports.Clock
import com.polylingy.core.ports.CourseRepository
import com.polylingy.core.ports.EventLog
import com.polylingy.core.rules.CheckSettings
import com.polylingy.core.rules.Checking
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.random.Random
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

data class TopicProgress(val topic: Topic, val status: TopicStatus)

/** A unit is learned when all its topics are; [started] — some topic of it has been answered. */
data class UnitProgress(val unit: CourseUnit, val topics: List<TopicProgress>) {
    val learned: Boolean get() = topics.all { it.status == TopicStatus.Learned }
    val started: Boolean get() = topics.any { it.status != TopicStatus.New }
}

/** Every topic's status today: by unit for a course with units, flat for one without. */
data class CourseProgress(val course: Course, val units: List<UnitProgress>, val topics: List<TopicProgress>) {
    val allTopics: List<TopicProgress> get() = units.flatMap { it.topics } + topics
}

/** What to show: an exercise of a topic, with the unit it belongs to. */
data class NextExercise(val topic: Topic, val unit: CourseUnit?, val exercise: Exercise)

/** A checked answer: one check per item, and the topic's status before and after it, for "passed" or "learned". */
data class AnswerOutcome(val checks: List<ItemCheck>, val before: TopicStatus, val after: TopicStatus)

/** Use cases of the study screens; the learning rules all live behind this class. */
class StudyService(
    private val courses: CourseRepository,
    private val journal: EventLog,
    private val clock: Clock,
    private val deviceId: Uuid,
    private val random: Random = Random.Default,
) {
    fun progress(courseId: Uuid): CourseProgress? {
        val course = courses.get(courseId) ?: return null
        val statuses = statuses(course)
        fun of(topics: List<Topic>) = topics.map { TopicProgress(it, statuses.getValue(it.id)) }
        return CourseProgress(course, course.units.map { UnitProgress(it, of(it.topics)) }, of(course.topics))
    }

    /**
     * The next exercise the course's picker chooses — from one unit, or from the whole course in unit order —
     * or null when nothing is left for today. [current] is the topic just shown, [lastExercise] the exercise just shown.
     */
    fun next(courseId: Uuid, unitId: Uuid? = null, current: Uuid? = null, lastExercise: Uuid? = null): NextExercise? {
        val course = courses.get(courseId) ?: return null
        return nextWith(course, course.rules(), unitId, current, lastExercise)
    }

    private fun <M, G> nextWith(course: Course, rules: CourseRules<M, G>, unitId: Uuid?, current: Uuid?, lastExercise: Uuid?): NextExercise? {
        val states = replay(course, journal.all(), rules)
        val today = clock.now().localDate
        val topics = (if (unitId != null) course.units.firstOrNull { it.id == unitId }?.topics.orEmpty() else course.allTopics)
            .filter { it.playable().isNotEmpty() }
        val candidates = topics.mapNotNull { topic ->
            val state = states.getValue(topic.id)
            Candidate(topic.id, state.phase, state.round.isNotEmpty()).takeIf { state.status(rules.scheduler, today).available }
        }
        val picked = rules.picker.pick(candidates, current) ?: return null.also { log.debug { "Nothing left today in course ${course.id}" } }
        log.debug { "Picked topic $picked from ${candidates.size} candidates" }
        return exerciseOf(course, topics.first { it.id == picked }, lastExercise)
    }

    /** An exercise of a topic the learner chose, whatever its status: practising early counts as a round. */
    fun practise(courseId: Uuid, topicId: Uuid, lastExercise: Uuid? = null): NextExercise? {
        val course = courses.get(courseId) ?: return null
        val topic = course.allTopics.firstOrNull { it.id == topicId } ?: return null
        return exerciseOf(course, topic, lastExercise)
    }

    /** Checks an answer and writes it to the journal. */
    fun answer(courseId: Uuid, topicId: Uuid, exerciseId: Uuid, response: Response): AnswerOutcome {
        val course = requireNotNull(courses.get(courseId)) { "No course $courseId" }
        val topic = requireNotNull(course.allTopics.firstOrNull { it.id == topicId }) { "No topic $topicId in course $courseId" }
        val exercise = requireNotNull(topic.exercises.firstOrNull { it.id == exerciseId }) { "No exercise $exerciseId in topic $topicId" }
        val before = statuses(course).getValue(topicId)
        val checks = Checking.check(exercise, response, CheckSettings(course.settings.allowTypos))
        val now = clock.now()
        journal.append(
            EventEnvelope(
                Uuid.random(), deviceId, now,
                event = AnswerSubmitted(courseId, topicId, exerciseId, course.revision, response, checks.map { it.result }),
            ),
        )
        val after = statuses(course).getValue(topicId)
        log.info { "Answer in course $courseId, topic $topicId \"${topic.title}\", exercise $exerciseId: ${checks.map { it.result }}; $before → $after" }
        return AnswerOutcome(checks, before, after)
    }

    /** Whether to open a unit with its theory: nothing in it has been answered yet. */
    fun theoryFirst(courseId: Uuid, unitId: Uuid): Boolean {
        val progress = progress(courseId) ?: return false
        val unit = progress.units.firstOrNull { it.unit.id == unitId } ?: return false
        return unit.unit.theory != null && !unit.started
    }

    private fun Course.rules(): CourseRules<*, *> = rules.toRules(random)

    private fun statuses(course: Course): Map<Uuid, TopicStatus> = statusesWith(course, course.rules())

    private fun <M, G> statusesWith(course: Course, rules: CourseRules<M, G>): Map<Uuid, TopicStatus> {
        val today = clock.now().localDate
        return replay(course, journal.all(), rules).mapValues { it.value.status(rules.scheduler, today) }
    }

    private fun exerciseOf(course: Course, topic: Topic, lastExercise: Uuid?): NextExercise? {
        val playable = topic.playable()
        if (playable.isEmpty()) return null
        val exercise = (playable.filter { it.id != lastExercise }.ifEmpty { playable }).random(random)
        return NextExercise(topic, course.units.firstOrNull { unit -> unit.topics.any { it.id == topic.id } }, exercise)
    }

    /** Exercises this version can show: not of a type from a newer format. */
    private fun Topic.playable() = exercises.filterNot { it is Exercise.Unknown }
}

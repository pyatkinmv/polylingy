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
import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

/**
 * Where a topic stands, decided only by what replay() knows: whether it has memory (passed at least once) and whether
 * a round is under way. A mistake in a learned topic never makes it new or learning again: it waits in [TO_REVIEW].
 */
enum class TopicGroup {
    /** No memory, no round. */
    NEW,

    /** No memory, a first round under way. */
    LEARNING,

    /** Memory, and it is due or a review round is under way. */
    TO_REVIEW,

    /** Memory, resting until its next review. */
    LEARNED,

    /** Memory, and the schedule has no next review. */
    LEARNED_FOR_GOOD,
}

/** A topic's status today; [studied] — it has been passed at least once (it has memory). */
data class TopicProgress(val topic: Topic, val status: TopicStatus, val studied: Boolean) {
    val group: TopicGroup
        get() = when (status) {
            TopicStatus.New -> TopicGroup.NEW
            TopicStatus.InProgress -> if (studied) TopicGroup.TO_REVIEW else TopicGroup.LEARNING
            TopicStatus.Due -> TopicGroup.TO_REVIEW
            is TopicStatus.Passed -> TopicGroup.LEARNED
            TopicStatus.Learned -> TopicGroup.LEARNED_FOR_GOOD
        }
}

/**
 * How a set of topics stands today, for a course or unit card: the size of each [TopicGroup], and [newToday] —
 * how many new topics may still be begun today under the course's daily limit, or null when it has none.
 */
data class ProgressCounts(
    val learnedForGood: Int,
    val resting: Int,
    val toReview: Int,
    val learning: Int,
    val new: Int,
    val newToday: Int?,
    /** The earliest day a resting topic comes back, if any. */
    val nextReview: LocalDate?,
) {
    val total: Int get() = learnedForGood + resting + toReview + learning + new

    /** Passed at least once: learned for good, resting, or to review. */
    val learned: Int get() = learnedForGood + resting + toReview

    /** New topics to offer now: up to the daily limit, or all that are left when there is none. */
    val newNow: Int get() = newToday ?: new

    /** Nothing to review, no first round to finish, no new topic to begin today. */
    val done: Boolean get() = toReview == 0 && learning == 0 && newNow == 0

    companion object {
        fun of(topics: List<TopicProgress>, newToday: Int? = null): ProgressCounts {
            val groups = topics.groupingBy { it.group }.eachCount()
            fun count(group: TopicGroup) = groups[group] ?: 0
            val new = count(TopicGroup.NEW)
            return ProgressCounts(
                learnedForGood = count(TopicGroup.LEARNED_FOR_GOOD),
                resting = count(TopicGroup.LEARNED),
                toReview = count(TopicGroup.TO_REVIEW),
                learning = count(TopicGroup.LEARNING),
                new = new,
                newToday = newToday?.coerceAtMost(new),
                nextReview = topics.map { it.status }.filterIsInstance<TopicStatus.Passed>().minOfOrNull { it.due },
            )
        }
    }
}

/**
 * A unit is learned when all its topics are; [started] — some topic of it has been answered. Its counts have no
 * [ProgressCounts.newToday]: the daily limit belongs to the whole course.
 */
data class UnitProgress(val unit: CourseUnit, val topics: List<TopicProgress>) {
    val learned: Boolean get() = topics.all { it.status == TopicStatus.Learned }
    val started: Boolean get() = topics.any { it.status != TopicStatus.New }
    val counts: ProgressCounts get() = ProgressCounts.of(topics)
}

/**
 * Every topic's status today: by unit for a course with units, flat for one without. [newToday] — new topics that
 * may still be begun today under the course's daily limit; null when it has none.
 */
data class CourseProgress(val course: Course, val units: List<UnitProgress>, val topics: List<TopicProgress>, val newToday: Int? = null) {
    val allTopics: List<TopicProgress> get() = units.flatMap { it.topics } + topics
    val counts: ProgressCounts get() = ProgressCounts.of(allTopics, newToday)
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
        val progress = topicProgress(course)
        fun of(topics: List<Topic>) = topics.map { progress.getValue(it.id) }
        return CourseProgress(course, course.units.map { UnitProgress(it, of(it.topics)) }, of(course.topics), newToday(course))
    }

    /** New topics that may still be begun today: the daily limit minus those begun today; null without a limit. */
    private fun newToday(course: Course): Int? =
        course.settings.newPerDay?.let { limit -> (limit - begunToday(course).size).coerceAtLeast(0) }

    /** Topics of the course whose very first answer was given today: these are the new topics begun today. */
    private fun begunToday(course: Course): Set<Uuid> {
        val today = clock.now().localDate
        val topicIds = course.allTopics.mapTo(mutableSetOf()) { it.id }
        return journal.all()
            .mapNotNull { envelope -> (envelope.event as? AnswerSubmitted)?.takeIf { it.courseId == course.id && it.topicId in topicIds }?.let { it.topicId to envelope.occurredAt } }
            .groupBy({ it.first }, { it.second })
            .filterValues { times -> times.minBy { it.instant }.localDate == today }
            .keys
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
        // The daily limit: once today's new topics are begun, untouched ones wait for another day.
        val noMoreNew = newToday(course) == 0
        val candidates = topics.mapNotNull { topic ->
            val state = states.getValue(topic.id)
            val status = state.status(rules.scheduler, today)
            Candidate(topic.id, state.phase, state.round.isNotEmpty()).takeIf { status.available && !(noMoreNew && status == TopicStatus.New) }
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

    private fun statuses(course: Course): Map<Uuid, TopicStatus> = topicProgress(course).mapValues { it.value.status }

    private fun topicProgress(course: Course): Map<Uuid, TopicProgress> = progressWith(course, course.rules())

    private fun <M, G> progressWith(course: Course, rules: CourseRules<M, G>): Map<Uuid, TopicProgress> {
        val today = clock.now().localDate
        val states = replay(course, journal.all(), rules)
        return course.allTopics.associate { topic ->
            val state = states.getValue(topic.id)
            topic.id to TopicProgress(topic, state.status(rules.scheduler, today), studied = state.memory != null)
        }
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

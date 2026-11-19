package com.polylingy.core.study

import com.polylingy.content.Exercise
import com.polylingy.content.ItemCheck
import com.polylingy.content.Response
import com.polylingy.core.content.Course
import com.polylingy.core.content.CourseUnit
import com.polylingy.core.content.DisplaySpec
import com.polylingy.core.content.Topic
import com.polylingy.core.journal.AnswerSubmitted
import com.polylingy.core.journal.EventEnvelope
import com.polylingy.core.journal.PracticeAnswered
import com.polylingy.content.Path
import com.polylingy.core.content.RulesSpec
import com.polylingy.core.content.of
import com.polylingy.core.learning.Candidate
import com.polylingy.core.learning.Group
import com.polylingy.core.learning.CourseRules
import com.polylingy.core.learning.PassFail
import com.polylingy.core.learning.TopicAnswer
import com.polylingy.core.learning.TopicStatus
import com.polylingy.core.learning.replay
import com.polylingy.core.learning.status
import com.polylingy.core.learning.toRules
import com.polylingy.core.ports.Clock
import com.polylingy.core.ports.CourseRepository
import com.polylingy.core.ports.CourseSettingsRepository
import com.polylingy.core.ports.EventLog
import com.polylingy.core.rules.Checking
import com.polylingy.core.rules.forgiving
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
 * Every topic's status today: by unit for a course with units, flat for one without. [newPerDay] — the course's daily
 * limit of new topics, [newToday] — how many may still be begun today; both null when it has none.
 */
data class CourseProgress(
    val course: Course,
    val units: List<UnitProgress>,
    val topics: List<TopicProgress>,
    val newToday: Int? = null,
    val newPerDay: Int? = null,
) {
    val allTopics: List<TopicProgress> get() = units.flatMap { it.topics } + topics
    val counts: ProgressCounts get() = ProgressCounts.of(allTopics, newToday)
}

/** What to show: an exercise of a topic, with the unit it belongs to. */
data class NextExercise(val topic: Topic, val unit: CourseUnit?, val exercise: Exercise)

/** A checked answer of practice: [outcome] as the learner sees it (the status unchanged) and whether it [passed] by the course's grading. */
data class PracticeResult(val outcome: AnswerOutcome, val passed: Boolean)

/** A checked answer: one check per item, and the topic's status before and after it, for "passed" or "learned". */
data class AnswerOutcome(val checks: List<ItemCheck>, val before: TopicStatus, val after: TopicStatus)

/** Where extra practice goes over: the whole course, one unit, or one topic. */
sealed interface PracticeScope {
    data object Course : PracticeScope
    data class Unit(val unitId: Uuid) : PracticeScope
    data class Topic(val topicId: Uuid) : PracticeScope
}

/** One answer of a practice session, for [PracticeState]. */
data class PracticeAnswer(val topicId: Uuid, val exerciseId: Uuid, val passed: Boolean)

/** What a practice session has done so far, oldest answer first, and the topic it is on. It lives only as long as the session. */
data class PracticeState(val answers: List<PracticeAnswer> = emptyList(), val current: Uuid? = null)

/** Use cases of the study screens; the learning rules all live behind this class. */
class StudyService(
    private val courses: CourseRepository,
    private val settings: CourseSettingsRepository,
    private val journal: EventLog,
    private val clock: Clock,
    private val deviceId: Uuid,
    private val random: Random = Random.Default,
) {
    fun progress(courseId: Uuid): CourseProgress? {
        val course = courses.get(courseId) ?: return null
        return progressWith(course, settings.of(course))
    }

    /** The course's settings: its policies, every part written out. */
    fun settings(courseId: Uuid): RulesSpec? = courses.get(courseId)?.let { settings.of(it) }

    /** How the course would stand with [rules] instead of its settings — for the settings screen, before saving. */
    fun progressWith(courseId: Uuid, rules: RulesSpec): CourseProgress? = courses.get(courseId)?.let { progressWith(it, rules.resolved()) }

    /**
     * Saves the course's settings; progress is then recounted from the whole journal by them. Problems in [rules] are an
     * [IllegalArgumentException]. Not for a course from a newer format.
     */
    fun saveSettings(courseId: Uuid, rules: RulesSpec) {
        val course = requireNotNull(courses.get(courseId)) { "No course $courseId" }
        require(!course.studyOnly) { "\"${course.name}\" was made with a newer version of Polylingy and can't be changed" }
        val resolved = rules.resolved()
        resolved.validate(Path("rules")).firstOrNull()?.let { throw IllegalArgumentException("${it.path}: ${it.message}") }
        settings.save(courseId, resolved, clock.now().instant)
        log.info { "Saved settings of course $courseId: $resolved" }
    }

    private fun progressWith(course: Course, rules: RulesSpec): CourseProgress {
        val progress = topicProgress(course, rules)
        fun of(topics: List<Topic>) = topics.map { progress.getValue(it.id) }
        val newPerDay = rules.resolved().picker?.newPerDay
        return CourseProgress(course, course.units.map { UnitProgress(it, of(it.topics)) }, of(course.topics), newToday(course, newPerDay), newPerDay)
    }

    /** New topics that may still be begun today: the daily limit minus those begun today; null without a limit. */
    private fun newToday(course: Course, newPerDay: Int?): Int? =
        newPerDay?.let { limit -> (limit - begunToday(course).size).coerceAtLeast(0) }

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

    /**
     * Builds the candidates from replay() and asks the picker's three parts in turn: the queue (which topics and in what
     * order), the focus (stay or move on), the exercise choice. Nothing is decided here.
     */
    private fun <M> nextWith(course: Course, rules: CourseRules<M, PassFail>, unitId: Uuid?, current: Uuid?, lastExercise: Uuid?): NextExercise? {
        val states = replay(course, journal.all(), rules)
        val today = clock.now().localDate
        val topics = (if (unitId != null) course.units.firstOrNull { it.id == unitId }?.topics.orEmpty() else course.allTopics)
            .filter { it.playable().isNotEmpty() }
        val candidates = topics.mapNotNull { topic ->
            val state = states.getValue(topic.id)
            val due = state.memory?.let { rules.scheduler.dueDate(it) }
            when (state.status(rules.scheduler, today)) {
                TopicStatus.InProgress -> Candidate(topic.id, Group.STARTED, due)
                TopicStatus.Due -> Candidate(topic.id, Group.DUE, due)
                TopicStatus.New -> Candidate(topic.id, Group.NEW)
                is TopicStatus.Passed, TopicStatus.Learned -> null
            }
        }
        val queue = rules.picker.queue.build(candidates, begunToday(course).size)
        val picked = rules.picker.focus.pick(queue, current) ?: return null.also { log.debug { "Nothing left today in course ${course.id}" } }
        log.debug { "Picked topic $picked from a queue of ${queue.size}" }
        val topic = topics.first { it.id == picked }
        val history = historyOf(course, topic, rules, states.getValue(topic.id).round.size, lastExercise)
        return NextExercise(topic, unitOf(course, topic), rules.picker.exercise.choose(topic.offered(course.display()), history))
    }

    /** An exercise of a topic the learner chose, whatever its status: practising early counts as a round. */
    fun practise(courseId: Uuid, topicId: Uuid, lastExercise: Uuid? = null): NextExercise? {
        val course = courses.get(courseId) ?: return null
        val topic = course.allTopics.firstOrNull { it.id == topicId }?.takeIf { it.playable().isNotEmpty() } ?: return null
        return practiseWith(course, topic, course.rules(), lastExercise)
    }

    private fun <M> practiseWith(course: Course, topic: Topic, rules: CourseRules<M, PassFail>, lastExercise: Uuid?): NextExercise {
        val roundSize = replay(course, journal.all(), rules).getValue(topic.id).round.size
        val exercise = rules.picker.exercise.choose(topic.offered(course.display()), historyOf(course, topic, rules, roundSize, lastExercise))
        return NextExercise(topic, unitOf(course, topic), exercise)
    }

    /**
     * What [ExerciseChoice] is told about the topic: its earlier answers, oldest first, each with whether it passed by the course's
     * grading and whether it belongs to the topic's current round ([roundSize] of its last counted answers). An exercise shown
     * on the screen but not in the journal (the [lastExercise]) counts as the last answer, so it is not shown twice in a row.
     */
    private fun <M> historyOf(course: Course, topic: Topic, rules: CourseRules<M, PassFail>, roundSize: Int, lastExercise: Uuid?): List<TopicAnswer> {
        val ids = topic.exercises.mapTo(mutableSetOf()) { it.id }
        val graded = journal.all().sortedBy { it.occurredAt.instant }
            .mapNotNull { e -> (e.event as? AnswerSubmitted)?.takeIf { it.courseId == course.id && it.topicId == topic.id && it.exerciseId in ids } }
            .mapNotNull { a -> rules.grading.grade(a)?.let { a to it } }
        val firstOfRound = graded.size - roundSize
        val history = graded.mapIndexed { i, (a, grade) -> TopicAnswer(a.exerciseId, grade == PassFail.PASS, i >= firstOfRound) }
        return if (lastExercise != null && history.lastOrNull()?.exerciseId != lastExercise) history + TopicAnswer(lastExercise, true, false) else history
    }

    /**
     * Checks an answer and writes it to the journal. The journal gets the plain check, typos included; the returned
     * checks are as the learner sees them — a typo the course doesn't forgive is shown as wrong.
     */
    fun answer(courseId: Uuid, topicId: Uuid, exerciseId: Uuid, response: Response): AnswerOutcome {
        val course = requireNotNull(courses.get(courseId)) { "No course $courseId" }
        val topic = requireNotNull(course.allTopics.firstOrNull { it.id == topicId }) { "No topic $topicId in course $courseId" }
        val exercise = requireNotNull(topic.exercises.firstOrNull { it.id == exerciseId }) { "No exercise $exerciseId in topic $topicId" }
        val before = statuses(course).getValue(topicId)
        val checks = Checking.check(exercise, response)
        val now = clock.now()
        journal.append(
            EventEnvelope(
                Uuid.random(), deviceId, now,
                event = AnswerSubmitted(courseId, topicId, exerciseId, course.version, response, checks.map { it.result }),
            ),
        )
        val after = statuses(course).getValue(topicId)
        log.info { "Answer in course $courseId, topic $topicId \"${topic.title}\", exercise $exerciseId: ${checks.map { it.result }}; $before → $after" }
        return AnswerOutcome(checks.forgiving(forgivesTypos(course)), before, after)
    }

    // ---- extra practice: apart from the plan (docs/learning-model.md, "Extra practice")

    /**
     * The topics extra practice in [scope] can go over: those the plan has already started (answered at least once) and that
     * have an exercise this version can show; for a [PracticeScope.Topic] that topic.
     */
    fun practiceTopics(courseId: Uuid, scope: PracticeScope): List<Topic> {
        val course = courses.get(courseId) ?: return emptyList()
        val inScope = when (scope) {
            PracticeScope.Course -> course.allTopics
            is PracticeScope.Unit -> course.units.firstOrNull { it.id == scope.unitId }?.topics.orEmpty()
            is PracticeScope.Topic -> return listOfNotNull(course.allTopics.firstOrNull { it.id == scope.topicId }?.takeIf { it.playable().isNotEmpty() })
        }
        val started = journal.all().mapNotNullTo(mutableSetOf()) { e -> (e.event as? AnswerSubmitted)?.takeIf { it.courseId == courseId }?.topicId }
        return inScope.filter { it.id in started && it.playable().isNotEmpty() }
    }

    fun canPractise(courseId: Uuid, scope: PracticeScope): Boolean = practiceTopics(courseId, scope).isNotEmpty()

    /**
     * The next exercise of a practice session, or null when there is nothing to practise. It uses the course's own pickers (the
     * queue orders the topics, the focus stays on a topic or moves on, the exercise choice picks the exercise), but its own set of
     * candidates and its own round: a *pass*, in which every exercise of a topic comes once. The topics with the fewest finished
     * passes are the candidates, so the session goes round the topics and then starts again; it never ends by itself.
     * Only [state], the session's own answers, is read: the plan's journal is not.
     */
    fun practiceNext(courseId: Uuid, scope: PracticeScope, state: PracticeState): NextExercise? {
        val course = courses.get(courseId) ?: return null
        return practiceNextWith(course, practiceTopics(courseId, scope), course.rules(), state)
    }

    private fun <M> practiceNextWith(course: Course, topics: List<Topic>, rules: CourseRules<M, PassFail>, state: PracticeState): NextExercise? {
        if (topics.isEmpty()) return null
        val display = course.display()
        // for each topic: the exercises shown in its current pass, and how many passes are finished
        class Pass(val topic: Topic, val offered: List<Exercise>, val shown: Set<Uuid>, val finished: Int)
        val passes = topics.map { topic ->
            val offered = topic.offered(display)
            var shown = emptySet<Uuid>()
            var finished = 0
            for (a in state.answers) if (a.topicId == topic.id) {
                shown = shown + a.exerciseId
                if (shown.containsAll(offered.map { it.id })) { finished++; shown = emptySet() }
            }
            Pass(topic, offered, shown, finished)
        }
        val fewest = passes.minOf { it.finished }
        // the topic whose pass has just been finished lets another one have its turn, whichever focus the course has
        val justFinished = state.current?.takeIf { id -> passes.first { it.topic.id == id }.let { it.shown.isEmpty() && it.finished > 0 } }
        val candidates = passes.filter { it.finished == fewest }.let { all -> all.filter { it.topic.id != justFinished }.ifEmpty { all } }
        val queue = rules.picker.queue.build(candidates.map { Candidate(it.topic.id, Group.STARTED) }, 0)
        val picked = rules.picker.focus.pick(queue, state.current) ?: return null
        val pass = candidates.first { it.topic.id == picked }
        val pool = pass.offered.filter { it.id !in pass.shown }.ifEmpty { pass.offered }
        val history = state.answers.filter { it.topicId == picked }.map { TopicAnswer(it.exerciseId, it.passed, inCurrentRound = true) }
        return NextExercise(pass.topic, unitOf(course, pass.topic), rules.picker.exercise.choose(pool, history))
    }

    /**
     * Checks an answer of practice and writes it to the journal as a [PracticeAnswered]. The topic's status is told as it is
     * (before and after the same), because practice changes none; [PracticeResult.passed] is by the course's grading.
     */
    fun practiceAnswer(courseId: Uuid, topicId: Uuid, exerciseId: Uuid, response: Response): PracticeResult {
        val course = requireNotNull(courses.get(courseId)) { "No course $courseId" }
        val topic = requireNotNull(course.allTopics.firstOrNull { it.id == topicId }) { "No topic $topicId in course $courseId" }
        val exercise = requireNotNull(topic.exercises.firstOrNull { it.id == exerciseId }) { "No exercise $exerciseId in topic $topicId" }
        val checks = Checking.check(exercise, response)
        val event = PracticeAnswered(courseId, topicId, exerciseId, course.version, response, checks.map { it.result })
        journal.append(EventEnvelope(Uuid.random(), deviceId, clock.now(), event = event))
        val status = statuses(course).getValue(topicId)
        val passed = course.rules().grading.grade(event.asAnswer()) == PassFail.PASS
        log.info { "Practice answer in course $courseId, topic $topicId \"${topic.title}\", exercise $exerciseId: ${checks.map { it.result }}" }
        return PracticeResult(AnswerOutcome(checks.forgiving(forgivesTypos(course)), status, status), passed)
    }

    /** Checks an answer as [answer] would show it, without writing anything — for previews. */
    fun preview(courseId: Uuid, exercise: Exercise, response: Response): List<ItemCheck> {
        val typos = courses.get(courseId)?.let(::forgivesTypos) ?: true
        return Checking.check(exercise, response).forgiving(typos)
    }

    /** Whether to open a unit with its theory: nothing in it has been answered yet. */
    fun theoryFirst(courseId: Uuid, unitId: Uuid): Boolean {
        val progress = progress(courseId) ?: return false
        val unit = progress.units.firstOrNull { it.unit.id == unitId } ?: return false
        return unit.unit.theory != null && !unit.started
    }

    private fun Course.rules(): CourseRules<*, PassFail> = settings.of(this).toRules(random)

    private fun Course.display(): DisplaySpec = settings.of(this).resolved().display ?: DisplaySpec()

    private fun forgivesTypos(course: Course): Boolean = settings.of(course).resolved().grading?.typos ?: true

    private fun statuses(course: Course): Map<Uuid, TopicStatus> = topicProgress(course, settings.of(course)).mapValues { it.value.status }

    private fun topicProgress(course: Course, rules: RulesSpec): Map<Uuid, TopicProgress> = topicProgressWith(course, rules.toRules(random))

    private fun <M, G> topicProgressWith(course: Course, rules: CourseRules<M, G>): Map<Uuid, TopicProgress> {
        val today = clock.now().localDate
        val states = replay(course, journal.all(), rules)
        return course.allTopics.associate { topic ->
            val state = states.getValue(topic.id)
            topic.id to TopicProgress(topic, state.status(rules.scheduler, today), studied = state.memory != null)
        }
    }

    private fun unitOf(course: Course, topic: Topic): CourseUnit? = course.units.firstOrNull { unit -> unit.topics.any { it.id == topic.id } }

    /** Exercises this version can show: not of a type from a newer format. */
    private fun Topic.playable() = exercises.filterNot { it is Exercise.Unknown }

    /** The exercises to choose from: with "avoid typing", those without a keyboard if the topic has any. */
    private fun Topic.offered(display: DisplaySpec): List<Exercise> {
        val all = playable()
        return if (display.avoidTyping) all.filterNot { it.needsKeyboard }.ifEmpty { all } else all
    }
}

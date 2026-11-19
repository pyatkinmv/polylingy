package com.polylingy.core.study

import com.polylingy.content.ItemResult
import com.polylingy.core.content.Course
import com.polylingy.core.content.of
import com.polylingy.core.journal.AnswerSubmitted
import com.polylingy.core.journal.EventEnvelope
import com.polylingy.core.learning.CourseRules
import com.polylingy.core.learning.PassFail
import com.polylingy.core.learning.TopicStatus
import com.polylingy.core.learning.replay
import com.polylingy.core.learning.toRules
import com.polylingy.core.ports.Clock
import com.polylingy.core.ports.CourseRepository
import com.polylingy.core.ports.CourseSettingsRepository
import com.polylingy.core.ports.EventLog
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.random.Random
import kotlin.uuid.Uuid

/**
 * What happened on one day: [answers] (one per exercise answered), how many of them [passed] by the course's grading rule,
 * how many [withTypos] had a typo in some item (a typo is a fact of the journal; whether it counts as right is the course's
 * grading) and how many [newTopics] were begun that day (their very first answer).
 */
data class DayActivity(val answers: Int, val passed: Int, val withTypos: Int, val newTopics: Int = 0) {
    val failed: Int get() = answers - passed
}

/** How many topics are to be reviewed on [date]: today's count includes those overdue or in a review round. */
data class ReviewDay(val date: LocalDate, val topics: Int)

/**
 * Today's work. [newBegun] — new topics begun today and [newPerDay] — the limit of them, summed over the courses, null when some
 * course has no limit (then there is no goal for the day). [toReview] and [learning] are what is waiting now.
 */
data class TodayWork(val answers: Int, val newBegun: Int, val newPerDay: Int?, val toReview: Int, val learning: Int)

/** Everything the statistics screen shows, for one course or for all of them; see [StatisticsService]. */
data class Statistics(
    val today: LocalDate,
    /** Only days with answers; the screen fills the rest with zeros. */
    val days: Map<LocalDate, DayActivity>,
    /** Days in a row with answers up to today; a day without any yet does not break it before the day is over. */
    val streak: Int,
    val bestStreak: Int,
    val totalAnswers: Int,
    val daysActive: Int,
    /** Share of all answers that passed; null with no answers. */
    val passedShare: Double?,
    val work: TodayWork,
    /** Topics passed at least once now, and a week ago, of [topicsTotal]. */
    val learned: Int,
    val learnedWeekAgo: Int,
    val topicsTotal: Int,
    /** Today and the next six days. */
    val forecast: List<ReviewDay>,
)

/**
 * Numbers about the learner's work, all derived from the journal — nothing is stored. Whether an answer passed is the course's
 * own grading rule ([com.polylingy.core.learning.GradingRule]), so changing the course's settings changes these numbers like it
 * changes the progress. "How things stood on a day" is [replay] of the journal up to that day with the course's current policies.
 * Only courses that still exist count; answers to deleted ones stay in the journal but not here.
 */
class StatisticsService(
    private val courses: CourseRepository,
    private val settings: CourseSettingsRepository,
    private val journal: EventLog,
    private val clock: Clock,
    private val study: StudyService,
    private val random: Random = Random.Default,
) {
    /** [courseId] — one course, or all of them when null. */
    fun statistics(courseId: Uuid? = null): Statistics {
        val today = clock.now().localDate
        val scope = scope(courseId)
        val answers = answersOf(scope)
        val rules = scope.associate { it.id to settings.of(it).toRules(random) }
        fun passed(answer: AnswerSubmitted) = rules.getValue(answer.courseId).grading.grade(answer) == PassFail.PASS

        val firstDay = firstAnswerDays(answers)
        val begun = firstDay.values.groupingBy { it }.eachCount()
        val days = answers.groupBy { (envelope, _) -> envelope.occurredAt.localDate }.mapValues { (date, day) -> activity(day.map { it.second }, ::passed, begun[date] ?: 0) }
        val active = days.keys
        val total = days.values.sumOf { it.answers }
        val progress = scope.mapNotNull { study.progress(it.id) }

        val weekAgo = today.minus(7, DateTimeUnit.DAY)
        val learned = progress.sumOf { it.counts.learned }
        val learnedWeekAgo = scope.sumOf { course ->
            learnedCount(course, answers.filter { (e, a) -> a.courseId == course.id && e.occurredAt.localDate <= weekAgo }.map { it.first }, rules.getValue(course.id))
        }

        val limits = scope.map { settings.of(it).resolved().picker?.newPerDay }
        val work = TodayWork(
            answers = days[today]?.answers ?: 0,
            newBegun = firstDay.values.count { it == today },
            newPerDay = if (limits.any { it == null }) null else limits.sumOf { it!! },
            toReview = progress.sumOf { it.counts.toReview },
            learning = progress.sumOf { it.counts.learning },
        )
        return Statistics(
            today, days, streak(active, today), bestStreak(active), total, active.size,
            if (total == 0) null else days.values.sumOf { it.passed }.toDouble() / total,
            work, learned, learnedWeekAgo, scope.sumOf { it.allTopics.size },
            forecast(progress, today),
        )
    }

    /** Days in a row with answers up to today, for the chip in the header: only the journal is read, nothing is replayed. */
    fun streak(): Int {
        val ids = courses.all().mapTo(mutableSetOf()) { it.id }
        val active = journal.all().mapNotNullTo(mutableSetOf()) { e -> (e.event as? AnswerSubmitted)?.takeIf { it.courseId in ids }?.let { e.occurredAt.localDate } }
        return streak(active, clock.now().localDate)
    }

    private fun scope(courseId: Uuid?): List<Course> = if (courseId == null) courses.all() else listOfNotNull(courses.get(courseId))

    /** Every answer of the courses in [scope], with its envelope, in journal order. */
    private fun answersOf(scope: List<Course>): List<Pair<EventEnvelope, AnswerSubmitted>> {
        val ids = scope.mapTo(mutableSetOf()) { it.id }
        return journal.all().mapNotNull { envelope -> (envelope.event as? AnswerSubmitted)?.takeIf { it.courseId in ids }?.let { envelope to it } }
    }

    private fun activity(answers: List<AnswerSubmitted>, passed: (AnswerSubmitted) -> Boolean, newTopics: Int) =
        DayActivity(answers.size, answers.count(passed), answers.count { a -> a.results.any { it == ItemResult.TYPO } }, newTopics)

    /** The day of each topic's very first answer: the day it was begun as a new topic. */
    private fun firstAnswerDays(answers: List<Pair<EventEnvelope, AnswerSubmitted>>): Map<Uuid, LocalDate> =
        answers.groupBy { (_, a) -> a.topicId }.mapValues { (_, list) -> list.minBy { it.first.occurredAt.instant }.first.occurredAt.localDate }

    private fun <M> studiedTopics(course: Course, events: List<EventEnvelope>, rules: CourseRules<M, PassFail>): Set<Uuid> =
        replay(course, events, rules).filterValues { it.memory != null }.keys

    private fun <M> learnedCount(course: Course, events: List<EventEnvelope>, rules: CourseRules<M, PassFail>): Int = studiedTopics(course, events, rules).size

    private fun streak(active: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in active) today else today.minus(1, DateTimeUnit.DAY)
        var count = 0
        while (day in active) {
            count++
            day = day.minus(1, DateTimeUnit.DAY)
        }
        return count
    }

    private fun bestStreak(active: Set<LocalDate>): Int {
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in active.sorted()) {
            run = if (previous != null && previous.plus(1, DateTimeUnit.DAY) == day) run + 1 else 1
            best = maxOf(best, run)
            previous = day
        }
        return best
    }

    /** From the scheduler's own dates ([TopicStatus.Passed]): what is waiting today and on each of the next six days. */
    private fun forecast(progress: List<CourseProgress>, today: LocalDate): List<ReviewDay> {
        val topics = progress.flatMap { it.allTopics }
        val dueToday = topics.count { it.group == TopicGroup.TO_REVIEW }
        val later = topics.mapNotNull { (it.status as? TopicStatus.Passed)?.due }.groupingBy { it }.eachCount()
        return (0..6).map { i ->
            val date = today.plus(i, DateTimeUnit.DAY)
            ReviewDay(date, if (i == 0) dueToday else later[date] ?: 0)
        }
    }
}

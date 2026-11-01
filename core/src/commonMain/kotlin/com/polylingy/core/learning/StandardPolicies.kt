package com.polylingy.core.learning

import com.polylingy.content.ItemResult
import com.polylingy.core.journal.AnswerSubmitted
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.uuid.Uuid

// Grading

private fun ItemResult.counts() = this == ItemResult.CORRECT || this == ItemResult.TYPO

/** Passes when every item is right. A typo only appears when the course forgives typos, so it counts as right. */
object AllCorrect : GradingRule<PassFail> {
    override fun grade(answer: AnswerSubmitted) = if (answer.results.all { it.counts() }) PassFail.PASS else PassFail.FAIL
}

/** Passes when at least [min] of the items (0…1) are right: with 0.75, 3 of 4 pass and 1 of 2 doesn't. */
class Threshold(val min: Double) : GradingRule<PassFail> {
    init {
        require(min > 0 && min <= 1) { "min must be in (0, 1], got $min" }
    }

    override fun grade(answer: AnswerSubmitted): PassFail {
        val right = answer.results.count { it.counts() }
        return if (right >= min * answer.results.size - 1e-9) PassFail.PASS else PassFail.FAIL
    }
}

// Mastery

/** Mastered after [learning] passes in a row while learning, [review] in a row while reviewing; a fail starts the run again. */
class Consecutive(val learning: Int, val review: Int) : MasteryPolicy<PassFail> {
    init {
        require(learning >= 1 && review >= 1) { "need at least 1 in a row" }
    }

    override fun isMastered(round: Round<PassFail>, phase: Phase): Boolean {
        val need = if (phase == Phase.LEARNING) learning else review
        return round.takeLastWhile { it.grade == PassFail.PASS }.size >= need
    }
}

// Scheduling

/** Learned for good after the first round. */
object Once : Scheduler<Once.Learned, PassFail> {
    data object Learned

    override fun schedule(memory: Learned?, round: Round<PassFail>, today: LocalDate) = Learned
    override fun dueDate(memory: Learned): LocalDate? = null
}

/**
 * A ladder of intervals in days. A round without a fail climbs one step, a round with one goes back to the first step.
 * After a clean round on the last step the topic is learned for good when [retire] is on, otherwise it stays there.
 */
class Ladder(val days: List<Int> = DEFAULT_DAYS, val retire: Boolean = true) : Scheduler<Ladder.Memory, PassFail> {
    /** [step] indexes [days]; [due] is null once the topic is learned. */
    data class Memory(val step: Int, val due: LocalDate?)

    init {
        require(days.isNotEmpty() && days.all { it >= 1 }) { "days must be at least 1" }
    }

    override fun schedule(memory: Memory?, round: Round<PassFail>, today: LocalDate): Memory {
        val clean = round.none { it.grade == PassFail.FAIL }
        val step = when {
            memory == null || !clean -> 0
            memory.due == null -> return memory
            memory.step + 1 < days.size -> memory.step + 1
            retire -> return Memory(days.lastIndex, null)
            else -> days.lastIndex
        }
        return Memory(step, today.plus(days[step], DateTimeUnit.DAY))
    }

    override fun dueDate(memory: Memory) = memory.due

    companion object {
        val DEFAULT_DAYS = listOf(1, 3, 7, 14, 30)
    }
}

/**
 * SM-2 as in Polylingy 0.1: the quality of a round comes from its number of fails (0 → 5, 1 → 4, 2 → 3, more → 2),
 * the ease factor moves by quality and never drops below 1.3, the interval grows by it — or goes back to 1 day.
 */
object Sm2Scheduler : Scheduler<Sm2Scheduler.Memory, PassFail> {
    data class Memory(val easeFactor: Double, val intervalDays: Int, val due: LocalDate)

    private const val MIN_EASE_FACTOR = 1.3

    override fun schedule(memory: Memory?, round: Round<PassFail>, today: LocalDate): Memory {
        val easeFactor = memory?.easeFactor ?: 2.5
        val interval = memory?.intervalDays ?: 1
        val q = when (round.count { it.grade == PassFail.FAIL }) { 0 -> 5; 1 -> 4; 2 -> 3; else -> 2 }
        val delta = when (q) { 5 -> 0.1; 4 -> 0.0; 3 -> -0.15; else -> -0.3 }
        val newEase = (easeFactor + delta).coerceAtLeast(MIN_EASE_FACTOR)
        val newInterval = if (q > 2) (interval * newEase).roundToInt() else 1
        return Memory(newEase, newInterval, today.plus(newInterval, DateTimeUnit.DAY))
    }

    override fun dueDate(memory: Memory) = memory.due
}

// Order

/** A round under way is finished first (the current topic, else the first started one); then reviews, then new topics. */
private fun startedFirst(candidates: List<Candidate>, current: Uuid?): Uuid? =
    candidates.firstOrNull { it.topicId == current && it.started }?.topicId ?: candidates.firstOrNull { it.started }?.topicId

/** Reviews first, then new topics, both in course order: a conveyor through the units. */
object ReviewsFirst : TopicPicker {
    override fun pick(candidates: List<Candidate>, current: Uuid?): Uuid? =
        startedFirst(candidates, current)
            ?: candidates.firstOrNull { it.phase == Phase.REVIEW }?.topicId
            ?: candidates.firstOrNull()?.topicId
}

/** Reviews first, then new topics, each in random order — for flat courses where order doesn't matter. */
class RandomOrder(private val random: Random) : TopicPicker {
    override fun pick(candidates: List<Candidate>, current: Uuid?): Uuid? =
        startedFirst(candidates, current)
            ?: candidates.filter { it.phase == Phase.REVIEW }.randomOrNull(random)?.topicId
            ?: candidates.randomOrNull(random)?.topicId
}

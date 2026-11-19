package com.polylingy.core.learning

import com.polylingy.content.Exercise
import com.polylingy.content.ItemResult
import com.polylingy.core.journal.AnswerSubmitted
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.uuid.Uuid

// Grading

/** Whether an item counts as right: a typo counts only when [typos] are forgiven. */
private fun ItemResult.counts(typos: Boolean) = this == ItemResult.CORRECT || (typos && this == ItemResult.TYPO)

/** Passes when every item is right; [typos] — an item with a typo counts as right. */
class AllCorrect(val typos: Boolean = true) : GradingRule<PassFail> {
    override fun grade(answer: AnswerSubmitted) = if (answer.results.all { it.counts(typos) }) PassFail.PASS else PassFail.FAIL
}

/** Passes when at least [min] of the items (0…1) are right: with 0.75, 3 of 4 pass and 1 of 2 doesn't. */
class Threshold(val min: Double, val typos: Boolean = true) : GradingRule<PassFail> {
    init {
        require(min > 0 && min <= 1) { "min must be in (0, 1], got $min" }
    }

    override fun grade(answer: AnswerSubmitted): PassFail {
        val right = answer.results.count { it.counts(typos) }
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
 * After a clean round on the last step the topic is learned for good when [retire] is on; otherwise every next gap
 * is twice the one before (30, 60, 120… days).
 */
class Ladder(val days: List<Int> = DEFAULT_DAYS, val retire: Boolean = true) : Scheduler<Ladder.Memory, PassFail> {
    /** [step] indexes [days]; [gap] — the days until [due]; [due] is null once the topic is learned. */
    data class Memory(val step: Int, val due: LocalDate?, val gap: Int)

    init {
        require(days.isNotEmpty() && days.all { it >= 1 }) { "days must be at least 1" }
    }

    override fun schedule(memory: Memory?, round: Round<PassFail>, today: LocalDate): Memory {
        val clean = round.none { it.grade == PassFail.FAIL }
        val (step, gap) = when {
            memory == null || !clean -> 0 to days[0]
            memory.due == null -> return memory
            memory.step + 1 < days.size -> (memory.step + 1).let { it to days[it] }
            retire -> return Memory(days.lastIndex, null, memory.gap)
            else -> days.lastIndex to memory.gap * 2
        }
        return Memory(step, today.plus(gap, DateTimeUnit.DAY), gap)
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

// Picker: queues

/** How many new topics may still be begun today; without a limit, any number. */
private fun newAllowed(newPerDay: Int?, begunToday: Int) =
    if (newPerDay == null) Int.MAX_VALUE else (newPerDay - begunToday).coerceAtLeast(0)

/** Started → due → new; within each group, course order. [newPerDay] limits the new ones; null — no limit. */
class StartedFirst(val newPerDay: Int?) : Queue {
    override fun build(candidates: List<Candidate>, newBegunToday: Int): List<Candidate> =
        candidates.filter { it.group == Group.STARTED } +
            candidates.filter { it.group == Group.DUE } +
            candidates.filter { it.group == Group.NEW }.take(newAllowed(newPerDay, newBegunToday))
}

/** Started → due → new; within each group, random order. [newPerDay] limits the new ones; null — no limit. */
class Shuffled(val newPerDay: Int?, private val random: Random) : Queue {
    override fun build(candidates: List<Candidate>, newBegunToday: Int): List<Candidate> =
        candidates.filter { it.group == Group.STARTED }.shuffled(random) +
            candidates.filter { it.group == Group.DUE }.shuffled(random) +
            candidates.filter { it.group == Group.NEW }.shuffled(random).take(newAllowed(newPerDay, newBegunToday))
}

// Picker: focus

/** Stay: the current topic while it is in the queue (its round isn't over); otherwise the first in the queue. */
object Stay : Focus {
    override fun pick(queue: List<Candidate>, current: Uuid?): Uuid? =
        queue.firstOrNull { it.topicId == current }?.topicId ?: queue.firstOrNull()?.topicId
}

/** Move on: the first in the queue other than the current topic; the current one only when nothing else is left. */
object MoveOn : Focus {
    override fun pick(queue: List<Candidate>, current: Uuid?): Uuid? =
        (queue.firstOrNull { it.topicId != current } ?: queue.firstOrNull())?.topicId
}

// Picker: exercise

/** A random exercise other than the last one answered, unless it is the only one. */
class RandomNotLast(private val random: Random) : ExerciseChoice {
    override fun choose(exercises: List<Exercise>, history: List<TopicAnswer>): Exercise {
        require(exercises.isNotEmpty()) { "a topic needs an exercise to show" }
        val last = history.lastOrNull()?.exerciseId
        return exercises.filter { it.id != last }.ifEmpty { exercises }.random(random)
    }
}

/** The exercise after the one answered last, in the order of the course, and the first again after the last one: round robin. */
object InCourseOrder : ExerciseChoice {
    override fun choose(exercises: List<Exercise>, history: List<TopicAnswer>): Exercise {
        require(exercises.isNotEmpty()) { "a topic needs an exercise to show" }
        return nextAfterLast(exercises, exercises, history.lastOrNull()?.exerciseId)
    }
}

/**
 * Brings back what was missed, but never in the round it was missed in:
 *  1. an exercise whose last answer was wrong, given in an earlier round, with no right answer since: the one missed longest ago;
 *  2. otherwise the next in the order of the course after the last answered, skipping what was missed in this round and the one
 *     just shown, while any other is left;
 *  3. if every exercise was missed in this round, the next in the order of the course after the last answered, all of them
 *     being left (the one just shown still never twice in a row unless it is the only one).
 */
object MissedFirst : ExerciseChoice {
    override fun choose(exercises: List<Exercise>, history: List<TopicAnswer>): Exercise {
        require(exercises.isNotEmpty()) { "a topic needs an exercise to show" }
        val offered = exercises.mapTo(mutableSetOf()) { it.id }
        val answers = history.filter { it.exerciseId in offered }
        // for each exercise: where its run of wrong answers began (null when the last answer was right) and whether its last answer is of this round
        val missedSince = HashMap<Uuid, Int>()
        val lastInRound = HashMap<Uuid, Boolean>()
        answers.forEachIndexed { i, a ->
            if (a.passed) missedSince.remove(a.exerciseId) else missedSince.putIfAbsent(a.exerciseId, i)
            lastInRound[a.exerciseId] = a.inCurrentRound
        }
        val earlier = missedSince.filterKeys { lastInRound[it] == false }
        if (earlier.isNotEmpty()) {
            val oldest = earlier.minBy { it.value }.key
            return exercises.first { it.id == oldest }
        }
        val last = answers.lastOrNull()?.exerciseId
        val missedNow = missedSince.keys
        val others = exercises.filter { it.id != last && it.id !in missedNow }
        val pool = others.ifEmpty { exercises.filter { it.id != last } }.ifEmpty { exercises }
        return nextAfterLast(exercises, pool, last)
    }
}

/** From [pool] (a part of [exercises]), the first that comes after [last] in the order of [exercises], wrapping round. */
private fun nextAfterLast(exercises: List<Exercise>, pool: List<Exercise>, last: Uuid?): Exercise {
    val at = exercises.indexOfFirst { it.id == last }
    val allowed = pool.mapTo(mutableSetOf()) { it.id }
    for (step in 1..exercises.size) {
        val candidate = exercises[(at + step).mod(exercises.size)]
        if (candidate.id in allowed) return candidate
    }
    return pool.first()
}

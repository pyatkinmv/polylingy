package com.polylingy.core.learning

import com.polylingy.content.ItemResult
import com.polylingy.content.ItemResult.CORRECT
import com.polylingy.content.ItemResult.TYPO
import com.polylingy.content.ItemResult.WRONG
import com.polylingy.content.Response
import com.polylingy.core.at
import com.polylingy.core.content.GradingSpec
import com.polylingy.core.content.MasterySpec
import com.polylingy.core.content.PickerSpec
import com.polylingy.core.content.Preset
import com.polylingy.core.content.RulesSpec
import com.polylingy.core.content.SchedulerSpec
import com.polylingy.core.journal.AnswerSubmitted
import com.polylingy.core.learning.PassFail.FAIL
import com.polylingy.core.learning.PassFail.PASS
import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.uuid.Uuid

class PoliciesTest {
    private val today = LocalDate(2026, 11, 2)
    private fun answer(vararg results: ItemResult) =
        AnswerSubmitted(Uuid.NIL, Uuid.NIL, Uuid.NIL, 1, Response.Blanks(results.map { "" }), results.toList())
    private fun round(vararg grades: PassFail): Round<PassFail> = grades.map { Attempt(it, emptyList(), at(today)) }

    // grading

    @Test fun allCorrectCountsTyposAsRight() {
        assertEquals(PASS, AllCorrect.grade(answer(CORRECT, CORRECT)))
        assertEquals(PASS, AllCorrect.grade(answer(CORRECT, TYPO)))
        assertEquals(FAIL, AllCorrect.grade(answer(CORRECT, WRONG)))
    }

    @Test fun thresholdComparesTheShareOfRightItems() {
        val threshold = Threshold(0.75)
        assertEquals(PASS, threshold.grade(answer(CORRECT, CORRECT, TYPO, WRONG)))
        assertEquals(FAIL, threshold.grade(answer(CORRECT, WRONG)))
        assertEquals(PASS, threshold.grade(answer(CORRECT)))
        assertEquals(FAIL, threshold.grade(answer(WRONG)))
        assertEquals(PASS, Threshold(2.0 / 3).grade(answer(CORRECT, CORRECT, WRONG)))
        assertFailsWith<IllegalArgumentException> { Threshold(0.0) }
        assertFailsWith<IllegalArgumentException> { Threshold(1.5) }
    }

    // mastery

    @Test fun consecutiveCountsTheRunAtTheEnd() {
        val policy = Consecutive(learning = 3, review = 1)
        assertEquals(false, policy.isMastered(round(PASS, PASS), Phase.LEARNING))
        assertEquals(true, policy.isMastered(round(PASS, PASS, PASS), Phase.LEARNING))
        assertEquals(false, policy.isMastered(round(PASS, PASS, FAIL), Phase.LEARNING))
        assertEquals(true, policy.isMastered(round(FAIL, PASS, PASS, PASS), Phase.LEARNING))
        assertEquals(true, policy.isMastered(round(FAIL, PASS), Phase.REVIEW))
        assertEquals(false, policy.isMastered(round(FAIL), Phase.REVIEW))
        assertFailsWith<IllegalArgumentException> { Consecutive(0, 1) }
        assertFailsWith<IllegalArgumentException> { Consecutive(1, 0) }
    }

    // scheduling

    @Test fun onceLearnsForGood() {
        val memory = Once.schedule(null, round(PASS), today)
        assertNull(Once.dueDate(memory))
        assertSame(memory, Once.schedule(memory, round(FAIL, PASS), today))
    }

    @Test fun ladderClimbsOnCleanRoundsAndFallsBackOnFails() {
        val ladder = Ladder()
        var memory = ladder.schedule(null, round(FAIL, PASS, PASS, PASS), today)
        assertEquals(Ladder.Memory(0, LocalDate(2026, 11, 3)), memory)
        memory = ladder.schedule(memory, round(PASS), today)
        assertEquals(Ladder.Memory(1, LocalDate(2026, 11, 5)), memory)
        memory = ladder.schedule(memory, round(FAIL, PASS), today)
        assertEquals(Ladder.Memory(0, LocalDate(2026, 11, 3)), memory)
        repeat(4) { memory = ladder.schedule(memory, round(PASS), today) }
        assertEquals(Ladder.Memory(4, LocalDate(2026, 12, 2)), memory)
        memory = ladder.schedule(memory, round(PASS), today)
        assertEquals(Ladder.Memory(4, null), memory)
        assertNull(ladder.dueDate(memory))
        assertSame(memory, ladder.schedule(memory, round(PASS), today), "a learned topic practised cleanly stays learned")
        assertEquals(Ladder.Memory(0, LocalDate(2026, 11, 3)), ladder.schedule(memory, round(FAIL, PASS), today), "a mistake brings it back")
    }

    @Test fun ladderWithoutRetiringStaysOnTheLastStep() {
        val ladder = Ladder(listOf(2, 5), retire = false)
        val top = ladder.schedule(ladder.schedule(null, round(PASS), today), round(PASS), today)
        assertEquals(Ladder.Memory(1, LocalDate(2026, 11, 7)), ladder.schedule(top, round(PASS), today))
        assertFailsWith<IllegalArgumentException> { Ladder(emptyList()) }
        assertFailsWith<IllegalArgumentException> { Ladder(listOf(0)) }
    }

    @Test fun sm2MovesTheEaseFactorByTheFailsOfTheRound() {
        val first = Sm2Scheduler.schedule(null, round(PASS, PASS, PASS), today)
        assertEquals(Sm2Scheduler.Memory(2.6, 3, LocalDate(2026, 11, 5)), first)
        assertEquals(2.5, Sm2Scheduler.schedule(null, round(FAIL, PASS), today).easeFactor, 1e-9)
        assertEquals(2.35, Sm2Scheduler.schedule(null, round(FAIL, FAIL, PASS), today).easeFactor, 1e-9)
        val bad = Sm2Scheduler.schedule(Sm2Scheduler.Memory(1.4, 10, today), round(FAIL, FAIL, FAIL, PASS), today)
        assertEquals(Sm2Scheduler.Memory(1.3, 1, LocalDate(2026, 11, 3)), bad)
        assertEquals(bad.due, Sm2Scheduler.dueDate(bad))
    }

    // order

    private val a = Uuid.random()
    private val b = Uuid.random()
    private val c = Uuid.random()
    private fun new(id: Uuid) = Candidate(id, Phase.LEARNING, started = false)
    private fun due(id: Uuid) = Candidate(id, Phase.REVIEW, started = false)
    private fun started(id: Uuid, phase: Phase = Phase.LEARNING) = Candidate(id, phase, started = true)

    @Test fun reviewsFirstFinishesRoundsThenReviewsThenNewInCourseOrder() {
        assertEquals(a, ReviewsFirst.pick(listOf(new(a), new(c)), null))
        assertEquals(b, ReviewsFirst.pick(listOf(new(a), due(b), due(c)), null))
        assertEquals(c, ReviewsFirst.pick(listOf(new(a), due(b), started(c)), null))
        assertEquals(c, ReviewsFirst.pick(listOf(started(a), started(c)), current = c))
        assertEquals(a, ReviewsFirst.pick(listOf(started(a), started(c)), current = b))
        assertNull(ReviewsFirst.pick(emptyList(), null))
    }

    @Test fun randomOrderStillPutsRoundsAndReviewsFirst() {
        val random = RandomOrder(Random(1))
        assertEquals(c, random.pick(listOf(new(a), due(b), started(c)), null))
        assertEquals(b, random.pick(listOf(new(a), due(b), new(c)), null))
        val picks = (1..40).mapNotNull { RandomOrder(Random(it)).pick(listOf(new(a), new(b), new(c)), null) }.toSet()
        assertEquals(setOf(a, b, c), picks)
        assertNull(random.pick(emptyList(), null))
    }

    // presets

    @Test fun noRulesMeansTheLadder() {
        val rules = null.toRules(Random(0))
        assertSame(AllCorrect, rules.grading)
        val mastery = assertIs<Consecutive>(rules.mastery)
        assertEquals(3 to 1, mastery.learning to mastery.review)
        assertEquals(Ladder.DEFAULT_DAYS, assertIs<Ladder>(rules.scheduler).days)
        assertSame(ReviewsFirst, rules.picker)
    }

    @Test fun presets() {
        val quick = RulesSpec(Preset.QUICK).toRules(Random(0))
        assertSame(Once, quick.scheduler)
        assertEquals(1, (quick.mastery as Consecutive).learning)
        assertSame(Sm2Scheduler, RulesSpec(Preset.SM2).toRules(Random(0)).scheduler)
    }

    @Test fun overridesReplaceTheirPartOnly() {
        val rules = RulesSpec(
            Preset.QUICK,
            grading = GradingSpec.Threshold(0.8),
            mastery = MasterySpec.Consecutive(2, 2),
            scheduler = SchedulerSpec.Ladder(listOf(2, 4), retire = false),
            picker = PickerSpec.Random,
        ).toRules(Random(0))
        assertEquals(0.8, assertIs<Threshold>(rules.grading).min)
        assertEquals(2, (rules.mastery as Consecutive).review)
        assertEquals(listOf(2, 4), (rules.scheduler as Ladder).days)
        assertIs<RandomOrder>(rules.picker)
        assertSame(Once, RulesSpec(Preset.LADDER, scheduler = SchedulerSpec.Once).toRules(Random(0)).scheduler)
        assertSame(Sm2Scheduler, RulesSpec(scheduler = SchedulerSpec.Sm2, picker = PickerSpec.ReviewsFirst).toRules(Random(0)).scheduler)
        assertSame(AllCorrect, RulesSpec(grading = GradingSpec.AllCorrect).toRules(Random(0)).grading)
    }
}

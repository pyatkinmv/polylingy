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

    @Test fun allCorrectCountsTyposAsRightWhenForgiven() {
        assertEquals(PASS, AllCorrect().grade(answer(CORRECT, CORRECT)))
        assertEquals(PASS, AllCorrect().grade(answer(CORRECT, TYPO)))
        assertEquals(FAIL, AllCorrect().grade(answer(CORRECT, WRONG)))
        assertEquals(FAIL, AllCorrect(typos = false).grade(answer(CORRECT, TYPO)), "a typo not forgiven is wrong")
        assertEquals(PASS, AllCorrect(typos = false).grade(answer(CORRECT, CORRECT)))
    }

    @Test fun thresholdComparesTheShareOfRightItems() {
        val threshold = Threshold(0.75)
        assertEquals(PASS, threshold.grade(answer(CORRECT, CORRECT, TYPO, WRONG)))
        assertEquals(FAIL, threshold.grade(answer(CORRECT, WRONG)))
        assertEquals(PASS, threshold.grade(answer(CORRECT)))
        assertEquals(FAIL, threshold.grade(answer(WRONG)))
        assertEquals(PASS, Threshold(2.0 / 3).grade(answer(CORRECT, CORRECT, WRONG)))
        assertEquals(FAIL, Threshold(0.75, typos = false).grade(answer(CORRECT, CORRECT, TYPO, WRONG)))
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
        assertEquals(Ladder.Memory(0, LocalDate(2026, 11, 3), 1), memory)
        memory = ladder.schedule(memory, round(PASS), today)
        assertEquals(Ladder.Memory(1, LocalDate(2026, 11, 5), 3), memory)
        memory = ladder.schedule(memory, round(FAIL, PASS), today)
        assertEquals(Ladder.Memory(0, LocalDate(2026, 11, 3), 1), memory)
        repeat(4) { memory = ladder.schedule(memory, round(PASS), today) }
        assertEquals(Ladder.Memory(4, LocalDate(2026, 12, 2), 30), memory)
        memory = ladder.schedule(memory, round(PASS), today)
        assertEquals(Ladder.Memory(4, null, 30), memory)
        assertNull(ladder.dueDate(memory))
        assertSame(memory, ladder.schedule(memory, round(PASS), today), "a learned topic practised cleanly stays learned")
        assertEquals(Ladder.Memory(0, LocalDate(2026, 11, 3), 1), ladder.schedule(memory, round(FAIL, PASS), today), "a mistake brings it back")
    }

    @Test fun ladderWithoutRetiringDoublesTheGapAfterTheLastStep() {
        val ladder = Ladder(listOf(2, 5), retire = false)
        val top = ladder.schedule(ladder.schedule(null, round(PASS), today), round(PASS), today)
        assertEquals(Ladder.Memory(1, LocalDate(2026, 11, 7), 5), top)
        val doubled = ladder.schedule(top, round(PASS), today)
        assertEquals(Ladder.Memory(1, LocalDate(2026, 11, 12), 10), doubled)
        assertEquals(20, ladder.schedule(doubled, round(PASS), today).gap)
        assertEquals(Ladder.Memory(0, LocalDate(2026, 11, 4), 2), ladder.schedule(doubled, round(FAIL, PASS), today), "a mistake goes back to the first step")
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

    // picker: queue, focus, exercise

    private val a = Uuid.random()
    private val b = Uuid.random()
    private val c = Uuid.random()
    private val d = Uuid.random()
    private fun new(id: Uuid) = Candidate(id, Group.NEW)
    private fun due(id: Uuid, on: LocalDate = today) = Candidate(id, Group.DUE, on)
    private fun started(id: Uuid) = Candidate(id, Group.STARTED)
    private fun ids(queue: List<Candidate>) = queue.map { it.topicId }

    @Test fun startedFirstPutsRoundsThenReviewsThenNewInCourseOrder() {
        val queue = StartedFirst(newPerDay = null).build(listOf(new(a), due(b), started(c), new(d)), newBegunToday = 0)
        assertEquals(listOf(c, b, a, d), ids(queue))
    }

    @Test fun theDailyLimitCutsOnlyNewTopics() {
        val candidates = listOf(new(a), due(b), started(c), new(d))
        assertEquals(listOf(c, b, a), ids(StartedFirst(newPerDay = 2).build(candidates, newBegunToday = 1)))
        assertEquals(listOf(c, b), ids(StartedFirst(newPerDay = 2).build(candidates, newBegunToday = 2)), "the round begun today goes on")
        assertEquals(listOf(c, b), ids(StartedFirst(newPerDay = 2).build(candidates, newBegunToday = 5)))
        assertEquals(setOf(c, b), ids(Shuffled(newPerDay = 1, Random(3)).build(candidates, newBegunToday = 1)).toSet())
    }

    @Test fun shuffledKeepsTheGroupsButMixesEach() {
        val candidates = listOf(new(a), new(b), new(c), started(d))
        val firsts = (1..40).map { ids(Shuffled(null, Random(it)).build(candidates, 0)) }
        firsts.forEach { assertEquals(d, it.first(), "started first") }
        assertEquals(setOf(a, b, c), firsts.map { it[1] }.toSet())
    }

    @Test fun stayKeepsTheCurrentTopicWhileItIsQueued() {
        val queue = listOf(started(a), started(b), new(c))
        assertEquals(b, Stay.pick(queue, current = b))
        assertEquals(a, Stay.pick(queue, current = c.takeIf { false }))
        assertEquals(a, Stay.pick(queue, current = d), "a topic whose round is over isn't queued")
        assertNull(Stay.pick(emptyList(), current = a))
    }

    @Test fun moveOnTakesAnotherTopicUnlessNoneIsLeft() {
        val queue = listOf(started(a), started(b), new(c))
        assertEquals(b, MoveOn.pick(queue, current = a))
        assertEquals(a, MoveOn.pick(queue, current = b))
        assertEquals(a, MoveOn.pick(queue, current = null))
        assertEquals(a, MoveOn.pick(listOf(started(a)), current = a), "the only one left")
        assertNull(MoveOn.pick(emptyList(), current = a))
    }

    @Test fun randomNotLastNeverRepeatsAnExerciseUnlessItIsTheOnlyOne() {
        val one = com.polylingy.core.exercise("one {{a}}")
        val two = com.polylingy.core.exercise("two {{b}}")
        repeat(20) { assertSame(two, RandomNotLast(Random(it)).choose(listOf(one, two), last = one.id)) }
        assertSame(one, RandomNotLast(Random(0)).choose(listOf(one), last = one.id))
        assertEquals(setOf(one, two), (1..30).map { RandomNotLast(Random(it)).choose(listOf(one, two), null) }.toSet())
    }

    // presets

    @Test fun noRulesMeansTheLadder() {
        val rules = null.toRules(Random(0))
        assertEquals(true, assertIs<AllCorrect>(rules.grading).typos)
        val mastery = assertIs<Consecutive>(rules.mastery)
        assertEquals(3 to 1, mastery.learning to mastery.review)
        assertEquals(Ladder.DEFAULT_DAYS, assertIs<Ladder>(rules.scheduler).days)
        assertIs<StartedFirst>(rules.picker.queue)
        assertSame(Stay, rules.picker.focus)
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
            picker = PickerSpec.Shuffle(newPerDay = 5),
        ).toRules(Random(0))
        assertEquals(0.8, assertIs<Threshold>(rules.grading).min)
        assertEquals(2, (rules.mastery as Consecutive).review)
        assertEquals(listOf(2, 4), (rules.scheduler as Ladder).days)
        assertEquals(5, assertIs<Shuffled>(rules.picker.queue).newPerDay)
        assertSame(Once, RulesSpec(Preset.LADDER, scheduler = SchedulerSpec.Once).toRules(Random(0)).scheduler)
        assertSame(Sm2Scheduler, RulesSpec(scheduler = SchedulerSpec.Sm2, picker = PickerSpec.Conveyor()).toRules(Random(0)).scheduler)
        assertEquals(false, assertIs<AllCorrect>(RulesSpec(grading = GradingSpec.AllCorrect(typos = false)).toRules(Random(0)).grading).typos)
    }

    @Test fun theThreePickers() {
        val alternate = PickerSpec.Alternate(newPerDay = 3).toPicker(Random(0))
        assertEquals(3, assertIs<StartedFirst>(alternate.queue).newPerDay)
        assertSame(MoveOn, alternate.focus)
        assertSame(Stay, PickerSpec.Conveyor().toPicker(Random(0)).focus)
        assertSame(Stay, PickerSpec.Shuffle().toPicker(Random(0)).focus)
        assertIs<RandomNotLast>(PickerSpec.Conveyor().toPicker(Random(0)).exercise)
    }

    @Test fun resolvedWritesEveryPartOutAndMatchesItsPreset() {
        val resolved = RulesSpec(Preset.QUICK, mastery = MasterySpec.Consecutive(2, 1)).resolved()
        assertEquals(GradingSpec.AllCorrect(), resolved.grading)
        assertEquals(MasterySpec.Consecutive(2, 1), resolved.mastery)
        assertEquals(SchedulerSpec.Once, resolved.scheduler)
        assertEquals(PickerSpec.Conveyor(), resolved.picker)
        assertNull(resolved.matchingPreset(), "two in a row is no preset")
        assertEquals(Preset.SM2, RulesSpec(Preset.SM2).resolved().matchingPreset())
        assertEquals(Preset.LADDER, RulesSpec(Preset.QUICK, mastery = MasterySpec.Consecutive(3, 1), scheduler = SchedulerSpec.Ladder()).matchingPreset())
        assertEquals(Preset.LADDER, RulesSpec(picker = PickerSpec.Alternate(10)).matchingPreset(), "the picker isn't part of a preset")
    }
}

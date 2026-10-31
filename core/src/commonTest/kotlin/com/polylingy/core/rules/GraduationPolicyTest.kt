package com.polylingy.core.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Multi-topic independence is tested in ReplayTest, since streaks are kept per topic by replay().

/** Feeds answers one by one, starting a fresh streak after each graduation, and returns every outcome. */
private fun GraduationPolicy.run(answers: List<Boolean>): List<StreakOutcome> {
    var streak = Streak()
    return answers.map { correct ->
        record(streak, correct).also { outcome ->
            streak = (outcome as? StreakOutcome.InProgress)?.streak ?: Streak()
        }
    }
}

private fun GraduationPolicy.last(vararg answers: Boolean) = run(answers.toList()).last()

private val StreakOutcome.completed get() = this is StreakOutcome.Graduated

class GraduationPolicyTest {
    private val policy = ConsecutiveCorrectPolicy()

    // ── completion rule (3 consecutive correct) ─────────────────────────────

    @Test fun threeCorrectInARowCompletesImmediately() = assertTrue(policy.last(true, true, true).completed)

    @Test fun twoCorrectIsNotEnough() = assertEquals(false, policy.last(true, true).completed)

    @Test fun oneCorrectIsNotEnough() = assertEquals(false, policy.last(true).completed)

    @Test fun oneIncorrectThenThreeCorrectCompletesOnFourthAnswer() {
        assertEquals(listOf(false, false, false, true), policy.run(listOf(false, true, true, true)).map { it.completed })
    }

    @Test fun correctIncorrectThenThreeCorrectCompletesOnFifth() {
        assertTrue(policy.run(listOf(true, false, true, true, true))[4].completed)
    }

    @Test fun incorrectResetsTheStreakToZero() {
        assertTrue(policy.last(true, true, false, true, true, true).completed)
        assertEquals(false, policy.last(true, true, false, true, true).completed)
    }

    @Test fun manyWrongAnswersFollowedByThreeCorrectStillCompletes() {
        assertTrue(policy.run(List(10) { false } + listOf(true, true, true)).last().completed)
    }

    // ── mistake count at completion ─────────────────────────────────────────

    private fun mistakes(vararg answers: Boolean) = assertIs<StreakOutcome.Graduated>(policy.last(*answers)).mistakes

    @Test fun zeroMistakesOnAPerfectRun() = assertEquals(0, mistakes(true, true, true))

    @Test fun oneMistakeBeforeEventualThreeInARow() = assertEquals(1, mistakes(false, true, true, true))

    @Test fun twoMistakes() = assertEquals(2, mistakes(false, false, true, true, true))

    @Test fun threeMistakes() = assertEquals(3, mistakes(false, false, false, true, true, true))

    @Test fun mistakesCountedAcrossStreakResets() = assertEquals(2, mistakes(false, true, true, false, true, true, true))

    // ── state is cleared after completion ───────────────────────────────────

    @Test fun mistakesStartFreshAfterCompletion() {
        val outcomes = policy.run(listOf(false, true, true, true) + listOf(true, true, true))
        assertEquals(0, assertIs<StreakOutcome.Graduated>(outcomes.last()).mistakes)
    }

    @Test fun streakCounterStartsFromZeroAfterCompletion() {
        assertEquals(false, policy.run(listOf(true, true, true) + listOf(true, true)).last().completed)
    }
}

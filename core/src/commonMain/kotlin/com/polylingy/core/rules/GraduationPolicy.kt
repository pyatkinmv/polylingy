package com.polylingy.core.rules

/** Running state of a topic between graduations. It survives leaving the screen and day changes. */
data class Streak(val consecutiveCorrect: Int = 0, val mistakes: Int = 0)

sealed interface StreakOutcome {
    data class InProgress(val streak: Streak) : StreakOutcome

    /** The topic graduated; the next answer starts a fresh [Streak]. */
    data class Graduated(val mistakes: Int) : StreakOutcome
}

/** Decides when a topic is learned well enough to be scheduled by a [RepetitionPolicy]. */
interface GraduationPolicy {
    fun record(streak: Streak, correct: Boolean): StreakOutcome
}

/** Graduates a topic after [goal] correct answers in a row; a wrong answer resets the run. */
class ConsecutiveCorrectPolicy(private val goal: Int = 3) : GraduationPolicy {
    override fun record(streak: Streak, correct: Boolean): StreakOutcome {
        val next = if (correct) {
            streak.copy(consecutiveCorrect = streak.consecutiveCorrect + 1)
        } else {
            Streak(consecutiveCorrect = 0, mistakes = streak.mistakes + 1)
        }
        return if (next.consecutiveCorrect >= goal) StreakOutcome.Graduated(next.mistakes) else StreakOutcome.InProgress(next)
    }
}

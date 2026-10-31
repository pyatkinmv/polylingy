package com.polylingy.core.rules

import com.polylingy.core.content.Exercise
import com.polylingy.core.journal.AnswerSubmitted
import com.polylingy.core.journal.AutoCheck
import com.polylingy.core.journal.Rating

data class BlankCheck(val key: String, val expected: String, val given: String) {
    val correct: Boolean get() = given.trim().equals(expected.trim(), ignoreCase = true)
}

data class AnswerCheck(val result: AutoCheck, val blanks: List<BlankCheck>)

/**
 * Checks typed input against an exercise's blanks. Answers for several blanks are
 * comma-separated in blank order; comparison ignores case and surrounding spaces.
 */
object AnswerChecker {
    fun check(exercise: Exercise, input: String): AnswerCheck {
        val parts = input.split(',').map { it.trim() }
        val blanks = blankKeys(exercise).mapIndexed { i, key ->
            BlankCheck(key, expected = exercise.answers.getValue(key), given = parts.getOrElse(i) { "" })
        }
        val correctCount = blanks.count { it.correct }
        val result = when (correctCount) {
            blanks.size -> AutoCheck.CORRECT
            0 -> AutoCheck.INCORRECT
            else -> AutoCheck.PARTIAL
        }
        return AnswerCheck(result, blanks)
    }

    /** Blank keys in numeric order: "0", "1", …, "10". */
    fun blankKeys(exercise: Exercise): List<String> =
        exercise.answers.keys.sortedWith(compareBy<String> { it.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it })
}

/** Decides whether an answer counts as correct for graduation. */
fun interface CorrectnessRule {
    fun isCorrect(answer: AnswerSubmitted): Boolean
}

/** The self-rating decides; without one, only a fully correct automatic check counts. */
val RatingThenAutoCheck = CorrectnessRule { answer ->
    answer.rating?.let { it == Rating.CORRECT } ?: (answer.autoCheck == AutoCheck.CORRECT)
}

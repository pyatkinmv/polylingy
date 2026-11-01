package com.polylingy.core.rules

import com.polylingy.core.journal.AnswerSubmitted
import com.polylingy.core.journal.AutoCheck
import com.polylingy.core.journal.Rating

/** Decides whether an answer counts as correct for graduation. Replaced by GradingRule in stage 4. */
fun interface CorrectnessRule {
    fun isCorrect(answer: AnswerSubmitted): Boolean
}

/** The self-rating decides; without one, only a fully correct automatic check counts. */
val RatingThenAutoCheck = CorrectnessRule { answer ->
    answer.rating?.let { it == Rating.CORRECT } ?: (answer.autoCheck == AutoCheck.CORRECT)
}

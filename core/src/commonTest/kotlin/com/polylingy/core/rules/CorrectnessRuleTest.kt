package com.polylingy.core.rules

import com.polylingy.core.journal.AnswerSubmitted
import com.polylingy.core.journal.AutoCheck
import com.polylingy.core.journal.Rating
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class CorrectnessRuleTest {
    private fun answer(autoCheck: AutoCheck?, rating: Rating?) =
        AnswerSubmitted(Uuid.NIL, Uuid.NIL, Uuid.NIL, "", autoCheck, rating)

    @Test fun ratingDecidesOverAutoCheck() {
        assertTrue(RatingThenAutoCheck.isCorrect(answer(AutoCheck.INCORRECT, Rating.CORRECT)))
        assertFalse(RatingThenAutoCheck.isCorrect(answer(AutoCheck.CORRECT, Rating.INCORRECT)))
    }

    @Test fun withoutRatingOnlyFullyCorrectAutoCheckCounts() {
        assertTrue(RatingThenAutoCheck.isCorrect(answer(AutoCheck.CORRECT, null)))
        assertFalse(RatingThenAutoCheck.isCorrect(answer(AutoCheck.PARTIAL, null)))
        assertFalse(RatingThenAutoCheck.isCorrect(answer(null, null)))
    }
}

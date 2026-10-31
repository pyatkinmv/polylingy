package com.polylingy.core.rules

import com.polylingy.core.exercise
import com.polylingy.core.journal.AnswerSubmitted
import com.polylingy.core.journal.AutoCheck
import com.polylingy.core.journal.Rating
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class AnswerCheckerTest {
    private val single = exercise("Translate: hello", "Hola")
    private val colors = exercise("red: {0} / {1}", "rojo", "roja")

    @Test fun exactAnswerIsCorrect() = assertEquals(AutoCheck.CORRECT, AnswerChecker.check(single, "Hola").result)

    @Test fun caseAndSurroundingSpacesAreIgnored() =
        assertEquals(AutoCheck.CORRECT, AnswerChecker.check(single, "  hola ").result)

    @Test fun wrongAnswerIsIncorrect() = assertEquals(AutoCheck.INCORRECT, AnswerChecker.check(single, "adios").result)

    @Test fun emptyInputIsIncorrect() = assertEquals(AutoCheck.INCORRECT, AnswerChecker.check(single, "").result)

    @Test fun multipleBlanksAreCommaSeparated() =
        assertEquals(AutoCheck.CORRECT, AnswerChecker.check(colors, "rojo, roja").result)

    @Test fun someBlanksRightIsPartial() {
        val check = AnswerChecker.check(colors, "Rojo, rosa")
        assertEquals(AutoCheck.PARTIAL, check.result)
        assertEquals(listOf(true, false), check.blanks.map { it.correct })
        assertEquals("rosa", check.blanks[1].given)
        assertEquals("roja", check.blanks[1].expected)
    }

    @Test fun missingPartsCountAsEmpty() {
        val check = AnswerChecker.check(colors, "rojo")
        assertEquals(AutoCheck.PARTIAL, check.result)
        assertEquals("", check.blanks[1].given)
    }

    @Test fun blankKeysAreSortedNumerically() {
        val many = exercise("x", *Array(11) { "a$it" })
        assertEquals((0..10).map { "$it" }, AnswerChecker.blankKeys(many))
    }

    // ── which answers count as correct for graduation ───────────────────────

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

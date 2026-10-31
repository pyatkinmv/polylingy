package com.polylingy.core.rules

import com.polylingy.core.progress.ProgressStatus
import com.polylingy.core.progress.TopicProgress
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

// Ported 1:1 from the Flutter sm2_policy_test.dart "advance —" groups.
// The "learning card" case is gone: that status was never set and no longer exists.

private val today = LocalDate(2026, 1, 15)

private fun newCard() = TopicProgress(Uuid.random())

private fun reviewCard(intervalDays: Int, easeFactor: Double) = TopicProgress(
    topicId = Uuid.random(),
    status = ProgressStatus.TO_REVIEW,
    easeFactor = easeFactor,
    intervalDays = intervalDays,
    nextReviewDate = today,
)

private fun assertClose(expected: Double, actual: Double) = assertEquals(expected, actual, 0.001)

class Sm2PolicyTest {
    private val p = Sm2Policy()

    // ── quality mapped from session mistakes (observed through EF) ──────────

    @Test fun zeroMistakesRaisesEfByPointOne() = assertClose(2.6, p.advance(newCard(), 0, today).easeFactor)

    @Test fun oneMistakeKeepsEf() = assertClose(2.5, p.advance(newCard(), 1, today).easeFactor)

    @Test fun twoMistakesLowerEfByPointFifteen() = assertClose(2.35, p.advance(newCard(), 2, today).easeFactor)

    @Test fun threeMistakesLowerEfByPointThree() = assertClose(2.2, p.advance(newCard(), 3, today).easeFactor)

    @Test fun tenMistakesStillLowerEfByPointThreeOnly() = assertClose(2.2, p.advance(newCard(), 10, today).easeFactor)

    // ── EF floor at 1.3 ─────────────────────────────────────────────────────

    @Test fun efClampedToFloorFromOnePointFour() = assertClose(1.3, p.advance(reviewCard(5, 1.4), 3, today).easeFactor)

    @Test fun efStaysAtFloor() = assertClose(1.3, p.advance(reviewCard(5, 1.3), 3, today).easeFactor)

    @Test fun efClampedToFloorOnQ3() = assertClose(1.3, p.advance(reviewCard(5, 1.35), 2, today).easeFactor)

    @Test fun efAtFloorUnchangedOnQ4() = assertClose(1.3, p.advance(reviewCard(5, 1.3), 1, today).easeFactor)

    @Test fun efAtFloorRisesOnQ5() = assertClose(1.4, p.advance(reviewCard(5, 1.3), 0, today).easeFactor)

    // ── interval for a fresh new card (I=1, EF=2.5) ─────────────────────────

    @Test fun newCardZeroMistakes() = assertEquals(3, p.advance(newCard(), 0, today).intervalDays)

    // Kotlin's roundToInt rounds 2.5 up to 3, same as Dart for positive numbers.
    @Test fun newCardOneMistake() = assertEquals(3, p.advance(newCard(), 1, today).intervalDays)

    @Test fun newCardTwoMistakes() = assertEquals(2, p.advance(newCard(), 2, today).intervalDays)

    @Test fun newCardThreeMistakesResetsToOne() = assertEquals(1, p.advance(newCard(), 3, today).intervalDays)

    @Test fun newCardFourMistakesResetsToOne() = assertEquals(1, p.advance(newCard(), 4, today).intervalDays)

    // ── interval for a review card (I=5, EF=2.5) ────────────────────────────

    @Test fun reviewCardZeroMistakes() = assertEquals(13, p.advance(reviewCard(5, 2.5), 0, today).intervalDays)

    @Test fun reviewCardOneMistake() = assertEquals(13, p.advance(reviewCard(5, 2.5), 1, today).intervalDays)

    @Test fun reviewCardTwoMistakes() = assertEquals(12, p.advance(reviewCard(5, 2.5), 2, today).intervalDays)

    @Test fun reviewCardThreeMistakesResetsToOne() = assertEquals(1, p.advance(reviewCard(5, 2.5), 3, today).intervalDays)

    // ── nextReviewDate ──────────────────────────────────────────────────────

    @Test fun nextReviewInThreeDays() = assertEquals(LocalDate(2026, 1, 18), p.advance(newCard(), 0, today).nextReviewDate)

    @Test fun nextReviewTomorrowAfterReset() = assertEquals(LocalDate(2026, 1, 16), p.advance(newCard(), 3, today).nextReviewDate)

    @Test fun nextReviewInTwentySixDays() =
        assertEquals(LocalDate(2026, 2, 10), p.advance(reviewCard(10, 2.5), 0, today).nextReviewDate)

    // ── status always becomes TO_REVIEW ─────────────────────────────────────

    @Test fun newCardBecomesToReview() = assertEquals(ProgressStatus.TO_REVIEW, p.advance(newCard(), 0, today).status)

    @Test fun reviewCardStaysToReview() = assertEquals(ProgressStatus.TO_REVIEW, p.advance(reviewCard(7, 2.5), 0, today).status)

    // ── interval growth across successive reviews ───────────────────────────

    @Test fun fivePerfectSessionsFromNewCard() {
        var card = newCard()
        val intervals = List(5) { card = p.advance(card, 0, today); card.intervalDays }
        assertEquals(listOf(3, 8, 22, 64, 192), intervals)
    }

    @Test fun fourSessionsWithOneMistakeEach() {
        var card = newCard()
        val intervals = List(4) { card = p.advance(card, 1, today); card.intervalDays }
        assertEquals(listOf(3, 8, 20, 50), intervals)
    }

    // ── EF erosion with repeated hard reviews ───────────────────────────────

    @Test fun efDecreasesEachHardReview() {
        var card = newCard()
        val efs = List(5) { card = p.advance(card, 2, today); card.easeFactor }
        listOf(2.35, 2.20, 2.05, 1.90, 1.75).zip(efs).forEach { (expected, actual) -> assertClose(expected, actual) }
    }

    @Test fun efFloorsAfterManyForgottenReviews() {
        var card = newCard()
        repeat(10) { card = p.advance(card, 3, today) }
        assertClose(1.3, card.easeFactor)
    }

    // ── reset then perfect recovery ─────────────────────────────────────────

    @Test fun resetThenRecoveryMultipliesFromOne() {
        var card = reviewCard(20, 2.5)
        card = p.advance(card, 3, today)
        assertEquals(1, card.intervalDays)
        assertClose(2.2, card.easeFactor)

        card = p.advance(card, 0, today)
        assertEquals(2, card.intervalDays)
        assertClose(2.3, card.easeFactor)
    }
}

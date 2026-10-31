package com.polylingy.core.progress

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

// Ported from the Flutter "eligibleTopicIds" group and widget_test.dart (isEligibleToday).
// The "learning topic" case is gone with that status.

private val today = LocalDate(2026, 1, 15)

private fun reviewDue(date: LocalDate) =
    TopicProgress(Uuid.random(), status = ProgressStatus.TO_REVIEW, intervalDays = 3, nextReviewDate = date)

class EligibilityTest {
    @Test fun newTopicIsEligible() = assertTrue(TopicProgress(Uuid.random()).isEligibleOn(today))

    @Test fun dueTodayIsEligible() = assertTrue(reviewDue(today).isEligibleOn(today))

    @Test fun dueYesterdayIsEligible() = assertTrue(reviewDue(today.minus(1, DateTimeUnit.DAY)).isEligibleOn(today))

    @Test fun dueTomorrowIsNotEligible() = assertFalse(reviewDue(today.plus(1, DateTimeUnit.DAY)).isEligibleOn(today))

    @Test fun dueInAWeekIsNotEligible() = assertFalse(reviewDue(today.plus(7, DateTimeUnit.DAY)).isEligibleOn(today))

    @Test fun toReviewWithoutDateIsNotEligible() =
        assertFalse(TopicProgress(Uuid.random(), status = ProgressStatus.TO_REVIEW).isEligibleOn(today))

    @Test fun mixedPoolKeepsOnlyEligible() {
        val due = reviewDue(today)
        val future = reviewDue(today.plus(3, DateTimeUnit.DAY))
        val fresh = TopicProgress(Uuid.random())
        val eligible = listOf(due, future, fresh).filter { it.isEligibleOn(today) }
        assertTrue(due in eligible && fresh in eligible && future !in eligible)
    }

    @Test fun emptyPoolHasNoEligible() = assertTrue(emptyList<TopicProgress>().none { it.isEligibleOn(today) })
}

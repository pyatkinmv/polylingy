package com.polylingy.core.progress

import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

enum class ProgressStatus {
    /** Never graduated yet. */
    NEW,

    /** Graduated at least once; shown again on [TopicProgress.nextReviewDate]. */
    TO_REVIEW,
}

/** Scheduling state of a topic. Never stored — always derived from the journal by [replay]. */
data class TopicProgress(
    val topicId: Uuid,
    val status: ProgressStatus = ProgressStatus.NEW,
    val easeFactor: Double = 2.5,
    val intervalDays: Int = 1,
    val nextReviewDate: LocalDate? = null,
    val lastGraduatedOn: LocalDate? = null,
) {
    fun isEligibleOn(today: LocalDate): Boolean = when (status) {
        ProgressStatus.NEW -> true
        ProgressStatus.TO_REVIEW -> nextReviewDate != null && nextReviewDate <= today
    }
}

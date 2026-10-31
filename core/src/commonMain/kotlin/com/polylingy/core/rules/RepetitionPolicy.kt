package com.polylingy.core.rules

import com.polylingy.core.progress.ProgressStatus
import com.polylingy.core.progress.TopicProgress
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.roundToInt

/** Decides when a topic is shown again after it graduates. */
interface RepetitionPolicy {
    fun advance(current: TopicProgress, mistakes: Int, today: LocalDate): TopicProgress
}

/** SM-2 variant where the quality score comes from the number of mistakes made before graduating. */
class Sm2Policy : RepetitionPolicy {
    override fun advance(current: TopicProgress, mistakes: Int, today: LocalDate): TopicProgress {
        val q = when (mistakes) { 0 -> 5; 1 -> 4; 2 -> 3; else -> 2 }
        val efDelta = when (q) { 5 -> 0.1; 4 -> 0.0; 3 -> -0.15; else -> -0.3 }
        val newEf = (current.easeFactor + efDelta).coerceAtLeast(MIN_EASE_FACTOR)
        val newInterval = if (q > 2) (current.intervalDays * newEf).roundToInt() else 1
        return current.copy(
            status = ProgressStatus.TO_REVIEW,
            easeFactor = newEf,
            intervalDays = newInterval,
            nextReviewDate = today.plus(newInterval, DateTimeUnit.DAY),
            lastGraduatedOn = today,
        )
    }

    private companion object {
        const val MIN_EASE_FACTOR = 1.3
    }
}

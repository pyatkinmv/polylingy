package com.polylingy.core.content

import com.polylingy.content.Path
import com.polylingy.content.Problem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A course's policies as written in its file: a [preset], and any of the four parts to replace that part of it.
 *
 * ```json
 * "rules": { "preset": "ladder", "grading": { "type": "threshold", "min": 0.75 } }
 * ```
 */
@Serializable
data class RulesSpec(
    val preset: Preset = Preset.LADDER,
    val grading: GradingSpec? = null,
    val mastery: MasterySpec? = null,
    val scheduler: SchedulerSpec? = null,
    val picker: PickerSpec? = null,
) {
    fun validate(at: Path): List<Problem> = buildList {
        when (grading) {
            is GradingSpec.Threshold -> if (grading.min <= 0 || grading.min > 1) add(Problem(at / "grading" / "min", "must be more than 0 and at most 1, like 0.75"))
            GradingSpec.AllCorrect, null -> {}
        }
        if (mastery is MasterySpec.Consecutive) {
            if (mastery.learning < 1) add(Problem(at / "mastery" / "learning", "must be at least 1"))
            if (mastery.review < 1) add(Problem(at / "mastery" / "review", "must be at least 1"))
        }
        if (scheduler is SchedulerSpec.Ladder) {
            if (scheduler.days.isEmpty()) add(Problem(at / "scheduler" / "days", "needs at least one interval"))
            scheduler.days.forEachIndexed { i, d -> if (d < 1) add(Problem((at / "scheduler" / "days")[i], "must be at least 1 day")) }
        }
    }
}

/** The ready sets of docs/migration-plan.md, stage 4. */
@Serializable
enum class Preset {
    /** All correct, 1 in a row, learned after one round. */
    @SerialName("quick") QUICK,

    /** All correct, 3 in a row while learning and 1 while reviewing, ladder 1 · 3 · 7 · 14 · 30, then learned. */
    @SerialName("ladder") LADDER,

    /** All correct, 3 in a row while learning and 1 while reviewing, SM-2. */
    @SerialName("sm2") SM2,
}

@Serializable
sealed interface GradingSpec {
    @Serializable @SerialName("all-correct") data object AllCorrect : GradingSpec

    @Serializable @SerialName("threshold") data class Threshold(val min: Double) : GradingSpec
}

@Serializable
sealed interface MasterySpec {
    @Serializable @SerialName("consecutive") data class Consecutive(val learning: Int, val review: Int) : MasterySpec
}

@Serializable
sealed interface SchedulerSpec {
    @Serializable @SerialName("once") data object Once : SchedulerSpec

    @Serializable @SerialName("ladder") data class Ladder(val days: List<Int> = listOf(1, 3, 7, 14, 30), val retire: Boolean = true) : SchedulerSpec

    @Serializable @SerialName("sm2") data object Sm2 : SchedulerSpec
}

@Serializable
sealed interface PickerSpec {
    @Serializable @SerialName("reviews-first") data object ReviewsFirst : PickerSpec

    /** Reviews first, then new topics in random order. */
    @Serializable @SerialName("random") data object Random : PickerSpec
}

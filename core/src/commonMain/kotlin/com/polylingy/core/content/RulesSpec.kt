package com.polylingy.core.content

import com.polylingy.content.CourseStyle
import com.polylingy.content.Path
import com.polylingy.content.Problem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A course's policies as written in its file and in its settings: a [preset], and any of the four parts to replace
 * that part of it. Settings are always stored [resolved], with every part written out.
 *
 * ```json
 * "rules": { "preset": "ladder", "grading": { "type": "threshold", "min": 0.75 }, "picker": { "type": "conveyor", "newPerDay": 10 } }
 * ```
 */
@Serializable
data class RulesSpec(
    val preset: Preset = Preset.LADDER,
    val grading: GradingSpec? = null,
    val mastery: MasterySpec? = null,
    val scheduler: SchedulerSpec? = null,
    val picker: PickerSpec? = null,
    /** How exercises look; not one of the four policies, and not part of a preset. */
    val display: DisplaySpec? = null,
) {
    /** Every part written out: the ones given, the rest from the [preset]. */
    fun resolved(): RulesSpec {
        val base = preset.spec
        return copy(
            grading = grading ?: base.grading,
            mastery = mastery ?: base.mastery,
            scheduler = scheduler ?: base.scheduler,
            picker = picker ?: base.picker,
            display = display ?: DisplaySpec(),
        )
    }

    /** The preset whose grading, mastery and scheduler these are, if any; the picker isn't part of the comparison. */
    fun matchingPreset(): Preset? {
        val own = resolved()
        return Preset.entries.firstOrNull { preset ->
            val base = preset.spec
            own.grading == base.grading && own.mastery == base.mastery && own.scheduler == base.scheduler
        }
    }

    fun validate(at: Path): List<Problem> = buildList {
        if (grading is GradingSpec.Threshold && (grading.min <= 0 || grading.min > 1)) {
            add(Problem(at / "grading" / "min", "must be more than 0 and at most 1, like 0.75"))
        }
        if (mastery is MasterySpec.Consecutive) {
            if (mastery.learning < 1) add(Problem(at / "mastery" / "learning", "must be at least 1"))
            if (mastery.review < 1) add(Problem(at / "mastery" / "review", "must be at least 1"))
        }
        if (scheduler is SchedulerSpec.Ladder) {
            if (scheduler.days.isEmpty()) add(Problem(at / "scheduler" / "days", "needs at least one interval"))
            scheduler.days.forEachIndexed { i, d -> if (d < 1) add(Problem((at / "scheduler" / "days")[i], "must be at least 1 day")) }
        }
        picker?.newPerDay?.let { if (it < 1) add(Problem(at / "picker" / "newPerDay", "must be at least 1, or left out for no limit")) }
        display?.palette?.let { if (it.isBlank()) add(Problem(at / "display" / "palette", "must not be empty: leave it out, or name a palette")) }
    }
}

/**
 * How a course's exercises look to the learner. [annotations]: show the small text a course puts over words
 * (`[老师](lǎoshī)`); off, only the words show. [avoidTyping]: of a topic's exercises, prefer those answered without a
 * keyboard ([com.polylingy.content.Exercise.needsKeyboard]); a topic that has only typing ones keeps them.
 * [palette]: the id of the colour palette the course is drawn in (the app's list of palettes, see docs/content-format.md); null — the app's
 * default; an id this version doesn't know is drawn in the default too. [style]: how marks and details are set off, see [CourseStyle].
 */
@Serializable
data class DisplaySpec(
    val annotations: Boolean = true,
    val avoidTyping: Boolean = false,
    val palette: String? = null,
    val style: CourseStyle = CourseStyle.STRICT,
)

/** Ready sets of grading, mastery and scheduler (docs/learning-model.md, section 4); each comes with the conveyor picker. */
@Serializable
enum class Preset {
    /** All correct, 1 in a row, learned after one round. */
    @SerialName("quick") QUICK,

    /** All correct, 3 in a row while learning and 1 while reviewing, ladder 1 · 3 · 7 · 14 · 30, then learned. */
    @SerialName("ladder") LADDER,

    /** All correct, 3 in a row while learning and 1 while reviewing, SM-2. */
    @SerialName("sm2") SM2,
    ;

    /** This preset with every part written out. */
    val spec: RulesSpec
        get() = when (this) {
            QUICK -> RulesSpec(this, GradingSpec.AllCorrect(), MasterySpec.Consecutive(1, 1), SchedulerSpec.Once, PickerSpec.Conveyor())
            LADDER -> RulesSpec(this, GradingSpec.AllCorrect(), MasterySpec.Consecutive(3, 1), SchedulerSpec.Ladder(), PickerSpec.Conveyor())
            SM2 -> RulesSpec(this, GradingSpec.AllCorrect(), MasterySpec.Consecutive(3, 1), SchedulerSpec.Sm2, PickerSpec.Conveyor())
        }
}

/** When an answer counts. [typos] — whether an item with a typo counts as right; checking always reports it as a typo. */
@Serializable
sealed interface GradingSpec {
    val typos: Boolean

    @Serializable @SerialName("all-correct") data class AllCorrect(override val typos: Boolean = true) : GradingSpec

    @Serializable @SerialName("threshold") data class Threshold(val min: Double, override val typos: Boolean = true) : GradingSpec
}

@Serializable
sealed interface MasterySpec {
    @Serializable @SerialName("consecutive") data class Consecutive(val learning: Int, val review: Int) : MasterySpec
}

@Serializable
sealed interface SchedulerSpec {
    @Serializable @SerialName("once") data object Once : SchedulerSpec

    /** [retire] off: after the last step every gap is twice the one before. */
    @Serializable @SerialName("ladder") data class Ladder(val days: List<Int> = listOf(1, 3, 7, 14, 30), val retire: Boolean = true) : SchedulerSpec

    @Serializable @SerialName("sm2") data object Sm2 : SchedulerSpec
}

/** Which exercise of a topic is shown next (docs/learning-model.md, section 7, «Пикер»). */
@Serializable
enum class ExerciseSpec {
    /** A random one, never the one answered last. */
    @SerialName("random-not-last") RANDOM_NOT_LAST,

    /** One after another in the order of the course, round and round. */
    @SerialName("in-order") IN_ORDER,

    /** First the one missed longest ago and not answered right since, but never in the round it was missed in; then in order. */
    @SerialName("missed-first") MISSED_FIRST,
}

/**
 * What comes next in a session (docs/learning-model.md, section 7, «Пикер»). [newPerDay] — new topics a day; null — no limit.
 * [exercise] — which exercise of a topic is shown; it is chosen apart from the three kinds of picker.
 */
@Serializable
sealed interface PickerSpec {
    val newPerDay: Int?
    val exercise: ExerciseSpec

    /** Started → due → new, in course order; stays on a topic until its round is over. */
    @Serializable @SerialName("conveyor") data class Conveyor(override val newPerDay: Int? = null, override val exercise: ExerciseSpec = ExerciseSpec.RANDOM_NOT_LAST) : PickerSpec

    /** Started → due → new, each group in random order; stays on a topic until its round is over. */
    @Serializable @SerialName("shuffle") data class Shuffle(override val newPerDay: Int? = null, override val exercise: ExerciseSpec = ExerciseSpec.RANDOM_NOT_LAST) : PickerSpec

    /** Started → due → new, in course order; another topic after every answer. */
    @Serializable @SerialName("alternate") data class Alternate(override val newPerDay: Int? = null, override val exercise: ExerciseSpec = ExerciseSpec.RANDOM_NOT_LAST) : PickerSpec
}

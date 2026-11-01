package com.polylingy.core.learning

import com.polylingy.core.content.GradingSpec
import com.polylingy.core.content.MasterySpec
import com.polylingy.core.content.PickerSpec
import com.polylingy.core.content.Preset
import com.polylingy.core.content.RulesSpec
import com.polylingy.core.content.SchedulerSpec
import kotlin.random.Random

/** Builds a course's policies from its file: the preset, with the parts the file names replaced. No rules — the ladder. */
fun RulesSpec?.toRules(random: Random): CourseRules<*, PassFail> {
    val spec = this ?: RulesSpec()
    val base = presetSpec(spec.preset)
    val grading = when (val g = spec.grading ?: base.grading!!) {
        GradingSpec.AllCorrect -> AllCorrect
        is GradingSpec.Threshold -> Threshold(g.min)
    }
    val mastery = when (val m = spec.mastery ?: base.mastery!!) {
        is MasterySpec.Consecutive -> Consecutive(m.learning, m.review)
    }
    val picker = when (spec.picker ?: base.picker!!) {
        PickerSpec.ReviewsFirst -> ReviewsFirst
        PickerSpec.Random -> RandomOrder(random)
    }
    return when (val s = spec.scheduler ?: base.scheduler!!) {
        SchedulerSpec.Once -> CourseRules(grading, mastery, Once, picker)
        is SchedulerSpec.Ladder -> CourseRules(grading, mastery, Ladder(s.days, s.retire), picker)
        SchedulerSpec.Sm2 -> CourseRules(grading, mastery, Sm2Scheduler, picker)
    }
}

private fun presetSpec(preset: Preset) = when (preset) {
    Preset.QUICK -> RulesSpec(preset, GradingSpec.AllCorrect, MasterySpec.Consecutive(1, 1), SchedulerSpec.Once, PickerSpec.ReviewsFirst)
    Preset.LADDER -> RulesSpec(preset, GradingSpec.AllCorrect, MasterySpec.Consecutive(3, 1), SchedulerSpec.Ladder(), PickerSpec.ReviewsFirst)
    Preset.SM2 -> RulesSpec(preset, GradingSpec.AllCorrect, MasterySpec.Consecutive(3, 1), SchedulerSpec.Sm2, PickerSpec.ReviewsFirst)
}

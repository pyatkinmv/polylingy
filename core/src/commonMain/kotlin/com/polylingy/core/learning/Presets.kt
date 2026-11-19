package com.polylingy.core.learning

import com.polylingy.core.content.ExerciseSpec
import com.polylingy.core.content.GradingSpec
import com.polylingy.core.content.MasterySpec
import com.polylingy.core.content.PickerSpec
import com.polylingy.core.content.RulesSpec
import com.polylingy.core.content.SchedulerSpec
import kotlin.random.Random

/** Builds a course's policies from its rules: the preset, with the parts the rules name replaced. No rules — the ladder. */
fun RulesSpec?.toRules(random: Random): CourseRules<*, PassFail> {
    val spec = (this ?: RulesSpec()).resolved()
    val grading = when (val g = spec.grading!!) {
        is GradingSpec.AllCorrect -> AllCorrect(g.typos)
        is GradingSpec.Threshold -> Threshold(g.min, g.typos)
    }
    val mastery = when (val m = spec.mastery!!) {
        is MasterySpec.Consecutive -> Consecutive(m.learning, m.review)
    }
    val picker = spec.picker!!.toPicker(random)
    return when (val s = spec.scheduler!!) {
        SchedulerSpec.Once -> CourseRules(grading, mastery, Once, picker)
        is SchedulerSpec.Ladder -> CourseRules(grading, mastery, Ladder(s.days, s.retire), picker)
        SchedulerSpec.Sm2 -> CourseRules(grading, mastery, Sm2Scheduler, picker)
    }
}

/** The ready pickers (docs/learning-model.md, section 7, «Пикер»): which queue, focus and exercise choice each one is. */
fun PickerSpec.toPicker(random: Random): Picker = when (this) {
    is PickerSpec.Conveyor -> Picker(StartedFirst(newPerDay), Stay, exercise.toChoice(random))
    is PickerSpec.Shuffle -> Picker(Shuffled(newPerDay, random), Stay, exercise.toChoice(random))
    is PickerSpec.Alternate -> Picker(StartedFirst(newPerDay), MoveOn, exercise.toChoice(random))
}

/** The part of a picker that chooses the exercise. */
fun ExerciseSpec.toChoice(random: Random): ExerciseChoice = when (this) {
    ExerciseSpec.RANDOM_NOT_LAST -> RandomNotLast(random)
    ExerciseSpec.IN_ORDER -> InCourseOrder
    ExerciseSpec.MISSED_FIRST -> MissedFirst
}

package com.polylingy.app.study

import androidx.lifecycle.ViewModel
import com.polylingy.content.ItemCheck
import com.polylingy.content.ItemResult
import com.polylingy.content.Response
import com.polylingy.content.emptyResponse
import com.polylingy.content.isComplete
import com.polylingy.core.content.Course
import com.polylingy.core.learning.TopicStatus
import com.polylingy.core.study.AnswerOutcome
import com.polylingy.core.study.NextExercise
import com.polylingy.core.study.PracticeScope
import com.polylingy.core.study.ProgressCounts
import com.polylingy.core.study.StudyService
import com.polylingy.core.study.TopicGroup
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

sealed interface StudyState {
    /**
     * An exercise on screen: [response] is what the learner has entered; after "Check" [outcome] holds the checks.
     * [review] — the topic was passed before, this is a review. [seed] fixes the shuffled order for this showing.
     */
    data class Exercise(
        val next: NextExercise,
        val response: Response,
        val outcome: AnswerOutcome?,
        val review: Boolean,
        val seed: Int,
    ) : StudyState {
        val checks: List<ItemCheck>? get() = outcome?.checks
    }

    /** Nothing left today in the unit (or course): [counts] of the same scope; [canPractise] — there is something to practise in it. */
    data class Done(val counts: ProgressCounts, val canPractise: Boolean = false) : StudyState

    /** The course or topic is gone, or has no exercise this version can show. */
    data object Missing : StudyState
}

/**
 * Studying: one exercise after another, from [topicId] if the learner picked a topic, else as the course's picker
 * chooses — in [unitId], or in the whole course. All rules live in [StudyService]; this only keeps the screen's state.
 */
class StudyViewModel(
    private val study: StudyService,
    val courseId: Uuid,
    val unitId: Uuid?,
    topicId: Uuid?,
) : ViewModel() {
    val course: Course? = study.progress(courseId)?.course
    private var shown = 0

    private val _state = MutableStateFlow(show(topicId?.let { study.practise(courseId, it) } ?: study.next(courseId, unitId)))
    val state: StateFlow<StudyState> = _state.asStateFlow()

    fun change(response: Response) {
        val current = _state.value as? StudyState.Exercise ?: return
        if (current.outcome == null) _state.value = current.copy(response = response)
    }

    fun check() {
        val current = _state.value as? StudyState.Exercise ?: return
        if (current.outcome != null) return
        val next = current.next
        if (!next.exercise.isComplete(current.response)) return
        log.info { "User checks exercise ${next.exercise.id} of topic ${next.topic.id}" }
        _state.value = current.copy(outcome = study.answer(courseId, next.topic.id, next.exercise.id, current.response))
    }

    /** The next exercise, or "done for today". */
    fun next() {
        val current = _state.value as? StudyState.Exercise ?: return
        log.info { "User goes on from exercise ${current.next.exercise.id}" }
        _state.value = show(study.next(courseId, unitId, current = current.next.topic.id, lastExercise = current.next.exercise.id))
    }

    private fun show(next: NextExercise?): StudyState {
        val progress = study.progress(courseId) ?: return StudyState.Missing
        if (next == null) {
            if (course == null) return StudyState.Missing
            val counts = unitId?.let { id -> progress.units.firstOrNull { it.unit.id == id }?.counts } ?: progress.counts
            log.info { "Nothing left today in course $courseId${unitId?.let { " unit $it" }.orEmpty()}" }
            return StudyState.Done(counts, study.canPractise(courseId, unitId?.let(PracticeScope::Unit) ?: PracticeScope.Course))
        }
        val topic = progress.allTopics.first { it.topic.id == next.topic.id }
        val review = topic.group == TopicGroup.TO_REVIEW
        log.info { "Shows exercise ${next.exercise.id} of topic ${next.topic.id} \"${next.topic.title}\"${if (review) " (review)" else ""}" }
        return StudyState.Exercise(next, next.exercise.emptyResponse(), outcome = null, review = review, seed = shown++)
    }
}

/** What the check found, the same words for every exercise type: "All correct", "1 mistake · 2 typos". */
fun checkLine(checks: List<ItemCheck>): String {
    val mistakes = checks.count { it.result == ItemResult.WRONG }
    val typos = checks.count { it.result == ItemResult.TYPO }
    if (mistakes == 0 && typos == 0) return "All correct"
    return listOfNotNull(
        mistakes.takeIf { it > 0 }?.let { plural(it, "mistake") },
        typos.takeIf { it > 0 }?.let { plural(it, "typo") },
    ).joinToString(" · ")
}

/** What changed for the topic, from its status before and after the answer; null when nothing worth saying did. */
fun progressLine(outcome: AnswerOutcome, describeDay: (LocalDate) -> String): String? {
    if (outcome.before == outcome.after) return null
    return when (val after = outcome.after) {
        is TopicStatus.Passed -> "Topic passed — next review ${describeDay(after.due)}"
        TopicStatus.Learned -> "Topic learned for good"
        else -> null
    }
}

private fun plural(n: Int, noun: String) = "$n $noun${if (n == 1) "" else "s"}"

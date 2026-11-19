package com.polylingy.app.study

import androidx.lifecycle.ViewModel
import com.polylingy.content.Response
import com.polylingy.content.emptyResponse
import com.polylingy.content.isComplete
import com.polylingy.core.content.Course
import com.polylingy.core.study.NextExercise
import com.polylingy.core.study.PracticeAnswer
import com.polylingy.core.study.PracticeScope
import com.polylingy.core.study.PracticeState
import com.polylingy.core.study.StudyService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

/** What a finished practice says: how many were answered and how many passed, and the same by topic in the order they came. */
data class PracticeSummary(val answered: Int, val right: Int, val byTopic: List<TopicTally>) {
    val percent: Int get() = if (answered == 0) 0 else 100 * right / answered
}

data class TopicTally(val title: String, val answered: Int, val right: Int) {
    val percent: Int get() = if (answered == 0) 0 else 100 * right / answered
}

sealed interface PracticeUi {
    /** An exercise on screen, drawn like the plan's; [answered] and [right] so far in this practice. */
    data class Exercise(val state: StudyState.Exercise, val answered: Int, val right: Int) : PracticeUi

    data class Summary(val summary: PracticeSummary) : PracticeUi

    /** The course is gone, or nothing in it can be practised. */
    data object Nothing : PracticeUi
}

/**
 * Extra practice over [scope]: one exercise after another until the learner finishes. It keeps only its own answers
 * ([PracticeState]); the plan isn't read or changed here, all rules are in [StudyService].
 */
class PracticeViewModel(private val study: StudyService, val courseId: Uuid, val scope: PracticeScope) : ViewModel() {
    val course: Course? = study.progress(courseId)?.course
    private var practice = PracticeState()
    private var shown = 0
    private val titles = HashMap<Uuid, String>()

    private val _state = MutableStateFlow<PracticeUi>(show(study.practiceNext(courseId, scope, practice)))
    val state: StateFlow<PracticeUi> = _state.asStateFlow()

    fun change(response: Response) {
        val current = _state.value as? PracticeUi.Exercise ?: return
        if (current.state.outcome == null) _state.value = current.copy(state = current.state.copy(response = response))
    }

    fun check() {
        val current = _state.value as? PracticeUi.Exercise ?: return
        if (current.state.outcome != null) return
        val next = current.state.next
        if (!next.exercise.isComplete(current.state.response)) return
        log.info { "User checks exercise ${next.exercise.id} of topic ${next.topic.id} in practice" }
        val result = study.practiceAnswer(courseId, next.topic.id, next.exercise.id, current.state.response)
        titles[next.topic.id] = next.topic.title
        practice = PracticeState(practice.answers + PracticeAnswer(next.topic.id, next.exercise.id, result.passed), next.topic.id)
        _state.value = PracticeUi.Exercise(current.state.copy(outcome = result.outcome), practice.answers.size, practice.answers.count { it.passed })
    }

    fun next() {
        if ((_state.value as? PracticeUi.Exercise)?.state?.outcome == null) return
        _state.value = show(study.practiceNext(courseId, scope, practice))
    }

    /** The end of the practice: what was done. */
    fun finish() {
        log.info { "User finishes practice of course $courseId: ${practice.answers.size} answers" }
        _state.value = PracticeUi.Summary(summary())
    }

    /** "Practise more" after the summary: a new practice, from nothing. */
    fun more() {
        practice = PracticeState()
        _state.value = show(study.practiceNext(courseId, scope, practice))
    }

    private fun summary(): PracticeSummary {
        val order = practice.answers.map { it.topicId }.distinct()
        return PracticeSummary(
            practice.answers.size,
            practice.answers.count { it.passed },
            order.map { id -> practice.answers.filter { it.topicId == id }.let { TopicTally(titles[id] ?: "?", it.size, it.count { a -> a.passed }) } },
        )
    }

    private fun show(next: NextExercise?): PracticeUi {
        if (course == null) return PracticeUi.Nothing
        if (next == null) return if (practice.answers.isEmpty()) PracticeUi.Nothing else PracticeUi.Summary(summary())
        log.info { "Practice shows exercise ${next.exercise.id} of topic ${next.topic.id} \"${next.topic.title}\"" }
        titles[next.topic.id] = next.topic.title
        val state = StudyState.Exercise(next, next.exercise.emptyResponse(), outcome = null, review = false, seed = shown++)
        return PracticeUi.Exercise(state, practice.answers.size, practice.answers.count { it.passed })
    }
}

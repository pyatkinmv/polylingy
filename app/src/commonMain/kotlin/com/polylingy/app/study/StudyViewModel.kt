package com.polylingy.app.study

import androidx.lifecycle.ViewModel
import com.polylingy.core.content.CourseService
import com.polylingy.core.content.Exercise
import com.polylingy.core.content.Topic
import com.polylingy.core.journal.AutoCheck
import com.polylingy.core.journal.Rating
import com.polylingy.core.ports.CourseRepository
import com.polylingy.core.rules.AnswerCheck
import com.polylingy.core.rules.AnswerChecker
import com.polylingy.core.study.StudyService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random
import kotlin.uuid.Uuid

sealed interface StudyState {
    val topic: Topic? get() = null

    data class Answering(override val topic: Topic, val exercise: Exercise, val input: String = "") : StudyState

    data class Reviewing(
        override val topic: Topic,
        val exercise: Exercise,
        val input: String,
        val check: AnswerCheck,
        val phrase: String,
    ) : StudyState

    data object Done : StudyState
}

class StudyViewModel(
    private val courseId: Uuid,
    private val study: StudyService,
    private val editor: CourseService,
    courses: CourseRepository,
    private val random: Random = Random.Default,
) : ViewModel() {
    val courseName: String = courses.get(courseId)?.name.orEmpty()

    private val _state = MutableStateFlow(next(current = null))
    val state: StateFlow<StudyState> = _state.asStateFlow()

    fun onInput(text: String) {
        val answering = _state.value as? StudyState.Answering ?: return
        _state.value = answering.copy(input = text)
    }

    fun submit() {
        val answering = _state.value as? StudyState.Answering ?: return
        val check = AnswerChecker.check(answering.exercise, answering.input)
        _state.value = StudyState.Reviewing(
            answering.topic, answering.exercise, answering.input, check, phrase = PHRASES.getValue(check.result).random(random),
        )
    }

    /** The user's self-rating; it decides graduation (see RatingThenAutoCheck). */
    fun rate(correct: Boolean) {
        val reviewing = _state.value as? StudyState.Reviewing ?: return
        study.submitAnswer(
            courseId, reviewing.topic.id, reviewing.exercise.id, reviewing.input, reviewing.check.result,
            if (correct) Rating.CORRECT else Rating.INCORRECT,
        )
        _state.value = next(current = reviewing.topic.id)
    }

    fun removeCurrentTopic() {
        val topic = _state.value.topic ?: return
        editor.deleteTopic(courseId, topic.id)
        _state.value = next(current = null)
    }

    private fun next(current: Uuid?): StudyState =
        study.nextExercise(courseId, current)?.let { StudyState.Answering(it.topic, it.exercise) } ?: StudyState.Done

    private companion object {
        val PHRASES = mapOf(
            AutoCheck.CORRECT to listOf("Correct!", "Well done!", "Right!", "Got it!", "Yes!"),
            AutoCheck.PARTIAL to listOf("Partially right.", "Some correct!", "Half right!", "Almost!", "Close!"),
            AutoCheck.INCORRECT to listOf("Incorrect.", "Not quite.", "Try again.", "Missed it.", "Wrong."),
        )
    }
}

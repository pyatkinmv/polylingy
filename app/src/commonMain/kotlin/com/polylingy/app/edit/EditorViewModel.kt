package com.polylingy.app.edit

import androidx.lifecycle.ViewModel
import com.polylingy.content.Content
import com.polylingy.content.Exercise
import com.polylingy.content.ItemCheck
import com.polylingy.content.Response
import com.polylingy.content.emptyResponse
import com.polylingy.core.content.Course
import com.polylingy.core.content.CoursePart
import com.polylingy.core.content.CourseParts
import com.polylingy.core.content.CourseService
import com.polylingy.core.content.PartCheck
import com.polylingy.core.content.PartException
import com.polylingy.core.content.PartProblem
import com.polylingy.core.content.Topic
import com.polylingy.core.ports.CourseRepository
import com.polylingy.core.study.StudyService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

/** The part being edited as the preview shows it: a unit's title and theory with its topics, or one topic. */
data class EditedPart(val title: String, val theory: Content?, val topics: List<Topic>)

/**
 * What the preview shows: the theory ([topic] null) or an exercise of a topic. [response] is what was entered,
 * [checks] what "Check" found. Nothing of it goes to the journal.
 */
data class Preview(val topic: Int?, val exercise: Int, val shown: Exercise?, val response: Response?, val checks: List<ItemCheck>?)

/** The answer to "Save": saved, or first confirm that these topics (dropped from the unit's JSON) will be deleted. */
sealed interface SaveOutcome {
    data object Saved : SaveOutcome
    data class ConfirmDropped(val topics: List<Topic>) : SaveOutcome
    data class Failed(val problems: List<PartProblem>) : SaveOutcome
}

/**
 * The editor of one unit or topic ([part]): its JSON, checked as a part of the course on every change, and a preview
 * to try. An empty text is a new part not started yet; the editor shows the template in grey.
 */
class EditorViewModel(
    private val service: CourseService,
    private val study: StudyService,
    courses: CourseRepository,
    val courseId: Uuid,
    val part: CoursePart,
) : ViewModel() {
    /** The course as it was when the editor opened; null if it is gone. */
    val before: Course? = courses.get(courseId)

    val isNew: Boolean = when (part) {
        is CoursePart.Unit -> part.unitId == null
        is CoursePart.Topic -> part.topicId == null
    }

    val template: String = if (part is CoursePart.Unit) CourseParts.UNIT_TEMPLATE else CourseParts.TOPIC_TEMPLATE

    /** The JSON the part had; empty for a new one. */
    val original: String = if (isNew) "" else before?.let { service.partJson(courseId, part) }.orEmpty()

    /** Where the part sits, so it is found again in the edited course whatever its id becomes. */
    private val unitIndex: Int? = when (part) {
        is CoursePart.Unit -> part.unitId?.let { id -> before?.units?.indexOfFirst { it.id == id } } ?: before?.units?.size
        is CoursePart.Topic -> part.unitId?.let { id -> before?.units?.indexOfFirst { it.id == id } }
    }
    private val topicIndex: Int? = (part as? CoursePart.Topic)?.let { topic ->
        val topics = unitIndex?.let { before?.units?.getOrNull(it)?.topics } ?: before?.topics.orEmpty()
        topic.topicId?.let { id -> topics.indexOfFirst { it.id == id } } ?: topics.size
    }

    private val _text = MutableStateFlow(original)
    val text: StateFlow<String> = _text.asStateFlow()

    private val _check = MutableStateFlow(checkOf(original))

    /** The last check of [text]; null while it is empty. */
    val check: StateFlow<PartCheck?> = _check.asStateFlow()

    private val _edited = MutableStateFlow(_check.value?.course?.let(::find))

    /** The part as the last valid text made it; kept while the text has problems, so the preview stays. */
    val edited: StateFlow<EditedPart?> = _edited.asStateFlow()

    private val _preview = MutableStateFlow(fit(Preview(topic = null, exercise = 0, shown = null, response = null, checks = null)))
    val preview: StateFlow<Preview> = _preview.asStateFlow()

    val changed: Boolean get() = _text.value != original

    fun change(text: String) {
        _text.value = text
        val check = checkOf(text)
        _check.value = check
        check?.course?.let { _edited.value = find(it) }
        if (text.isBlank()) _edited.value = null
        _preview.value = fit(_preview.value)
    }

    /** "Use it as a start": the grey template becomes the text. */
    fun useTemplate() = change(template)

    fun showTheory() {
        _preview.value = fit(_preview.value.copy(topic = null, exercise = 0))
    }

    fun showTopic(index: Int) {
        _preview.value = fit(_preview.value.copy(topic = index, exercise = 0))
    }

    fun showExercise(index: Int) {
        _preview.value = fit(_preview.value.copy(exercise = index))
    }

    fun answer(response: Response) {
        val p = _preview.value
        if (p.checks == null) _preview.value = p.copy(response = response)
    }

    /** Checks the answer in the preview as the course's settings show it; nothing is written to the journal. */
    fun checkAnswer() {
        val p = _preview.value
        val exercise = p.shown ?: return
        val response = p.response ?: return
        if (p.checks != null) return
        _preview.value = p.copy(checks = study.preview(courseId, exercise, response))
    }

    fun tryAgain() {
        val p = _preview.value
        _preview.value = p.copy(response = p.shown?.emptyResponse(), checks = null)
    }

    /**
     * Saves the text. A unit that dropped some of its topics is saved only once [confirmed]: those topics are deleted.
     */
    fun save(confirmed: Boolean = false): SaveOutcome {
        val check = _check.value ?: return SaveOutcome.Failed(emptyList())
        val after = check.course ?: return SaveOutcome.Failed(check.problems)
        val unitId = (part as? CoursePart.Unit)?.unitId
        if (unitId != null && !confirmed && before != null) {
            val dropped = CourseParts.droppedTopics(before, after, unitId)
            if (dropped.isNotEmpty()) return SaveOutcome.ConfirmDropped(dropped)
        }
        return try {
            service.savePart(courseId, part, _text.value)
            SaveOutcome.Saved
        } catch (e: PartException) {
            log.warn { "Saving $part of course $courseId failed: ${e.problems}" }
            SaveOutcome.Failed(e.problems)
        }
    }

    private fun checkOf(text: String): PartCheck? =
        if (text.isBlank() || before == null) null else service.checkPart(courseId, part, text)

    private fun find(course: Course): EditedPart? = when (part) {
        is CoursePart.Unit -> course.units.getOrNull(unitIndex ?: return null)?.let { EditedPart(it.title, it.theory, it.topics) }
        is CoursePart.Topic -> {
            val topics = if (part.unitId == null) course.topics else course.units.getOrNull(unitIndex ?: return null)?.topics.orEmpty()
            topics.getOrNull(topicIndex ?: return null)?.let { EditedPart(it.title, null, listOf(it)) }
        }
    }

    /**
     * Keeps the preview on something that exists after an edit: the theory if there is one and nothing else is
     * chosen, else the first topic. When the exercise shown changes, what was entered for it is dropped.
     */
    private fun fit(p: Preview): Preview {
        val edited = _edited.value ?: return p.copy(shown = null, response = null, checks = null)
        val topic = when {
            p.topic == null && edited.theory != null -> null
            edited.topics.isEmpty() -> null
            else -> (p.topic ?: 0).coerceIn(0, edited.topics.lastIndex)
        }
        val exercises = topic?.let { edited.topics[it].exercises }.orEmpty()
        val index = if (exercises.isEmpty()) 0 else p.exercise.coerceIn(0, exercises.lastIndex)
        val exercise = exercises.getOrNull(index)
        if (exercise == p.shown && topic == p.topic) return p.copy(exercise = index)
        return Preview(topic, index, exercise, exercise?.emptyResponse(), checks = null)
    }
}

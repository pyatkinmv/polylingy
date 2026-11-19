package com.polylingy.app.course

import androidx.lifecycle.ViewModel
import com.polylingy.core.content.CoursePart
import com.polylingy.core.content.CourseService
import com.polylingy.core.content.PartException
import com.polylingy.core.content.PartProblem
import com.polylingy.core.learning.TopicStatus
import com.polylingy.core.study.CourseProgress
import com.polylingy.core.study.NextExercise
import com.polylingy.core.study.PracticeScope
import com.polylingy.core.study.StudyService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

/** Where a click on "Continue" or on a topic leads. */
sealed interface Start {
    /** Exercises, keeping to [unitId] (null — the whole course), of [topicId] if the learner picked one. */
    data class Study(val unitId: Uuid?, val topicId: Uuid?) : Start

    /** The theory of [theoryUnit] first, because nothing in it has been answered yet; then [then]. */
    data class TheoryFirst(val theoryUnit: Uuid, val then: Study) : Start

    /** A topic that rests or is learned for good: its page says when it comes back. */
    data class TopicPage(val topicId: Uuid) : Start

    /** Nothing waits today. */
    data object Nothing : Start
}

/**
 * The state of a course for its screens: every topic's status and what comes next, in [scope] (one unit, or the
 * whole course when null). Read again on [refresh], e.g. when the screen shows after studying.
 */
class CourseViewModel(
    private val study: StudyService,
    private val courses: CourseService,
    val courseId: Uuid,
    private val scope: Uuid? = null,
) : ViewModel() {
    private val _progress = MutableStateFlow(study.progress(courseId))
    val progress: StateFlow<CourseProgress?> = _progress.asStateFlow()

    private val _next = MutableStateFlow(study.next(courseId, scope))
    private val _canPractise = MutableStateFlow(canPractise())

    /** Whether the plan has started anything in this course (or unit) that extra practice can go over. */
    val practisable: StateFlow<Boolean> = _canPractise.asStateFlow()

    private fun canPractise() = study.canPractise(courseId, scope?.let(PracticeScope::Unit) ?: PracticeScope.Course)

    /** What "Continue" would show now; null — nothing left today. */
    val next: StateFlow<NextExercise?> = _next.asStateFlow()

    fun refresh() {
        _progress.value = study.progress(courseId)
        _next.value = study.next(courseId, scope)
        _canPractise.value = canPractise()
    }

    /** Deletes a unit or topic; the problems if the course needs it (a unit's last topic, the course's last unit). */
    fun delete(part: CoursePart): List<PartProblem> = try {
        courses.deletePart(courseId, part)
        refresh()
        emptyList()
    } catch (e: PartException) {
        log.warn { "Deleting $part of course $courseId failed: ${e.problems}" }
        e.problems
    }

    /** "Continue": the next exercise, with the theory first if its unit is new. */
    fun continueStudy(): Start {
        val next = study.next(courseId, scope) ?: return Start.Nothing
        val start = Start.Study(scope, null)
        log.info { "User continues course $courseId${scope?.let { " in unit $it" }.orEmpty()}: topic ${next.topic.id}" }
        val unit = next.unit ?: return start
        return if (study.theoryFirst(courseId, unit.id)) Start.TheoryFirst(unit.id, start) else start
    }

    /** A click on a topic: study it if there is something to do, else show when it comes back. */
    fun open(topicId: Uuid): Start {
        val progress = _progress.value ?: return Start.Nothing
        val topic = progress.allTopics.firstOrNull { it.topic.id == topicId } ?: return Start.Nothing
        log.info { "User opens topic $topicId \"${topic.topic.title}\" (${topic.status})" }
        if (topic.status is TopicStatus.Passed || topic.status == TopicStatus.Learned) return Start.TopicPage(topicId)
        val unit = progress.units.firstOrNull { u -> u.topics.any { it.topic.id == topicId } }?.unit
        val start = Start.Study(unit?.id, topicId)
        return if (unit != null && study.theoryFirst(courseId, unit.id)) Start.TheoryFirst(unit.id, start) else start
    }
}

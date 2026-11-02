package com.polylingy.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.polylingy.app.course.CourseScreen
import com.polylingy.app.course.CourseViewModel
import com.polylingy.app.course.TopicScreen
import com.polylingy.app.course.UnitScreen
import com.polylingy.app.courses.CourseListScreen
import com.polylingy.app.courses.CourseListViewModel
import com.polylingy.app.study.StudyScreen
import com.polylingy.app.study.StudyViewModel
import com.polylingy.app.study.TheoryScreen
import com.polylingy.app.ui.AppTheme
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

@Serializable
internal object CourseListRoute

@Serializable
internal data class CourseRoute(val courseId: String)

@Serializable
internal data class UnitRoute(val courseId: String, val unitId: String)

/** A topic that rests or is learned for good: when it comes back. */
@Serializable
internal data class TopicRoute(val courseId: String, val topicId: String)

/**
 * A unit's theory. [section] — scroll to it and light it up (opened by a link). [study] — the unit is starting:
 * the button under the theory goes on to the exercises, of [topicId] if set, keeping to [studyUnit] (null — the whole
 * course). [back] — opened from an exercise.
 */
@Serializable
internal data class TheoryRoute(
    val courseId: String,
    val unitId: String,
    val section: String? = null,
    val study: Boolean = false,
    val studyUnit: String? = null,
    val topicId: String? = null,
    val back: Boolean = false,
)

/** Exercises: from one topic the learner picked, else as the course's picker chooses (in one unit, or the whole course). */
@Serializable
internal data class StudyRoute(val courseId: String, val unitId: String? = null, val topicId: String? = null)

/** Where a screen may go; screens never see the nav controller itself. */
class Navigator internal constructor(private val nav: NavHostController) {
    fun courses() {
        nav.popBackStack(CourseListRoute, inclusive = false)
    }

    fun course(courseId: Uuid) = go(CourseRoute(courseId.toString()))

    /** Back to the course screen already on the stack, or open it. */
    fun backToCourse(courseId: Uuid) {
        if (!nav.popBackStack<CourseRoute>(inclusive = false)) course(courseId)
    }

    fun unit(courseId: Uuid, unitId: Uuid) = go(UnitRoute(courseId.toString(), unitId.toString()))

    fun backToUnit(courseId: Uuid, unitId: Uuid) {
        if (!nav.popBackStack<UnitRoute>(inclusive = false)) unit(courseId, unitId)
    }

    fun topic(courseId: Uuid, topicId: Uuid) = go(TopicRoute(courseId.toString(), topicId.toString()))

    fun theory(courseId: Uuid, unitId: Uuid, section: Uuid? = null, back: Boolean = false) =
        go(TheoryRoute(courseId.toString(), unitId.toString(), section?.toString(), back = back))

    /** The theory of a unit that is starting, then the exercises, keeping to [studyUnit] (null — the whole course). */
    fun theoryThenStudy(courseId: Uuid, unitId: Uuid, studyUnit: Uuid?, topicId: Uuid?) =
        go(TheoryRoute(courseId.toString(), unitId.toString(), study = true, studyUnit = studyUnit?.toString(), topicId = topicId?.toString()))

    fun study(courseId: Uuid, unitId: Uuid?, topicId: Uuid? = null) =
        go(StudyRoute(courseId.toString(), unitId?.toString(), topicId?.toString()))

    /** From a theory page that starts a unit: the exercises replace the theory, so Back leads to where we came from. */
    fun studyInsteadOfTheory(courseId: Uuid, unitId: Uuid?, topicId: Uuid?) {
        nav.popBackStack()
        study(courseId, unitId, topicId)
    }

    fun back() {
        nav.popBackStack()
    }

    private fun go(route: Any) {
        log.info { "Opens $route" }
        nav.navigate(route)
    }
}

@Composable
fun App(container: AppContainer, text: TextStyle = TextStyle.Default) {
    AppTheme(text) {
        val nav = rememberNavController()
        val go = remember(nav) { Navigator(nav) }
        NavHost(nav, startDestination = CourseListRoute) {
            composable<CourseListRoute> {
                CourseListScreen(
                    viewModel = viewModel {
                        CourseListViewModel(container.library, container.courseService, container.storage.courses, container.study, container.storage::brokenCourses)
                    },
                    files = container.files,
                    today = container.clock.now().localDate,
                    onOpen = go::course,
                )
            }
            composable<CourseRoute> { entry ->
                val route = entry.toRoute<CourseRoute>()
                val id = Uuid.parse(route.courseId)
                CourseScreen(viewModel { CourseViewModel(container.study, id) }, container.clock, go)
            }
            composable<UnitRoute> { entry ->
                val route = entry.toRoute<UnitRoute>()
                val id = Uuid.parse(route.courseId)
                val unitId = Uuid.parse(route.unitId)
                UnitScreen(viewModel { CourseViewModel(container.study, id, scope = unitId) }, unitId, container.clock, go)
            }
            composable<TopicRoute> { entry ->
                val route = entry.toRoute<TopicRoute>()
                val id = Uuid.parse(route.courseId)
                TopicScreen(viewModel { CourseViewModel(container.study, id) }, Uuid.parse(route.topicId), container.clock, go)
            }
            composable<TheoryRoute> { entry ->
                val route = entry.toRoute<TheoryRoute>()
                val course = container.storage.courses.get(Uuid.parse(route.courseId))
                if (course != null) TheoryScreen(
                    course = course,
                    unitId = Uuid.parse(route.unitId),
                    section = route.section?.let(Uuid::parse),
                    images = container.images(course.id),
                    onStart = if (route.study) ({ go.studyInsteadOfTheory(course.id, route.studyUnit?.let(Uuid::parse), route.topicId?.let(Uuid::parse)) }) else null,
                    onBack = if (route.back) go::back else null,
                    go = go,
                )
            }
            composable<StudyRoute> { entry ->
                val route = entry.toRoute<StudyRoute>()
                val courseId = Uuid.parse(route.courseId)
                val unitId = route.unitId?.let(Uuid::parse)
                StudyScreen(
                    viewModel = viewModel { StudyViewModel(container.study, courseId, unitId, route.topicId?.let(Uuid::parse)) },
                    images = container.images(courseId),
                    today = container.clock.now().localDate,
                    go = go,
                )
            }
        }
    }
}

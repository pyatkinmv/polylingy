package com.polylingy.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.polylingy.app.courses.CourseListScreen
import com.polylingy.app.courses.CourseListViewModel
import com.polylingy.app.study.StudyScreen
import com.polylingy.app.study.StudyViewModel
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
internal object CourseListRoute

@Serializable
internal data class StudyRoute(val courseId: String)

private val Indigo = lightColorScheme(
    primary = Color(0xFF4355B9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDEE0FF),
    secondaryContainer = Color(0xFFE1E0F9),
)

@Composable
fun App(container: AppContainer) {
    MaterialTheme(colorScheme = Indigo) {
        val nav = rememberNavController()
        NavHost(nav, startDestination = CourseListRoute) {
            composable<CourseListRoute> {
                CourseListScreen(
                    viewModel = viewModel { CourseListViewModel(container.studyService, container.courseService, container.storage.courses, container.clock) },
                    files = container.files,
                    onStart = { courseId -> nav.navigate(StudyRoute(courseId.toString())) },
                )
            }
            composable<StudyRoute> { entry ->
                val courseId = Uuid.parse(entry.toRoute<StudyRoute>().courseId)
                StudyScreen(
                    viewModel = viewModel { StudyViewModel(courseId, container.studyService, container.courseService, container.storage.courses, container.random) },
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}

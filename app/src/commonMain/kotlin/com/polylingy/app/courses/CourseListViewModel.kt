package com.polylingy.app.courses

import androidx.lifecycle.ViewModel
import com.polylingy.client.CourseFile
import com.polylingy.core.content.Course
import com.polylingy.core.content.CourseService
import com.polylingy.core.ports.Clock
import com.polylingy.core.ports.CourseRepository
import com.polylingy.core.study.CourseStats
import com.polylingy.core.study.StudyService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.uuid.Uuid

data class ExportedCourse(val fileName: String, val json: String)

class CourseListViewModel(
    private val study: StudyService,
    private val editor: CourseService,
    private val courses: CourseRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _courses = MutableStateFlow(study.courseStats())
    val courseStats: StateFlow<List<CourseStats>> = _courses.asStateFlow()

    fun refresh() {
        _courses.value = study.courseStats()
    }

    fun create(name: String) = edit { editor.create(name) }

    fun rename(courseId: Uuid, name: String) = edit { editor.rename(courseId, name) }

    fun delete(courseId: Uuid) = edit { editor.delete(courseId) }

    /** Adds a topic pasted as JSON. Returns an error message to show, or null on success. */
    fun addTopic(courseId: Uuid, json: String): String? = tryEdit { editor.addTopic(courseId, CourseFile.parseTopic(json)) }

    /** Imports a course file's contents. Returns an error message to show, or null on success. */
    fun import(json: String): String? = tryEdit {
        val now = clock.now()
        editor.import(CourseFile.parseCourse(json, now.instant, now.zone))
    }

    fun export(courseId: Uuid): ExportedCourse? =
        courses.get(courseId)?.let { ExportedCourse(fileName(it), CourseFile.export(it)) }

    private fun fileName(course: Course): String {
        val slug = course.name.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_')
        return "${slug.ifEmpty { "course" }}.json"
    }

    private fun edit(action: () -> Unit) {
        action()
        refresh()
    }

    private fun tryEdit(action: () -> Unit): String? = try {
        edit(action)
        null
    } catch (e: IllegalArgumentException) {
        e.message ?: "Invalid input"
    }
}

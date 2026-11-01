package com.polylingy.app.courses

import androidx.lifecycle.ViewModel
import com.polylingy.app.PickedFile
import com.polylingy.client.CourseExport
import com.polylingy.client.CourseLibrary
import com.polylingy.content.Problem
import com.polylingy.core.content.Course
import com.polylingy.core.content.CourseFormatException
import com.polylingy.core.content.CourseService
import com.polylingy.core.ports.CourseRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

/** What happened to a picked course file. */
sealed interface ImportOutcome {
    /** Imported; [warnings] is what to tell the user (newer format, failed downloads), often nothing. */
    data class Imported(val course: Course, val warnings: List<Problem>) : ImportOutcome

    data class Failed(val fileName: String, val problems: List<Problem>) : ImportOutcome
}

class CourseListViewModel(
    private val library: CourseLibrary,
    private val service: CourseService,
    private val repository: CourseRepository,
) : ViewModel() {
    private val _courses = MutableStateFlow(load())

    /** Oldest course first. */
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    fun import(file: PickedFile): ImportOutcome {
        log.info { "User imports ${file.name} (${file.bytes.size} bytes)" }
        return try {
            val (course, warnings) = library.import(file.name, file.bytes)
            ImportOutcome.Imported(course, warnings)
        } catch (e: CourseFormatException) {
            ImportOutcome.Failed(file.name, e.problems)
        } finally {
            refresh()
        }
    }

    /** The course as a file to save, or an error to show. */
    fun export(courseId: Uuid): Result<CourseExport> {
        log.info { "User exports course $courseId" }
        return runCatching { library.export(courseId) }.onFailure { log.warn(it) { "Export of course $courseId failed" } }
    }

    fun rename(courseId: Uuid, name: String) {
        log.info { "User renames course $courseId to \"$name\"" }
        service.rename(courseId, name)
        refresh()
    }

    fun delete(courseId: Uuid) {
        log.info { "User removes course $courseId" }
        library.delete(courseId)
        refresh()
    }

    fun refresh() {
        _courses.value = load()
    }

    private fun load() = repository.all().sortedBy { it.createdAt }
}

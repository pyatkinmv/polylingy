package com.polylingy.app.courses

import androidx.lifecycle.ViewModel
import com.polylingy.app.PickedFile
import com.polylingy.client.BrokenCourse
import com.polylingy.client.CourseExport
import com.polylingy.client.CourseLibrary
import com.polylingy.content.Problem
import com.polylingy.core.content.Course
import com.polylingy.core.content.CourseFormatException
import com.polylingy.core.content.CourseService
import com.polylingy.core.content.CourseUpdateException
import com.polylingy.core.ports.CourseRepository
import com.polylingy.core.study.ProgressCounts
import com.polylingy.core.study.StudyService
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

    /** A good file that can't update the course (another course, not newer); [reason] is meant to be shown as it is. */
    data class Refused(val fileName: String, val reason: String) : ImportOutcome
}

/** A course on the list with how it stands today. */
data class CourseRow(val course: Course, val counts: ProgressCounts)

class CourseListViewModel(
    private val library: CourseLibrary,
    private val service: CourseService,
    private val repository: CourseRepository,
    private val study: StudyService,
    private val brokenCourses: () -> List<BrokenCourse>,
    /** Days in a row with answers, for the header; 0 when it is not known. */
    private val streak: () -> Int = { 0 },
) : ViewModel() {
    private val _courses = MutableStateFlow(load())
    private val _broken = MutableStateFlow(brokenCourses())
    private val _streak = MutableStateFlow(streak())

    val streakDays: StateFlow<Int> = _streak.asStateFlow()

    /** Oldest course first. */
    val courses: StateFlow<List<CourseRow>> = _courses.asStateFlow()

    /** Stored courses this version can't read; shown so they can be looked at and removed. */
    val broken: StateFlow<List<BrokenCourse>> = _broken.asStateFlow()

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

    /** Updates the course with a newer file of the same course, keeping its progress; refusals carry the message to show. */
    fun update(courseId: Uuid, file: PickedFile): ImportOutcome {
        log.info { "User updates course $courseId from ${file.name} (${file.bytes.size} bytes)" }
        return try {
            val (course, warnings) = library.update(courseId, file.name, file.bytes)
            ImportOutcome.Imported(course, warnings)
        } catch (e: CourseFormatException) {
            ImportOutcome.Failed(file.name, e.problems)
        } catch (e: CourseUpdateException) {
            log.info { "Update of course $courseId refused: ${e.message}" }
            ImportOutcome.Refused(file.name, e.message ?: "The file can't update this course")
        } finally {
            refresh()
        }
    }

    /** The course as a file to save, or an error to show. */
    fun export(courseId: Uuid): Result<CourseExport> {
        log.info { "User exports course $courseId" }
        return runCatching { library.export(courseId) }.onFailure { log.warn(it) { "Export of course $courseId failed" } }
    }

    /** Saves a new name and description; a failure carries the message to show (e.g. bad markup). */
    fun editDetails(courseId: Uuid, name: String, description: String): Result<Course> {
        log.info { "User edits course $courseId: name \"$name\"" }
        return runCatching { service.editDetails(courseId, name, description) }
            .onFailure { log.warn { "Editing course $courseId failed: ${it.message}" } }
            .also { refresh() }
    }

    fun delete(courseId: Uuid) {
        log.info { "User removes course $courseId" }
        library.delete(courseId)
        refresh()
    }

    fun refresh() {
        _courses.value = load()
        _broken.value = brokenCourses()
        _streak.value = streak()
    }

    private fun load() = repository.all().sortedBy { it.createdAt }.map { course ->
        CourseRow(course, study.progress(course.id)?.counts ?: ProgressCounts(0, 0, 0, 0, 0, null, null))
    }
}

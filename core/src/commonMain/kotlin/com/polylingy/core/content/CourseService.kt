package com.polylingy.core.content

import com.polylingy.content.withId
import com.polylingy.core.ports.Clock
import com.polylingy.core.ports.CourseRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

/** Changes course documents. Every change is saved as a new [Course.revision]. */
class CourseService(private val courses: CourseRepository, private val clock: Clock) {
    /**
     * Adds an imported course under a name no other course has ("Name (2)"). If any of its ids is already used
     * by a stored course, the import becomes a copy with fresh ids, so a topic never belongs to two courses.
     */
    fun import(course: Course): Course {
        val used = courses.all().flatMapTo(mutableSetOf()) { it.ids() }
        val copy = course.ids().any { it in used }
        val incoming = if (copy) course.withFreshIds() else course
        val saved = save(incoming.copy(name = uniqueName(incoming.name, except = null), revision = 0))
        log.info { "Imported course ${saved.id} \"${saved.name}\"${if (copy) " as a copy with fresh ids" else ""}" }
        return saved
    }

    /** Renames a course; a name another course has gets a number, like on import. */
    fun rename(courseId: Uuid, name: String): Course {
        val saved = save(course(courseId).copy(name = uniqueName(name, except = courseId)))
        log.info { "Renamed course $courseId to \"${saved.name}\"" }
        return saved
    }

    fun delete(courseId: Uuid) {
        courses.delete(courseId)
        log.info { "Deleted course $courseId" }
    }

    private fun course(id: Uuid) = requireNotNull(courses.get(id)) { "No course $id" }

    private fun save(course: Course): Course =
        course.copy(revision = course.revision + 1, updatedAt = clock.now().instant).also(courses::save)

    private fun uniqueName(name: String, except: Uuid?): String {
        val base = validName(name)
        val taken = courses.all().filter { it.id != except }.map { it.name.lowercase() }.toSet()
        if (base.lowercase() !in taken) return base
        return generateSequence(2) { it + 1 }
            .map { n -> " ($n)".let { suffix -> base.take(MAX_NAME_LENGTH - suffix.length).trimEnd() + suffix } }
            .first { it.lowercase() !in taken }
    }

    companion object {
        const val MAX_NAME_LENGTH = 50

        /** Trimmed name; must be non-blank and at most [MAX_NAME_LENGTH] characters. */
        fun validName(name: String): String {
            val trimmed = name.trim()
            require(trimmed.isNotEmpty()) { "Course name can't be empty" }
            require(trimmed.length <= MAX_NAME_LENGTH) { "Course name is longer than $MAX_NAME_LENGTH characters" }
            return trimmed
        }
    }
}

/** Ids of the course, its units, topics and exercises. */
fun Course.ids(): List<Uuid> =
    listOf(id) + units.map { it.id } + allTopics.flatMap { topic -> listOf(topic.id) + topic.exercises.map { it.id } }

fun Course.withFreshIds(): Course {
    fun Topic.fresh() = copy(id = Uuid.random(), exercises = exercises.map { it.withId(Uuid.random()) })
    return copy(
        id = Uuid.random(),
        units = units.map { unit -> unit.copy(id = Uuid.random(), topics = unit.topics.map { it.fresh() }) },
        topics = topics.map { it.fresh() },
    )
}

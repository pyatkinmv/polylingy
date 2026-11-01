package com.polylingy.core.content

import com.polylingy.content.Block
import com.polylingy.content.Inline
import com.polylingy.content.Path
import com.polylingy.content.mapLinks
import com.polylingy.content.validate
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

    /**
     * Changes a course's name and description. A name another course has gets a number, like on import;
     * a blank description removes it. Bad markup in the description is an [IllegalArgumentException] with the problem.
     * Not for a course from a newer format: it is written back as it came.
     */
    fun editDetails(courseId: Uuid, name: String, description: String): Course {
        val course = course(courseId)
        require(!course.studyOnly) { "\"${course.name}\" was made with a newer version of Polylingy and can't be changed" }
        val text = description.trim().takeIf { it.isNotEmpty() }?.let(::Inline)
        text?.validate(Path("description"))?.firstOrNull()?.let { throw IllegalArgumentException(it.message) }
        val saved = save(course.copy(name = uniqueName(name, except = courseId), description = text))
        log.info { "Edited course $courseId: name \"${saved.name}\", description ${if (text == null) "none" else "${text.text.length} characters"}" }
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

/**
 * The same course under new ids of its own, its units, topics and exercises; links to units follow their new ids.
 * Section ids stay: they only need to be unique within the course.
 */
fun Course.withFreshIds(): Course {
    val newUnitIds = units.associate { it.id to Uuid.random() }
    val relink = { link: Block.Link -> link.copy(unit = link.unit?.let { newUnitIds[it] ?: it }) }
    fun Topic.fresh() = copy(
        id = Uuid.random(),
        explanation = explanation?.mapLinks(relink),
        exercises = exercises.map { it.withId(Uuid.random()).mapLinks(relink) },
    )
    return copy(
        id = Uuid.random(),
        units = units.map { unit ->
            unit.copy(id = newUnitIds.getValue(unit.id), theory = unit.theory?.mapLinks(relink), topics = unit.topics.map { it.fresh() })
        },
        topics = topics.map { it.fresh() },
    )
}

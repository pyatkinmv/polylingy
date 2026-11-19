package com.polylingy.core.content

import com.polylingy.content.Block
import com.polylingy.content.Inline
import com.polylingy.content.Path
import com.polylingy.content.mapLinks
import com.polylingy.content.validate
import com.polylingy.content.withId
import com.polylingy.core.ports.Clock
import com.polylingy.core.ports.CourseRepository
import com.polylingy.core.ports.CourseSettingsRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

/**
 * Changes course documents; every change made in the app is saved as the next [Course.version]. A course's settings live apart from its
 * document: import starts them from the file's rules, deleting the course deletes them.
 */
class CourseService(private val courses: CourseRepository, private val settings: CourseSettingsRepository, private val clock: Clock) {
    /**
     * Adds an imported course under a name no other course has ("Name (2)"). If any of its ids is already used
     * by a stored course, the import becomes a copy with fresh ids, so a topic never belongs to two courses.
     * The file's [Course.version] is kept as it is.
     */
    fun import(course: Course): Course {
        val used = courses.all().flatMapTo(mutableSetOf()) { it.ids() }
        val copy = course.ids().any { it in used }
        val incoming = if (copy) course.withFreshIds() else course
        val saved = save(incoming.copy(name = uniqueName(incoming.name, except = null)), bump = false)
        settings.save(saved.id, (saved.rules ?: RulesSpec()).resolved(), saved.updatedAt)
        log.info { "Imported course ${saved.id} \"${saved.name}\"${if (copy) " as a copy with fresh ids" else ""}" }
        return saved
    }

    /**
     * Throws [CourseUpdateException] unless [incoming] may replace the stored course [courseId]: it must be the same course
     * (same id), carry a higher [Course.version], and not use an id of any other course.
     */
    fun checkUpdate(courseId: Uuid, incoming: Course) {
        val stored = course(courseId)
        if (incoming.id != courseId) {
            throw CourseUpdateException("The file is another course: its id is ${incoming.id}, \"${stored.name}\" has $courseId")
        }
        if (incoming.version <= stored.version) {
            throw CourseUpdateException("The file's version is ${incoming.version}, \"${stored.name}\" is already at ${stored.version}; the file must be newer")
        }
        val others = courses.all().filter { it.id != courseId }.flatMapTo(mutableSetOf()) { it.ids() }
        incoming.ids().firstOrNull { it in others }?.let {
            throw CourseUpdateException("The file uses an id ($it) that belongs to another course")
        }
    }

    /**
     * Replaces the content of the stored course with the same id by the file's course, which must have a higher
     * [Course.version] ([checkUpdate]); the course then has the file's version. Progress stays: it hangs on the ids of topics and exercises, so what keeps its id
     * keeps its progress, what is new starts fresh, and answers to what the file no longer has stay in the journal unused.
     * The course's settings stay as they are; its name may change.
     */
    fun update(courseId: Uuid, incoming: Course): Course {
        checkUpdate(courseId, incoming)
        val stored = course(courseId)
        val saved = save(incoming.copy(name = uniqueName(incoming.name, except = courseId), createdAt = stored.createdAt), bump = false)
        log.info { "Updated course $courseId \"${saved.name}\" from version ${stored.version} to ${saved.version}: ${saved.allTopics.size} topics" }
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
        settings.delete(courseId)
        log.info { "Deleted course $courseId" }
    }

    /** The course as it goes into a file: its current settings as the file's rules. */
    fun forExport(course: Course): Course = course.copy(rules = settings.of(course))

    /** The JSON of a unit or topic for the editor, or null when the course has no such part. */
    fun partJson(courseId: Uuid, part: CoursePart): String? = CourseParts.json(course(courseId), part)

    /** Checks an edited unit or topic without saving it: the course as it would be, or the problems. */
    fun checkPart(courseId: Uuid, part: CoursePart, text: String): PartCheck {
        val course = course(courseId)
        if (course.studyOnly) return PartCheck(null, listOf(PartProblem(Path.ROOT, "this course was made with a newer version of Polylingy and can't be changed", null)))
        return CourseParts.check(course, part, text, clock.now().instant)
    }

    /** Saves an edited or new unit or topic as the next version; [PartException] with the problems if it isn't valid. */
    fun savePart(courseId: Uuid, part: CoursePart, text: String): Course {
        val check = checkPart(courseId, part, text)
        val edited = check.course ?: throw PartException(check.problems)
        val saved = save(edited)
        log.info { "Saved $part in course $courseId (version ${saved.version})" }
        return saved
    }

    /** Deletes a unit or topic; [PartException] if the rest of the course needs it, e.g. a unit's last topic. */
    fun deletePart(courseId: Uuid, part: CoursePart): Course {
        val course = course(courseId)
        if (course.studyOnly) throw PartException(listOf(PartProblem(Path.ROOT, "this course was made with a newer version of Polylingy and can't be changed", null)))
        val check = CourseParts.remove(course, part, clock.now().instant)
        val edited = check.course ?: throw PartException(check.problems)
        val saved = save(edited)
        log.info { "Deleted $part from course $courseId (version ${saved.version})" }
        return saved
    }

    private fun course(id: Uuid) = requireNotNull(courses.get(id)) { "No course $id" }

    /** Saves the document; a change made in the app is the next version, an import or an update keeps the version of its file. */
    private fun save(course: Course, bump: Boolean = true): Course =
        course.copy(version = if (bump) course.version + 1 else course.version, updatedAt = clock.now().instant).also(courses::save)

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

/** A file can't update the course; the message says why and is meant to be shown. */
class CourseUpdateException(message: String) : IllegalArgumentException(message)

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

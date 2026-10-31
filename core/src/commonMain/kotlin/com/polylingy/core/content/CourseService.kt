package com.polylingy.core.content

import com.polylingy.core.ports.Clock
import com.polylingy.core.ports.CourseRepository
import kotlin.uuid.Uuid

/** Edits course documents. Every change is saved as a new [Course.revision]. */
class CourseService(private val courses: CourseRepository, private val clock: Clock) {
    fun create(name: String): Course {
        val now = clock.now().instant
        return save(Course(Uuid.random(), validName(name), createdAt = now, revision = 0, updatedAt = now, topics = emptyList()))
    }

    fun rename(courseId: Uuid, name: String): Course = save(course(courseId).copy(name = validName(name)))

    fun addTopic(courseId: Uuid, topic: Topic): Course {
        require(topic.exercises.isNotEmpty()) { "A topic needs at least one exercise" }
        val course = course(courseId)
        require(course.topics.none { it.id == topic.id }) { "Topic ${topic.id} is already in the course" }
        return save(course.copy(topics = course.topics + topic))
    }

    fun deleteTopic(courseId: Uuid, topicId: Uuid): Course {
        val course = course(courseId)
        return save(course.copy(topics = course.topics.filterNot { it.id == topicId }))
    }

    fun delete(courseId: Uuid) = courses.delete(courseId)

    /**
     * Adds an imported course. If a course with the same id already exists, the import
     * becomes a copy with fresh ids, so topics never belong to two courses at once.
     */
    fun import(course: Course): Course {
        val incoming = if (courses.get(course.id) != null) course.withFreshIds() else course
        return save(incoming.copy(name = validName(incoming.name), revision = 0))
    }

    private fun course(id: Uuid) = requireNotNull(courses.get(id)) { "No course $id" }

    private fun save(course: Course): Course =
        course.copy(revision = course.revision + 1, updatedAt = clock.now().instant).also(courses::save)

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

fun Course.withFreshIds(): Course = copy(
    id = Uuid.random(),
    topics = topics.map { topic ->
        topic.copy(id = Uuid.random(), exercises = topic.exercises.map { it.copy(id = Uuid.random()) })
    },
)

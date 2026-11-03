package com.polylingy.core.ports

import com.polylingy.core.content.Course
import com.polylingy.core.content.RulesSpec
import com.polylingy.core.journal.EventEnvelope
import com.polylingy.core.journal.ZonedInstant
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Storage for course documents. Implemented outside the core (SQLite on clients). */
interface CourseRepository {
    fun all(): List<Course>
    fun get(id: Uuid): Course?

    /** Stores [course], replacing any document with the same id. */
    fun save(course: Course)
    fun delete(id: Uuid)
}

/**
 * Storage for each course's settings: its policies as the learner set them, every part written out. Kept apart from
 * the course document (docs/architecture.md, section 5); a course stored before settings existed has none.
 */
interface CourseSettingsRepository {
    fun get(courseId: Uuid): RulesSpec?

    /** Stores [rules] for the course, replacing what it had; [updatedAt] is for sync. */
    fun save(courseId: Uuid, rules: RulesSpec, updatedAt: Instant)
    fun delete(courseId: Uuid)
}

/** Append-only storage for the learning journal. */
interface EventLog {
    fun append(event: EventEnvelope)
    fun all(): List<EventEnvelope>
}

/** Current moment in the device's time zone. */
fun interface Clock {
    fun now(): ZonedInstant
}

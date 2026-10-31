package com.polylingy.core.ports

import com.polylingy.core.content.Course
import com.polylingy.core.journal.EventEnvelope
import com.polylingy.core.journal.ZonedInstant
import kotlin.uuid.Uuid

/** Storage for course documents. Implemented outside the core (SQLite on clients). */
interface CourseRepository {
    fun all(): List<Course>
    fun get(id: Uuid): Course?

    /** Stores [course], replacing any document with the same id. */
    fun save(course: Course)
    fun delete(id: Uuid)
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

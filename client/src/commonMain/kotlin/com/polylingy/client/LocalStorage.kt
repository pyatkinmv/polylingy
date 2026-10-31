package com.polylingy.client

import app.cash.sqldelight.db.SqlDriver
import com.polylingy.client.db.PolylingyDb
import com.polylingy.core.PolylingyJson
import com.polylingy.core.content.Course
import com.polylingy.core.journal.EventEnvelope
import com.polylingy.core.ports.CourseRepository
import com.polylingy.core.ports.EventLog
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.uuid.Uuid

/** Everything a client keeps on disk: course documents, the journal and installation settings. */
class LocalStorage(private val driver: SqlDriver) : AutoCloseable {
    private val db = PolylingyDb(driver)

    val courses: CourseRepository = SqlCourseRepository(db)
    val journal: EventLog = SqlEventLog(db)

    /** Random id generated on first use and kept for this installation. */
    val deviceId: Uuid by lazy {
        db.settingQueries.selectValue(DEVICE_ID).executeAsOneOrNull()?.let(Uuid::parse)
            ?: Uuid.random().also { db.settingQueries.upsert(DEVICE_ID, it.toString()) }
    }

    override fun close() = driver.close()

    private companion object {
        const val DEVICE_ID = "device_id"
    }
}

internal class SqlCourseRepository(private val db: PolylingyDb) : CourseRepository {
    override fun all(): List<Course> = db.courseDocumentQueries.selectAll().executeAsList().map(::decode)

    override fun get(id: Uuid): Course? =
        db.courseDocumentQueries.selectById(id.toString()).executeAsOneOrNull()?.let(::decode)

    override fun save(course: Course) {
        val body = PolylingyJson.encodeToString(Course.serializer(), course)
        db.courseDocumentQueries.upsert(course.id.toString(), course.revision.toLong(), course.updatedAt.toString(), body)
    }

    override fun delete(id: Uuid) {
        db.courseDocumentQueries.deleteById(id.toString())
    }

    private fun decode(body: String) = PolylingyJson.decodeFromString(Course.serializer(), body)
}

internal class SqlEventLog(private val db: PolylingyDb) : EventLog {
    override fun append(event: EventEnvelope) {
        val json = PolylingyJson.encodeToJsonElement(EventEnvelope.serializer(), event).jsonObject
        db.journalEventQueries.insert(
            event_id = event.eventId.toString(),
            occurred_at = event.occurredAt.toString(),
            schema_version = event.schemaVersion.toLong(),
            type = json.getValue("event").jsonObject.getValue("type").jsonPrimitive.content,
            body = json.toString(),
        )
    }

    override fun all(): List<EventEnvelope> = db.journalEventQueries.selectAll().executeAsList()
        .map { PolylingyJson.decodeFromString(EventEnvelope.serializer(), it) }
}

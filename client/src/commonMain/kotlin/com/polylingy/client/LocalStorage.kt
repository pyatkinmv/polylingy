package com.polylingy.client

import app.cash.sqldelight.db.SqlDriver
import com.polylingy.client.db.PolylingyDb
import com.polylingy.content.ContentFormatException
import com.polylingy.content.ContentJson
import com.polylingy.content.Problem
import com.polylingy.core.PolylingyJson
import com.polylingy.core.content.Course
import com.polylingy.core.content.CourseFormatException
import com.polylingy.core.content.CourseJson
import com.polylingy.core.content.RulesSpec
import com.polylingy.core.journal.EventEnvelope
import com.polylingy.core.ports.CourseRepository
import com.polylingy.core.ports.CourseSettingsRepository
import com.polylingy.core.ports.EventLog
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

/** Everything a client keeps on disk: course documents, their settings, the journal and installation settings. */
class LocalStorage(private val driver: SqlDriver) : AutoCloseable {
    private val db = PolylingyDb(driver)

    private val courseRepository = SqlCourseRepository(db)
    val courses: CourseRepository = courseRepository
    val courseSettings: CourseSettingsRepository = SqlCourseSettings(db)
    val journal: EventLog = SqlEventLog(db)

    /** Stored courses this version can't read; [courses] leaves them out. Removing one goes through [courses] as usual. */
    fun brokenCourses(): List<BrokenCourse> = courseRepository.broken()

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

/**
 * A stored course that no longer reads, e.g. after its format changed during development. [name] is taken from the
 * document if it has one; [problems] say what is wrong, with paths.
 */
data class BrokenCourse(val id: Uuid, val name: String?, val problems: List<Problem>)

internal class SqlCourseRepository(private val db: PolylingyDb) : CourseRepository {
    /** Every course that reads; the others are logged and left out, see [broken]. */
    override fun all(): List<Course> = db.courseDocumentQueries.selectAll().executeAsList().mapNotNull { row ->
        try {
            decode(row.body)
        } catch (e: CourseFormatException) {
            log.error { "Stored course ${row.id} can't be read: ${e.problems.joinToString("; ")}" }
            null
        }
    }

    fun broken(): List<BrokenCourse> = db.courseDocumentQueries.selectAll().executeAsList().mapNotNull { row ->
        try {
            decode(row.body)
            null
        } catch (e: CourseFormatException) {
            val name = runCatching { Json.parseToJsonElement(row.body).jsonObject["name"]?.jsonPrimitive?.content }.getOrNull()
            BrokenCourse(Uuid.parse(row.id), name, e.problems)
        }
    }

    override fun get(id: Uuid): Course? =
        db.courseDocumentQueries.selectById(id.toString()).executeAsOneOrNull()?.let(::decode)

    override fun save(course: Course) {
        val body = CourseJson.write(course)
        db.courseDocumentQueries.upsert(course.id.toString(), course.revision.toLong(), course.updatedAt.toString(), body)
    }

    override fun delete(id: Uuid) {
        db.courseDocumentQueries.deleteById(id.toString())
    }

    private fun decode(body: String) = CourseJson.readStored(body)
}

internal class SqlCourseSettings(private val db: PolylingyDb) : CourseSettingsRepository {
    /** The stored settings; ones this version can't read are logged and treated as none, so the course's rules apply. */
    override fun get(courseId: Uuid): RulesSpec? {
        val body = db.courseSettingsQueries.selectBody(courseId.toString()).executeAsOneOrNull() ?: return null
        return try {
            ContentJson.decode(RulesSpec.serializer(), body)
        } catch (e: ContentFormatException) {
            log.error { "Settings of course $courseId can't be read (${e.problem}); using the course's own rules" }
            null
        }
    }

    override fun save(courseId: Uuid, rules: RulesSpec, updatedAt: Instant) {
        db.courseSettingsQueries.upsert(courseId.toString(), updatedAt.toString(), ContentJson.strict.encodeToString(RulesSpec.serializer(), rules))
    }

    override fun delete(courseId: Uuid) {
        db.courseSettingsQueries.deleteById(courseId.toString())
    }
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

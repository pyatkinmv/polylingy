package com.polylingy.client

import com.polylingy.core.content.Course
import com.polylingy.core.content.Exercise
import com.polylingy.core.content.Topic
import com.polylingy.core.journal.AnswerSubmitted
import com.polylingy.core.journal.AutoCheck
import com.polylingy.core.journal.EventEnvelope
import com.polylingy.core.journal.Rating
import com.polylingy.core.journal.ZonedInstant
import kotlinx.datetime.TimeZone
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val moscow = TimeZone.of("Europe/Moscow")

private fun course(name: String = "c", createdAt: Instant = Instant.fromEpochSeconds(0)) = Course(
    id = Uuid.random(),
    name = name,
    createdAt = createdAt,
    revision = 1,
    updatedAt = createdAt,
    topics = listOf(Topic(Uuid.random(), "t", exercises = listOf(Exercise(Uuid.random(), "{0}", mapOf("0" to "x"))))),
)

private fun event(at: Long = 0) = EventEnvelope(
    eventId = Uuid.random(),
    deviceId = Uuid.random(),
    occurredAt = ZonedInstant(Instant.fromEpochSeconds(at), moscow),
    event = AnswerSubmitted(Uuid.random(), Uuid.random(), Uuid.random(), "x", AutoCheck.CORRECT, Rating.CORRECT),
)

class LocalStorageTest {
    private val storage = DesktopStorage.inMemory()
    private val tempDir = Files.createTempDirectory("polylingy-test")

    @AfterTest fun cleanUp() {
        storage.close()
        tempDir.toFile().deleteRecursively()
    }

    // ── course documents ────────────────────────────────────────────────────

    @Test fun savedCourseReadsBackEqual() {
        val c = course()
        storage.courses.save(c)
        assertEquals(c, storage.courses.get(c.id))
        assertEquals(listOf(c), storage.courses.all())
    }

    @Test fun savingTheSameIdReplacesTheDocument() {
        val c = course("old")
        storage.courses.save(c)
        storage.courses.save(c.copy(name = "new", revision = 2))
        assertEquals("new", storage.courses.get(c.id)!!.name)
        assertEquals(1, storage.courses.all().size)
    }

    @Test fun deletedCourseIsGone() {
        val c = course()
        storage.courses.save(c)
        storage.courses.delete(c.id)
        assertNull(storage.courses.get(c.id))
    }

    // ── journal ─────────────────────────────────────────────────────────────

    @Test fun eventsReadBackInAppendOrder() {
        val events = listOf(event(30), event(10), event(20))
        events.forEach(storage.journal::append)
        assertEquals(events, storage.journal.all())
    }

    @Test fun appendingTheSameEventTwiceKeepsOneCopy() {
        val e = event()
        storage.journal.append(e)
        storage.journal.append(e)
        assertEquals(listOf(e), storage.journal.all())
    }

    // ── installation ────────────────────────────────────────────────────────

    @Test fun deviceIdIsStable() = assertEquals(storage.deviceId, storage.deviceId)

    @Test fun dataAndDeviceIdSurviveReopeningTheFile() {
        val file = tempDir.resolve("nested/polylingy.db")
        val c = course()
        val e = event()
        val deviceId = DesktopStorage.open(file).use { first ->
            first.courses.save(c)
            first.journal.append(e)
            first.deviceId
        }
        DesktopStorage.open(file).use { second ->
            assertEquals(listOf(c), second.courses.all())
            assertEquals(listOf(e), second.journal.all())
            assertEquals(deviceId, second.deviceId)
        }
    }
}

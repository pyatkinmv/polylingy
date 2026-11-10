package com.polylingy.client

import com.polylingy.content.Exercise
import com.polylingy.content.Inline
import com.polylingy.content.ItemResult
import com.polylingy.content.Response
import com.polylingy.core.content.Course
import com.polylingy.core.content.CourseJson
import com.polylingy.core.content.PickerSpec
import com.polylingy.core.content.Preset
import com.polylingy.core.content.RulesSpec
import com.polylingy.core.content.Topic
import com.polylingy.core.journal.AnswerSubmitted
import com.polylingy.core.journal.EventEnvelope
import com.polylingy.core.journal.ZonedInstant
import kotlinx.datetime.TimeZone
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val moscow = TimeZone.of("Europe/Moscow")

internal fun course(name: String = "c", createdAt: Instant = Instant.fromEpochSeconds(0)) = Course(
    id = Uuid.random(),
    name = name,
    createdAt = createdAt,
    revision = 1,
    updatedAt = createdAt,
    topics = listOf(Topic(Uuid.random(), "t", exercises = listOf(Exercise.Gaps(Uuid.random(), items = listOf(Exercise.GapItem(Inline("{{x}}"))))))),
)

private fun event(at: Long = 0) = EventEnvelope(
    eventId = Uuid.random(),
    deviceId = Uuid.random(),
    occurredAt = ZonedInstant(Instant.fromEpochSeconds(at), moscow),
    event = AnswerSubmitted(Uuid.random(), Uuid.random(), Uuid.random(), 1, Response.Blanks(listOf("x")), listOf(ItemResult.CORRECT)),
)

class LocalStorageTest {
    private val storage = DesktopStorage.inMemory()
    private val tempDir = Files.createTempDirectory("polylingy-test")

    @AfterTest fun cleanUp() {
        storage.close()
        tempDir.toFile().deleteRecursively()
    }

    // course documents

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

    @Test fun aStudyOnlyCourseIsStoredAsItCame() {
        val text = """{ "formatVersion": 8, "name": "Future", "level": "C1",
            "topics": [ { "title": "T", "exercises": [ { "type": "dictation", "audio": "a.mp3" } ] } ] }"""
        val c = CourseJson.read(text, Instant.fromEpochSeconds(0)).course
        storage.courses.save(c)
        val back = storage.courses.get(c.id)!!
        assertTrue(back.studyOnly)
        assertEquals(c.ids(), back.ids())
        assertTrue("dictation" in back.sourceJson!!)
    }

    private fun Course.ids() = listOf(id) + topics.map { it.id } + topics.flatMap { t -> t.exercises.map { it.id } }

    // course settings

    @Test fun settingsAreStoredPerCourseAndDeleted() {
        val id = Uuid.random()
        assertNull(storage.courseSettings.get(id))
        val rules = RulesSpec(Preset.SM2).resolved()
        storage.courseSettings.save(id, rules, Instant.fromEpochSeconds(0))
        assertEquals(rules, storage.courseSettings.get(id))
        val changed = rules.copy(picker = PickerSpec.Shuffle(newPerDay = 3))
        storage.courseSettings.save(id, changed, Instant.fromEpochSeconds(1))
        assertEquals(changed, storage.courseSettings.get(id))
        storage.courseSettings.delete(id)
        assertNull(storage.courseSettings.get(id))
    }

    @Test fun settingsThatNoLongerReadCountAsNone() {
        val id = Uuid.random()
        DesktopStorage.open(tempDir).use { it.courseSettings.save(id, RulesSpec(), Instant.fromEpochSeconds(0)) }
        java.sql.DriverManager.getConnection("jdbc:sqlite:${tempDir.resolve("polylingy.db")}").use { connection ->
            connection.createStatement().use { it.executeUpdate("UPDATE course_settings SET body = '{ \"preset\": \"turbo\" }'") }
        }
        DesktopStorage.open(tempDir).use { assertNull(it.courseSettings.get(id), "the course's own rules apply") }
    }

    @Test fun aDatabaseFromBeforeSettingsGetsTheirTable() {
        val c = course("Kept")
        DesktopStorage.open(tempDir).use { it.courses.save(c) }
        java.sql.DriverManager.getConnection("jdbc:sqlite:${tempDir.resolve("polylingy.db")}").use { connection ->
            connection.createStatement().use {
                it.executeUpdate("DROP TABLE course_settings")
                it.executeUpdate("PRAGMA user_version = 1")
            }
        }
        DesktopStorage.open(tempDir).use { reopened ->
            assertEquals(listOf(c), reopened.courses.all())
            assertNull(reopened.courseSettings.get(c.id))
            reopened.courseSettings.save(c.id, RulesSpec(), Instant.fromEpochSeconds(0))
            assertEquals(RulesSpec(), reopened.courseSettings.get(c.id))
        }
    }

    // journal

    @Test fun aCourseThatNoLongerReadsIsLeftOutAndListedAsBroken() {
        val good = course("Good")
        val broken = course("Old format")
        DesktopStorage.open(tempDir).use {
            it.courses.save(good)
            it.courses.save(broken)
        }
        java.sql.DriverManager.getConnection("jdbc:sqlite:${tempDir.resolve("polylingy.db")}").use { connection ->
            connection.prepareStatement("UPDATE course_document SET body = replace(body, '\"topics\"', '\"topix\"') WHERE id = ?").use {
                it.setString(1, broken.id.toString())
                it.executeUpdate()
            }
        }
        DesktopStorage.open(tempDir).use { reopened ->
            assertEquals(listOf(good), reopened.courses.all())
            val listed = reopened.brokenCourses().single()
            assertEquals(broken.id to "Old format", listed.id to listed.name)
            assertTrue(listed.problems.isNotEmpty())
            reopened.courses.delete(broken.id)
            assertEquals(emptyList(), reopened.brokenCourses())
        }
    }

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

    // installation

    @Test fun dataLivesInDocumentsPolylingy() = assertEquals("polylingy", DesktopStorage.defaultDataDir().fileName.toString())

    @Test fun deviceIdIsStable() = assertEquals(storage.deviceId, storage.deviceId)

    @Test fun dataAndDeviceIdSurviveReopeningTheFile() {
        val dir = tempDir.resolve("nested")
        val c = course()
        val e = event()
        val deviceId = DesktopStorage.open(dir).use { first ->
            first.courses.save(c)
            first.journal.append(e)
            first.deviceId
        }
        DesktopStorage.open(dir).use { second ->
            assertEquals(listOf(c), second.courses.all())
            assertEquals(listOf(e), second.journal.all())
            assertEquals(deviceId, second.deviceId)
        }
    }
}

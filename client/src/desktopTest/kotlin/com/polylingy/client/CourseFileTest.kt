package com.polylingy.client

import com.polylingy.core.content.CourseService
import com.polylingy.core.content.ExplanationFormat
import com.polylingy.core.journal.ZonedInstant
import com.polylingy.core.ports.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

class CourseFileTest {
    private val zone = TimeZone.of("Europe/Moscow")
    private val now = Instant.parse("2026-10-31T12:00:00Z")

    private fun parse(json: String) = CourseFile.parseCourse(json, now, zone)

    private fun topicJson(extra: String = "", exercise: String = """{"task": "t", "answer": "a"}""") =
        """{"subject": "s" $extra, "exercises": [$exercise]}"""

    private fun courseJson(vararg topics: String, extra: String = "") =
        """{"name": "Spanish" $extra, "topics": [${topics.joinToString()}]}"""

    // ── Flutter-era format ──────────────────────────────────────────────────

    @Test fun stringAnswerBecomesBlankZero() {
        val exercise = parse(courseJson(topicJson())).topics.single().exercises.single()
        assertEquals(mapOf("0" to "a"), exercise.answers)
    }

    @Test fun objectAnswerKeepsItsBlanks() {
        val exercise = parse(courseJson(topicJson(exercise = """{"task": "{0} {1}", "answer": {"0": "rojo", "1": "roja"}}""")))
            .topics.single().exercises.single()
        assertEquals(mapOf("0" to "rojo", "1" to "roja"), exercise.answers)
    }

    @Test fun explanationsAndHtmlFormatAreRead() {
        val topic = parse(courseJson(topicJson(
            extra = """, "generalExplanation": "<b>hi</b>", "generalExplanationFormat": "HTML"""",
            exercise = """{"task": "t", "answer": "a", "exampleExplanation": "because"}""",
        ))).topics.single()
        assertEquals(ExplanationFormat.HTML, topic.explanation!!.format)
        assertEquals("because", topic.exercises.single().explanation)
    }

    @Test fun emptyExplanationsBecomeNull() {
        val topic = parse(courseJson(topicJson(
            extra = """, "generalExplanation": """"",
            exercise = """{"task": "t", "answer": "a", "exampleExplanation": ""}""",
        ))).topics.single()
        assertNull(topic.explanation)
        assertNull(topic.exercises.single().explanation)
    }

    @Test fun flutterLocalCreatedAtIsReadInTheGivenZone() {
        val course = parse(courseJson(extra = """, "createdAt": "2026-10-31T02:02:24.255718""""))
        assertEquals(LocalDateTime.parse("2026-10-31T02:02:24.255718").toInstant(zone), course.createdAt)
    }

    @Test fun missingCreatedAtDefaultsToNow() = assertEquals(now, parse(courseJson()).createdAt)

    @Test fun missingNameGetsTheOldDefault() = assertEquals("Unnamed Course", parse("""{"topics": []}""").name)

    // ── ids (successor of the Flutter ensureTopicIds tests) ────────────────

    @Test fun missingIdsGetFreshUuids() {
        val course = parse(courseJson(topicJson(), topicJson()))
        val ids = listOf(course.id) + course.topics.map { it.id } + course.topics.flatMap { t -> t.exercises.map { it.id } }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun uuidIdsFromTheFileAreKept() {
        val id = Uuid.random()
        assertEquals(id, parse(courseJson(topicJson(extra = """, "id": "$id""""))).topics.single().id)
    }

    @Test fun flutterStyleIdsAreReplaced() {
        // The Flutter version used "0", "1", … and 8-hex ids; they aren't globally unique.
        val topics = parse(courseJson(topicJson(extra = """, "id": "0""""), topicJson(extra = """, "id": "a1b2c3d4""""))).topics
        assertEquals(2, topics.map { it.id }.toSet().size)
    }

    @Test fun duplicateIdsInOneFileAreReplaced() {
        val id = Uuid.random()
        val topics = parse(courseJson(topicJson(extra = """, "id": "$id""""), topicJson(extra = """, "id": "$id""""))).topics
        assertEquals(id, topics[0].id)
        assertNotEquals(id, topics[1].id)
    }

    // ── validation ──────────────────────────────────────────────────────────

    private fun error(json: String) = assertFailsWith<CourseFormatException> { parse(json) }.message!!

    @Test fun invalidJsonIsReported() = assertContains(error("{not json"), "Not valid JSON")

    @Test fun nonObjectRootIsReported() = assertContains(error("[]"), "course: expected a JSON object")

    @Test fun topicWithoutExercisesIsReportedWithItsPath() {
        assertContains(error(courseJson(topicJson(), """{"subject": "empty", "exercises": []}""")), "course.topics[1].exercises")
    }

    @Test fun missingAnswerIsReported() {
        assertContains(error(courseJson(topicJson(exercise = """{"task": "t"}"""))), "course.topics[0].exercises[0].answer: missing")
    }

    @Test fun wrongTypesAreReported() {
        assertContains(error("""{"name": 5}"""), "course.name: expected a string")
        assertContains(error("""{"topics": {}}"""), "course.topics: expected a list")
        assertContains(error(courseJson(topicJson(exercise = """{"task": "t", "answer": ["a"]}"""))), "answer: expected a string or an object")
    }

    @Test fun singleTopicParsesForTheAddTopicDialog() {
        assertEquals("s", CourseFile.parseTopic(topicJson()).subject)
        assertFailsWith<CourseFormatException> { CourseFile.parseTopic("""{"subject": "s"}""") }
    }

    // ── export ──────────────────────────────────────────────────────────────

    @Test fun exportThenImportIsLossless() {
        val original = parse(courseJson(
            topicJson(extra = """, "generalExplanation": "<i>x</i>", "generalExplanationFormat": "HTML""""),
            topicJson(exercise = """{"task": "{0} {1}", "answer": {"0": "a", "1": "b"}, "exampleExplanation": "e"}"""),
            extra = """, "createdAt": "2026-01-01T00:00:00Z"""",
        ))
        assertEquals(original, parse(CourseFile.export(original)))
    }

    // ── sample course ───────────────────────────────────────────────────────

    private val clock = Clock { ZonedInstant(now, zone) }

    @Test fun bundledSampleCourseParses() {
        val sample = CourseFile.parseCourse(DesktopStorage.sampleCourseJson(), now, zone)
        assertEquals("Spanish Basics", sample.name)
        assertEquals(listOf(3, 5, 3, 3), sample.topics.map { it.exercises.size })
    }

    @Test fun sampleCourseIsSeededOnlyIntoAnEmptyStorage() = DesktopStorage.inMemory().use { storage ->
        seedSampleCourse(storage, clock, DesktopStorage.sampleCourseJson())
        seedSampleCourse(storage, clock, DesktopStorage.sampleCourseJson())
        assertEquals(1, storage.courses.all().size)
        assertEquals(1, storage.courses.all().single().revision)
    }

    @Test fun noSampleWhenTheUserAlreadyHasCourses() = DesktopStorage.inMemory().use { storage ->
        CourseService(storage.courses, clock).create("Mine")
        seedSampleCourse(storage, clock, DesktopStorage.sampleCourseJson())
        assertEquals(listOf("Mine"), storage.courses.all().map { it.name })
    }
}

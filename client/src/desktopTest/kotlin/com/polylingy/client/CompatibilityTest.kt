package com.polylingy.client

import com.polylingy.content.solvedResponse
import com.polylingy.core.content.CourseService
import com.polylingy.core.journal.AnswerSubmitted
import com.polylingy.core.journal.EventEnvelope
import com.polylingy.core.journal.ZonedInstant
import com.polylingy.core.ports.Clock
import com.polylingy.core.rules.Checking
import com.polylingy.core.study.StudyService
import kotlinx.datetime.TimeZone
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * From the first release on, every later version of the app must open the database an earlier one wrote and lose nothing
 * (docs/architecture.md, "Compatibility"). `src/desktopTest/resources/compat/` keeps such databases, one per released schema,
 * with what they hold in `<name>.expected`; the tests open each with the current code, as the app does at start-up.
 *
 * A new baseline is made from the code of the release it stands for — never by editing an old file:
 * `./gradlew :client:desktopTest --tests "*CompatibilityTest*" -Dpolylingy.makeBaseline=<name>` writes
 * `build/compat-new/<name>.db` and `<name>.expected` to copy into `resources/compat/` and to list in [BASELINES].
 */
class CompatibilityTest {
    private val dir = Files.createTempDirectory("polylingy-compat")

    @AfterTest fun cleanUp() {
        dir.toFile().deleteRecursively()
    }

    private val clock = Clock { ZonedInstant(Instant.parse("2026-11-20T10:00:00Z"), TimeZone.UTC) }

    private fun resource(name: String) = requireNotNull(CompatibilityTest::class.java.getResourceAsStream("/compat/$name")) { "compat/$name is missing" }

    private fun expected(name: String): Map<String, String> =
        resource("$name.expected").bufferedReader().readLines().filter { it.contains('=') }.associate { it.substringBefore('=') to it.substringAfter('=') }

    /** The database of a released version, copied so the test can let the app migrate it. */
    private fun open(name: String): LocalStorage {
        resource("$name.db").use { Files.copy(it, dir.resolve("polylingy.db"), StandardCopyOption.REPLACE_EXISTING) }
        return DesktopStorage.open(dir)
    }

    @Test fun everyReleasedDatabaseStillOpensAndHoldsEverything() {
        for (name in BASELINES) {
            val want = expected(name)
            open(name).use { storage ->
                val courses = storage.courses.all()
                assertEquals(want.getValue("courses").toInt(), courses.size, "$name: courses")
                assertEquals(emptyList(), storage.brokenCourses(), "$name: every course reads")
                val course = courses.single()
                assertEquals(want.getValue("courseName"), course.name, name)
                assertEquals(want.getValue("topics").toInt(), course.allTopics.size, "$name: topics")
                assertEquals(want.getValue("exercises").toInt(), course.allTopics.sumOf { it.exercises.size }, "$name: exercises")
                assertEquals(Uuid.parse(want.getValue("courseId")), course.id, name)
                assertNotNull(storage.courseSettings.get(course.id), "$name: the course's settings")
                assertEquals(Uuid.parse(want.getValue("deviceId")), storage.deviceId, "$name: the installation's id")

                val events = storage.journal.all()
                assertEquals(want.getValue("events").toInt(), events.size, "$name: journal events")
                assertEquals(want.getValue("topicsAnswered").toInt(), events.map { (it.event as AnswerSubmitted).topicId }.toSet().size, "$name: answered topics")

                // the progress is derived from the journal with today's rules, so only that it can be derived is promised
                val study = StudyService(storage.courses, storage.courseSettings, storage.journal, clock, storage.deviceId)
                val progress = assertNotNull(study.progress(course.id), "$name: progress")
                assertTrue(progress.counts.learned + progress.counts.learning + progress.counts.new >= 0)
                assertNotNull(study.next(course.id), "$name: something to study next")
            }
        }
    }

    @Test fun answeringAfterTheMigrationAddsToTheOldJournal() {
        for (name in BASELINES) {
            open(name).use { storage ->
                val course = storage.courses.all().single()
                val study = StudyService(storage.courses, storage.courseSettings, storage.journal, clock, storage.deviceId)
                val before = storage.journal.all().size
                val next = study.next(course.id)!!
                study.answer(course.id, next.topic.id, next.exercise.id, next.exercise.solvedResponse())
                assertEquals(before + 1, storage.journal.all().size, name)
            }
        }
    }

    /** Writes a baseline for the current schema; see the class comment. Does nothing unless asked. */
    @Test fun makeABaselineWhenAsked() {
        val name = System.getProperty("polylingy.makeBaseline")?.takeIf { it.isNotBlank() } ?: return
        val out = Path.of("build", "compat-new").also { Files.createDirectories(it) }
        val work = Files.createTempDirectory("polylingy-baseline")
        try {
            val storage = DesktopStorage.open(work)
            val library = CourseLibrary(storage.courses, CourseService(storage.courses, storage.courseSettings, clock), FileImageStore(work.resolve("images")), { error("no network") }, CourseArchive, clock)
            val course = library.import(sampleCourse()).course
            val topics = course.allTopics.take(4)
            var n = 0
            for (topic in topics) for (exercise in topic.exercises.take(3)) {
                val results = Checking.check(exercise, exercise.solvedResponse()).map { it.result }
                storage.journal.append(
                    EventEnvelope(
                        eventId = Uuid.random(), deviceId = storage.deviceId,
                        occurredAt = ZonedInstant(Instant.parse("2026-11-10T10:00:00Z").plus(kotlin.time.Duration.parse("${n++}m")), TimeZone.UTC),
                        event = AnswerSubmitted(course.id, topic.id, exercise.id, course.version, exercise.solvedResponse(), results),
                    ),
                )
            }
            val events = storage.journal.all()
            val facts = listOf(
                "courses=1", "courseName=${course.name}", "courseId=${course.id}", "topics=${course.allTopics.size}",
                "exercises=${course.allTopics.sumOf { it.exercises.size }}", "deviceId=${storage.deviceId}", "events=${events.size}",
                "topicsAnswered=${events.map { (it.event as AnswerSubmitted).topicId }.toSet().size}",
            )
            storage.close()
            Files.copy(work.resolve("polylingy.db"), out.resolve("$name.db"), StandardCopyOption.REPLACE_EXISTING)
            Files.writeString(out.resolve("$name.expected"), facts.joinToString("\n", postfix = "\n"))
            println("BASELINE written to ${out.toAbsolutePath()}")
        } finally {
            work.toFile().deleteRecursively()
        }
    }

    private companion object {
        /** One entry per released schema, oldest first; the file name without extension. */
        val BASELINES = listOf("schema-2")
    }
}

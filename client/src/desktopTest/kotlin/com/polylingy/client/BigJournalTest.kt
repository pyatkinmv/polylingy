package com.polylingy.client

import com.polylingy.content.ItemResult
import com.polylingy.content.solvedResponse
import com.polylingy.core.content.Course
import com.polylingy.core.content.CourseService
import com.polylingy.core.journal.AnswerSubmitted
import com.polylingy.core.journal.EventEnvelope
import com.polylingy.core.journal.ZonedInstant
import com.polylingy.core.ports.Clock
import com.polylingy.core.rules.Checking
import com.polylingy.core.study.StatisticsService
import com.polylingy.core.study.StudyService
import kotlinx.datetime.TimeZone
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.measureTime
import kotlin.uuid.Uuid

/**
 * The progress is never stored: every screen replays the whole journal (docs/architecture.md). This builds the real course of the
 * installer (333 topics, 5399 exercises) with a journal as big as months of daily work would make, in a database file, and times
 * what the screens do. The numbers are printed (see docs/testing.md); the limits only catch a change that makes it much worse.
 * Writing the journal takes ~3 ms per answer (one disk commit each), so a normal `check` writes 2000 answers; the full size,
 * 30000 answers (about 5 months of 200 a day, a minute and a half to write), is run with `-Dpolylingy.perf=full` — see docs/testing.md.
 */
class BigJournalTest {
    private val dir = Files.createTempDirectory("polylingy-big")
    private val storage = DesktopStorage.open(dir)
    private val start = Instant.parse("2026-06-01T08:00:00Z")
    private var now = start
    private val clock = Clock { ZonedInstant(now, TimeZone.UTC) }

    @AfterTest fun cleanUp() {
        storage.close()
        dir.toFile().deleteRecursively()
    }

    private fun course(): Course {
        val bundled = BundledCourse.find()!!
        val library = CourseLibrary(storage.courses, CourseService(storage.courses, storage.courseSettings, clock), FileImageStore(dir.resolve("images")), { error("no network") }, CourseArchive, clock)
        library.seedBundled(bundled.id, bundled.version, bundled::source)
        return storage.courses.get(bundled.id)!!
    }

    /** [answers] answers over [days] days: each topic is worked on in turn, mostly right, some wrong. */
    private fun fill(course: Course, answers: Int, days: Int) {
        val random = Random(7)
        val topics = course.allTopics
        val ready = topics.map { topic -> topic to topic.exercises.map { it to Checking.check(it, it.solvedResponse()).map { c -> c.result } } }
        repeat(answers) { n ->
            now = start + (n * days.toLong() * 86_400 / answers).seconds
            val (topic, exercises) = ready[random.nextInt(ready.size)]
            val (exercise, results) = exercises[random.nextInt(exercises.size)]
            val wrong = random.nextInt(10) == 0
            storage.journal.append(
                EventEnvelope(
                    eventId = Uuid.random(),
                    deviceId = storage.deviceId,
                    occurredAt = ZonedInstant(now, TimeZone.UTC),
                    event = AnswerSubmitted(course.id, topic.id, exercise.id, course.version, exercise.solvedResponse(), if (wrong) results.map { ItemResult.WRONG } else results),
                ),
            )
        }
    }

    @Test fun theScreensStayFastOnAJournalOfManyMonths() {
        val full = System.getProperty("polylingy.perf") == "full"
        val answers = if (full) 30_000 else 2_000
        val days = if (full) 150 else 10
        val course = course()
        val fill = measureTime { fill(course, answers = answers, days = days) }
        println("PERF journal of $answers answers over $days days written in $fill")
        now = start + (days + 1).days
        val study = StudyService(storage.courses, storage.courseSettings, storage.journal, clock, storage.deviceId)

        val loadCourses = measureTime { assertTrue(storage.courses.all().size == 1) }
        val read = measureTime { assertTrue(storage.journal.all().size == answers) }
        val progress = measureTime { assertNotNull(study.progress(course.id)) }
        val progressAgain = measureTime { study.progress(course.id) }
        val next = measureTime { assertNotNull(study.next(course.id)) }
        // the choice of the exercise reads the topic's answers from the journal: time it with the one that reads most
        storage.courseSettings.save(course.id, com.polylingy.core.content.RulesSpec(picker = com.polylingy.core.content.PickerSpec.Conveyor(newPerDay = 4, exercise = com.polylingy.core.content.ExerciseSpec.MISSED_FIRST)), clock.now().instant)
        val nextMissed = measureTime { assertNotNull(study.next(course.id)) }
        println("PERF next exercise with missed-first: $nextMissed")
        val answered = study.next(course.id)!!
        val answer = measureTime { study.answer(course.id, answered.topic.id, answered.exercise.id, answered.exercise.solvedResponse()) }

        val statistics = StatisticsService(storage.courses, storage.courseSettings, storage.journal, clock, study)
        val stats = measureTime { assertNotNull(statistics.statistics()) }
        val statsAgain = measureTime { statistics.statistics() }
        val streak = measureTime { statistics.streak() }
        println("PERF statistics screen (replays twice): first $stats, again $statsAgain; the streak of the header $streak")

        println("PERF load the course document (4 MB of JSON): $loadCourses")
        println("PERF read all $answers events: $read")
        println("PERF progress (the course screen and the course list): first $progress, again $progressAgain")
        println("PERF next exercise: $next")
        println("PERF answer one exercise: $answer")

        // Generous limits: they are here to catch a change that makes a screen unusable, not to measure.
        for ((what, time) in listOf("progress" to progressAgain, "next exercise" to next, "next exercise, missed first" to nextMissed, "answer" to answer, "statistics" to statsAgain, "the streak" to streak)) {
            assertTrue(time < 3.seconds, "$what took $time on a journal of $answers answers")
        }
    }
}

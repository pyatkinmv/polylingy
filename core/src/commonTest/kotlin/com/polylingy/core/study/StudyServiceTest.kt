package com.polylingy.core.study

import com.polylingy.core.FixedClock
import com.polylingy.core.InMemoryCourseRepository
import com.polylingy.core.InMemoryEventLog
import com.polylingy.core.at
import com.polylingy.core.content.Topic
import com.polylingy.core.course
import com.polylingy.core.journal.AnswerSubmitted
import com.polylingy.core.journal.AutoCheck
import com.polylingy.core.journal.Rating
import com.polylingy.core.topic
import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class StudyServiceTest {
    private val day1 = LocalDate(2026, 1, 15)
    private val a = topic("A")
    private val b = topic("B")
    private val empty = topic("no exercises", exercises = emptyList())
    private val c = course(a, b, empty)

    private val clock = FixedClock(at(day1))
    private val journal = InMemoryEventLog()
    private val deviceId = Uuid.random()
    private val service = StudyService(InMemoryCourseRepository(c), journal, clock, deviceId, random = Random(42))

    private fun graduate(topic: Topic) = repeat(3) {
        service.submitAnswer(c.id, topic.id, topic.exercises.first().id, "x", AutoCheck.CORRECT, Rating.CORRECT)
    }

    private fun stats() = service.courseStats().single()

    @Test fun freshCourseCountsEveryStudyableTopicAsNew() {
        val stats = stats()
        assertEquals(2, stats.newCount)
        assertEquals(0, stats.toReview)
        assertEquals(0, stats.doneToday)
    }

    @Test fun graduatedTodayCountsAsDone() {
        graduate(a)
        val stats = stats()
        assertEquals(1, stats.newCount)
        assertEquals(1, stats.doneToday)
    }

    @Test fun dueTopicCountsAsToReview() {
        graduate(a)
        clock.set(LocalDate(2026, 1, 18))
        assertEquals(1, stats().toReview)
    }

    @Test fun coursesAreSortedOldestFirst() {
        val older = course(topic(), createdAt = Instant.fromEpochSeconds(-100))
        val svc = StudyService(InMemoryCourseRepository(c, older), journal, clock, deviceId)
        assertEquals(listOf(older.id, c.id), svc.courseStats().map { it.course.id })
    }

    @Test fun nextExerciseNeverOffersTopicsWithoutExercises() {
        repeat(20) { assertTrue(service.nextExercise(c.id)!!.topic.id != empty.id) }
    }

    @Test fun graduatedTopicIsNotOfferedAgainToday() {
        graduate(a)
        repeat(20) { assertEquals(b.id, service.nextExercise(c.id)!!.topic.id) }
    }

    @Test fun doneForTodayWhenEverythingGraduated() {
        graduate(a)
        graduate(b)
        assertNull(service.nextExercise(c.id))
        clock.set(LocalDate(2026, 1, 18))
        assertNotNull(service.nextExercise(c.id))
    }

    @Test fun submitAnswerRecordsTheFullEvent() {
        service.submitAnswer(c.id, a.id, a.exercises.first().id, "hola", AutoCheck.PARTIAL, null)
        val envelope = journal.all().single()
        assertEquals(deviceId, envelope.deviceId)
        assertEquals(clock.now(), envelope.occurredAt)
        assertEquals(AnswerSubmitted(c.id, a.id, a.exercises.first().id, "hola", AutoCheck.PARTIAL, null), envelope.event)
    }

    @Test fun unknownCourseHasNoExercises() = assertNull(service.nextExercise(Uuid.random()))
}

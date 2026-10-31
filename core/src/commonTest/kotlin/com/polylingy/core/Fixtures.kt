package com.polylingy.core

import com.polylingy.core.content.Course
import com.polylingy.core.content.Exercise
import com.polylingy.core.content.Topic
import com.polylingy.core.journal.AnswerSubmitted
import com.polylingy.core.journal.AutoCheck
import com.polylingy.core.journal.EventEnvelope
import com.polylingy.core.journal.Rating
import com.polylingy.core.journal.ZonedInstant
import com.polylingy.core.ports.Clock
import com.polylingy.core.ports.CourseRepository
import com.polylingy.core.ports.EventLog
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.time.Instant
import kotlin.uuid.Uuid

val Moscow = TimeZone.of("Europe/Moscow")

fun at(date: LocalDate, hour: Int = 12, zone: TimeZone = Moscow) =
    ZonedInstant(date.atTime(LocalTime(hour, 0)).toInstant(zone), zone)

fun exercise(task: String = "task {0}", vararg answers: String = arrayOf("x")) = Exercise(
    id = Uuid.random(),
    task = task,
    answers = answers.withIndex().associate { (i, a) -> "$i" to a },
)

fun topic(subject: String = "topic", exercises: List<Exercise> = listOf(exercise())) =
    Topic(id = Uuid.random(), subject = subject, exercises = exercises)

fun course(vararg topics: Topic, createdAt: Instant = Instant.fromEpochSeconds(0)) = Course(
    id = Uuid.random(),
    name = "course",
    createdAt = createdAt,
    revision = 1,
    updatedAt = createdAt,
    topics = topics.toList(),
)

/** A self-rated answer to the topic's first exercise. */
fun answer(course: Course, topic: Topic, correct: Boolean, occurredAt: ZonedInstant) = EventEnvelope(
    eventId = Uuid.random(),
    deviceId = Uuid.NIL,
    occurredAt = occurredAt,
    event = AnswerSubmitted(
        courseId = course.id,
        topicId = topic.id,
        exerciseId = topic.exercises.first().id,
        input = "",
        autoCheck = if (correct) AutoCheck.CORRECT else AutoCheck.INCORRECT,
        rating = if (correct) Rating.CORRECT else Rating.INCORRECT,
    ),
)

class InMemoryCourseRepository(vararg courses: Course) : CourseRepository {
    private val byId = courses.associateBy { it.id }.toMutableMap()
    override fun all() = byId.values.toList()
    override fun get(id: Uuid) = byId[id]
    override fun save(course: Course) { byId[course.id] = course }
    override fun delete(id: Uuid) { byId.remove(id) }
}

class InMemoryEventLog : EventLog {
    private val events = mutableListOf<EventEnvelope>()
    override fun append(event: EventEnvelope) { events += event }
    override fun all() = events.toList()
}

/** A clock that stays put until moved with [set]. */
class FixedClock(var now: ZonedInstant) : Clock {
    override fun now() = now
    fun set(date: LocalDate, hour: Int = 12) { now = at(date, hour) }
}

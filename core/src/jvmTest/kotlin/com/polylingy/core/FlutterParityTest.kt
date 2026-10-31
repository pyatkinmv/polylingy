package com.polylingy.core

import com.polylingy.core.journal.AnswerSubmitted
import com.polylingy.core.journal.AutoCheck
import com.polylingy.core.journal.EventEnvelope
import com.polylingy.core.journal.Rating
import com.polylingy.core.journal.ZonedInstant
import com.polylingy.core.progress.replay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

@Serializable
private data class GoldenSession(val day: LocalDate, val answers: List<Boolean>)

@Serializable
private data class GoldenProgress(val easeFactor: Double, val intervalDays: Int, val nextReviewDate: LocalDate)

@Serializable
private data class GoldenScenario(val sessions: List<GoldenSession>, val expected: List<GoldenProgress>)

/**
 * Differential check against the Flutter implementation. `flutter_sm2_golden.json` was produced
 * by running the Dart `ConsecutiveCorrectPolicy` + `Sm2Policy` (lib/services, before the Flutter
 * code was removed) on 200 random answer histories, each session running until graduation.
 * The Kotlin journal + replay() must reproduce every graduation exactly.
 */
class FlutterParityTest {
    private val scenarios: List<GoldenScenario> = Json.decodeFromString(
        requireNotNull(javaClass.getResource("/flutter_sm2_golden.json")).readText(),
    )

    @Test fun replayMatchesTheFlutterScheduleAfterEveryGraduation() {
        var graduations = 0
        scenarios.forEachIndexed { s, scenario ->
            val topic = topic()
            val course = course(topic)
            val events = mutableListOf<EventEnvelope>()

            scenario.sessions.zip(scenario.expected).forEachIndexed { k, (session, expected) ->
                session.answers.forEachIndexed { i, correct -> events += answerOn(course.id, topic, session.day, i, correct) }
                val progress = replay(listOf(course), events).getValue(topic.id).progress

                val where = "scenario $s, graduation $k"
                assertEquals(expected.intervalDays, progress.intervalDays, "$where: interval")
                assertEquals(expected.nextReviewDate, progress.nextReviewDate, "$where: next review")
                assertEquals(expected.easeFactor, progress.easeFactor, 1e-9, "$where: ease factor")
                graduations++
            }
        }
        assertEquals(856, graduations)
    }

    private fun answerOn(courseId: Uuid, topic: com.polylingy.core.content.Topic, day: LocalDate, index: Int, correct: Boolean) =
        EventEnvelope(
            eventId = Uuid.random(),
            deviceId = Uuid.NIL,
            occurredAt = ZonedInstant(day.atTime(LocalTime(8, 0)).toInstant(TimeZone.UTC) + kotlin.time.Duration.parse("${index}m"), TimeZone.UTC),
            event = AnswerSubmitted(
                courseId, topic.id, topic.exercises.first().id, input = "",
                autoCheck = null, rating = if (correct) Rating.CORRECT else Rating.INCORRECT,
            ),
        )
}

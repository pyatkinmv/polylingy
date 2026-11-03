package com.polylingy.core

import com.polylingy.core.journal.EventEnvelope
import com.polylingy.core.journal.ZonedInstant
import com.polylingy.core.learning.AllCorrect
import com.polylingy.core.learning.Consecutive
import com.polylingy.core.learning.CourseRules
import com.polylingy.core.content.PickerSpec
import com.polylingy.core.learning.toPicker
import kotlin.random.Random
import com.polylingy.core.learning.Sm2Scheduler
import com.polylingy.core.learning.replay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes

@Serializable
private data class GoldenSession(val day: LocalDate, val answers: List<Boolean>)

@Serializable
private data class GoldenProgress(val easeFactor: Double, val intervalDays: Int, val nextReviewDate: LocalDate)

@Serializable
private data class GoldenScenario(val sessions: List<GoldenSession>, val expected: List<GoldenProgress>)

/**
 * Guards the SM-2 schedule. `sm2_golden.json` holds 200 random answer histories (each session
 * running until graduation) with the results of the app's first implementation of these rules.
 * The journal + replay() with SM-2 and 3 in a row in every round must reproduce every graduation
 * exactly; a failure means the scheduling rules changed, which should be a deliberate decision.
 */
class Sm2GoldenTest {
    private val scenarios: List<GoldenScenario> = Json.decodeFromString(
        requireNotNull(javaClass.getResource("/sm2_golden.json")).readText(),
    )

    /** How 0.1 worked: 3 right in a row in learning and in review alike. */
    private val rules = CourseRules(AllCorrect(), Consecutive(learning = 3, review = 3), Sm2Scheduler, PickerSpec.Conveyor().toPicker(Random(0)))

    @Test fun replayMatchesTheGoldenScheduleAfterEveryGraduation() {
        var graduations = 0
        scenarios.forEachIndexed { s, scenario ->
            val topic = topic()
            val course = course(topic)
            val events = mutableListOf<EventEnvelope>()

            scenario.sessions.zip(scenario.expected).forEachIndexed { k, (session, expected) ->
                session.answers.forEachIndexed { i, correct ->
                    val at = ZonedInstant(session.day.atTime(LocalTime(8, 0)).toInstant(TimeZone.UTC) + i.minutes, TimeZone.UTC)
                    events += answer(course, topic, correct, at)
                }
                val state = replay(course, events, rules).getValue(topic.id)
                val memory = assertNotNull(state.memory)

                val where = "scenario $s, graduation $k"
                assertEquals(emptyList(), state.round, "$where: round finished")
                assertEquals(expected.intervalDays, memory.intervalDays, "$where: interval")
                assertEquals(expected.nextReviewDate, memory.due, "$where: next review")
                assertEquals(expected.easeFactor, memory.easeFactor, 1e-9, "$where: ease factor")
                graduations++
            }
        }
        assertEquals(856, graduations)
    }
}

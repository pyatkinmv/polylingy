package com.polylingy.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import com.polylingy.app.AppContainer
import com.polylingy.app.MovableClock
import com.polylingy.app.TestZone
import com.polylingy.content.ItemResult
import com.polylingy.content.Response
import com.polylingy.core.journal.AnswerSubmitted
import com.polylingy.core.journal.EventEnvelope
import com.polylingy.core.journal.ZonedInstant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

// The statistics tab: numbers from the journal, the activity map, the entrance from the course list; see docs/testing.md.
class StatisticsUiTest {
    private val today = LocalDate(2026, 1, 15)

    private fun topic(n: Int) = """{ "title": "Topic $n", "exercises": [ { "type": "gaps", "items": [ { "text": "{{a$n}}" } ] }, { "type": "gaps", "items": [ { "text": "{{b$n}}" } ] } ] }"""

    private val course = """{ "formatVersion": 1, "name": "English Usage Exercises", "rules": { "picker": { "type": "conveyor", "newPerDay": 4 } },
        "topics": [ ${(1..10).joinToString(",") { topic(it) }} ] }"""

    /** About a year of answers: most days, quieter at weekends, a few wrong ones; today has some already. */
    private fun AppContainer.seedYear() {
        val c = seedCourse(course)
        val random = Random(11)
        val topics = c.topics
        for (back in 330 downTo 0) {
            val day = today.minus(back, DateTimeUnit.DAY)
            val weekend = day.dayOfWeek.ordinal >= 5
            if (random.nextDouble() > (if (weekend) 0.55 else 0.9)) continue
            val n = if (back == 0) 7 else 5 + random.nextInt(if (back < 120) 70 else 25)
            repeat(n) { i ->
                val topic = topics[random.nextInt(topics.size)]
                val wrong = random.nextInt(8) == 0
                storage.journal.append(
                    EventEnvelope(
                        Uuid.random(), Uuid.NIL, ZonedInstant(day.atTime(8 + i % 12, i % 60).toInstant(TestZone), TestZone),
                        event = AnswerSubmitted(c.id, topic.id, topic.exercises.first().id, c.version, Response.Blanks(listOf("x")), listOf(if (wrong) ItemResult.WRONG else ItemResult.CORRECT)),
                    ),
                )
            }
        }
    }

    /** Something of the page is there, even if the window shows only its top. */
    private fun com.polylingy.app.ui.AppHarness.exists(text: String) { text(text).fetchSemanticsNode() }

    private fun com.polylingy.app.ui.AppHarness.clickTab(title: String) {
        test.onNode(hasText(title) and hasClickAction()).performClick()
        test.waitForIdle()
    }

    @Test fun theTabShowsTheNumbersAndTheMapTellsItsDayWhenPointedAt() = appTest(seed = { it.seedYear() }, width = 1280, height = 900) {
        // the entrance: the tabs and the streak in the header of the course list
        test.onNode(hasText("Statistics") and hasClickAction()).assertIsDisplayed()
        screenshot("stats-entrance-courses")
        clickTab("Statistics")

        text("STREAK").assertIsDisplayed()
        text("LEARNED").assertIsDisplayed()
        text("REVIEWS COMING UP").assertIsDisplayed()
        test.onNodeWithText("of 4 new topics", substring = true).assertIsDisplayed()
        test.onNodeWithText("answers in the last year", substring = true).assertIsDisplayed()
        screenshot("statistics")

        // pointing at a square names its day; today's square is in the last column, the page does not move
        val map = test.onNodeWithTag("activity-map")
        val before = map.fetchSemanticsNode().positionInRoot
        val width = map.fetchSemanticsNode().size.width
        val density = test.density.density
        val step = 20 * density
        val weeks = ((width / density + 4) / 20).toInt()
        val todayRow = today.dayOfWeek.ordinal
        test.onAllNodesWithTag("activity-tip").assertCountEquals(0)
        map.performMouseInput { moveTo(Offset((weeks - 1) * step + 8 * density, (todayRow - 1) * step + 8 * density)) }
        test.waitForIdle()
        test.onNodeWithTag("activity-tip").assertIsDisplayed()
        test.onNodeWithText("Wed 14 Jan").assertIsDisplayed()
        test.onNodeWithText(" passed", substring = true).assertIsDisplayed()
        assertEquals(before, test.onNodeWithTag("activity-map").fetchSemanticsNode().positionInRoot, "the map stays where it is")
        screenshot("statistics-hover")
        map.performMouseInput { moveTo(Offset(-5f, -5f)) }
        test.waitForIdle()
        test.onAllNodesWithTag("activity-tip").assertCountEquals(0)

        clickTab("Courses")
        text("English Usage Exercises").assertIsDisplayed()
    }

    @Test fun theStreakChipOpensTheStatisticsToo() = appTest(seed = { it.seedYear() }, width = 1280, height = 700) {
        val days = container.statistics.streak()
        assertTrue(days > 0, "the seeded year ends today")
        click(if (days == 1) "1 day" else "$days days")
        exists("REVIEWS COMING UP")
    }

    @Test fun anEmptyJournalShowsZerosAndNoStreakChip() = appTest(seed = { it.seedCourse(course) }, width = 1280, height = 1000) {
        assertEquals(0, container.statistics.streak())
        clickTab("Statistics")
        text("STREAK").assertIsDisplayed()
        screenshot("statistics-empty")
    }

    @Test fun aCourseWithoutALimitHasNoGoalRing() = appTest(
        seed = {
            val c = it.seedCourse(course.replace("""{ "type": "conveyor", "newPerDay": 4 }""", """{ "type": "conveyor" }"""))
            val topic = c.topics.first()
            it.storage.journal.append(
                EventEnvelope(
                    Uuid.random(), Uuid.NIL, ZonedInstant(today.atTime(9, 0).toInstant(TestZone), TestZone),
                    event = AnswerSubmitted(c.id, topic.id, topic.exercises.first().id, c.version, Response.Blanks(listOf("a1")), listOf(ItemResult.CORRECT)),
                ),
            )
        },
        width = 1280, height = 900,
    ) {
        clickTab("Statistics")
        text("No daily limit").assertIsDisplayed()
        screenshot("statistics-no-limit")
    }
}

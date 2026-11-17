package com.polylingy.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.polylingy.app.AppContainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The admin page of a unit: all its theory and all its exercises on one page, checked without recording; see docs/testing.md.
class AdminUiTest {
    private val course = """{ "formatVersion": 1, "name": "Grammar in Use",
        "units": [
          { "title": "Present continuous", "subtitle": "I am doing",
            "theory": { "format": "blocks", "blocks": [
              { "type": "section", "label": "A", "title": "Happening now", "blocks": [ { "type": "text", "text": "He**’s cycling** to work." } ] } ] },
            "topics": [
              { "title": "Things happening now", "exercises": [ { "type": "gaps", "instruction": "Complete the sentences.",
                "items": [ { "text": "Listen! Somebody {{is singing}}." }, { "text": "I {{’m not reading|am not reading}} it now." } ] } ] },
              { "title": "Questions", "exercises": [ { "type": "choice", "instruction": "Pick the right form.",
                "items": [ { "text": "What {{are you doing}}?", "options": ["do you do", "are you doing"] } ] } ] } ] },
          { "title": "Present simple", "topics": [ { "title": "Habits", "exercises": [ { "type": "gaps", "items": [ { "text": "She {{works}} here." } ] } ] } ] } ] }"""

    private val seed: (AppContainer) -> Unit = { it.seedCourse(course) }

    @Test fun theWholeUnitIsOnOnePageAndAnswersAreNotRecorded() = appTest(seed, height = 1100) {
        click("Grammar in Use")
        click("Present continuous")
        click("Admin")
        // the theory and every exercise of every topic, one under another
        text("Happening now").assertIsDisplayed()
        text("Things happening now").assertIsDisplayed()
        text("Questions").assertIsDisplayed()
        text("Complete the sentences.").assertIsDisplayed()
        text("Pick the right form.").assertIsDisplayed()
        screenshot("admin")

        // an exercise can be answered and checked here, but the journal stays empty
        test.onAllNodes(hasContentDescription("Blank 1"))[0].performTextInput("is singing")
        test.onAllNodes(hasContentDescription("Blank 2"))[0].performTextInput("am not reading")
        test.waitForIdle()
        test.onAllNodesWithText("Check")[0].performClick()
        test.waitForIdle()
        text("Again").assertIsDisplayed()
        screenshot("admin-checked")
        assertEquals(0, container.storage.journal.all().size, "nothing is recorded on the admin page")
    }

    @Test fun showAnswersFillsEveryExerciseWithItsKey() = appTest(seed, height = 1100) {
        click("Grammar in Use")
        click("Present continuous")
        click("Admin")
        test.onNode(isToggleable()).performClick()
        test.waitForIdle()
        assertTrue(test.onAllNodesWithText("is singing", substring = true).fetchSemanticsNodes().isNotEmpty(), "the key of the gaps is shown")
        screenshot("admin-key")
        assertEquals(0, container.storage.journal.all().size)
    }
}

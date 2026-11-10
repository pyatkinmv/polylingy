package com.polylingy.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import com.polylingy.app.AppContainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Adding, editing and deleting units and topics; see docs/testing.md.
class EditUiTest {
    private val course = """{ "formatVersion": 1, "name": "Grammar in Use",
        "units": [
          { "title": "Present continuous", "subtitle": "I am doing",
            "theory": { "format": "blocks", "blocks": [
              { "type": "section", "label": "A", "title": "Happening now",
                "blocks": [ { "type": "text", "text": "He**’s cycling** to work." } ] } ] },
            "topics": [
              { "title": "Things happening now", "exercises": [
                { "type": "gaps", "instruction": "Complete the sentences.",
                  "items": [ { "text": "Listen! Somebody {{is singing}}.", "hint": "sing" } ] },
                { "type": "choice", "items": [ { "text": "Shh! The baby {{is sleeping}}.", "options": ["sleeps", "is sleeping"] } ] } ] },
              { "title": "Questions", "exercises": [ { "type": "choice",
                "items": [ { "text": "What {{are you doing}}?", "options": ["do you do", "are you doing"] } ] } ] } ] },
          { "title": "Present simple", "subtitle": "I do",
            "topics": [ { "title": "Habits", "exercises": [ { "type": "gaps", "items": [ { "text": "She {{works}} here." } ] } ] } ] } ] }"""

    private val flat = """{ "formatVersion": 1, "name": "Irregular verbs",
        "topics": [ { "title": "be", "exercises": [ { "type": "gaps", "items": [ { "text": "I {{was}} there." } ] } ] } ] }"""

    private val seed: (AppContainer) -> Unit = { it.seedCourse(course) }

    private fun AppHarness.stored() = container.storage.courses.all().single { it.name == "Grammar in Use" }

    private fun AppHarness.json() = test.onNode(hasContentDescription("JSON"))

    private fun AppHarness.type(json: String) {
        json().performTextReplacement(json)
        test.waitForIdle()
    }

    @Test fun theCourseHasAnAddButtonByItsListAndAMenuOnEachUnit() = appTest(seed) {
        click("Grammar in Use")
        text("＋ Add unit").assertIsDisplayed()
        openMenu(0)
        text("Edit unit…").assertIsDisplayed()
        text("Add topic…").assertIsDisplayed()
        text("Delete unit…").assertIsEnabled()
        screenshot("edit-unit-menu")
    }

    @Test fun aNewUnitStartsFromTheGreyTemplate() = appTest(seed, height = 900) {
        click("Grammar in Use")
        click("＋ Add unit")
        text("Add unit").assertIsNotEnabled()
        screenshot("edit-add-unit-empty")

        click("use it as a start")
        text("Valid · 1 exercise").assertIsDisplayed()
        screenshot("edit-add-unit-template")
        click("Add unit")

        text("Unit title").assertIsDisplayed()
        assertEquals(listOf("Present continuous", "Present simple", "Unit title"), stored().units.map { it.title })
    }

    @Test fun anEditedTopicShowsProblemsByLineAndSavesOnceFixed() = appTest(seed, height = 1000) {
        click("Grammar in Use")
        click("Present continuous")
        menu("Edit topic…", index = 0)
        text("Valid · 2 exercises").assertIsDisplayed()
        text("Save").assertIsNotEnabled()
        text("Exercise 1 · gaps").assertIsDisplayed()
        screenshot("edit-topic")

        val revision = stored().revision
        val original = container.courseService.partJson(stored().id, com.polylingy.core.content.CoursePart.Topic(stored().units[0].id, stored().units[0].topics[0].id))!!
        type(original.replace("\"items\"", "\"itmes\"").replace("is singing}}", "is singing"))
        test.onNodeWithText("to save", substring = true).assertIsDisplayed()
        test.onAllNodes(hasText("Line ", substring = true)).fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
        text("Save").assertIsNotEnabled()
        screenshot("edit-topic-problems")

        type(
            """
            {
              "title": "Short",
              "exercsies": [],
              "exercises": [ { "type": "gaps", "items": [ { "text": "I {{am}} here." } ] } ]
            }
            """.trimIndent(),
        )
        text("Line 3 · Encountered an unknown key 'exercsies'").assertIsDisplayed()
        screenshot("edit-topic-problem-lines")

        type(original.replace("Things happening now", "Happening now"))
        text("Valid · 2 exercises").assertIsDisplayed()
        click("Save")
        text("Happening now").assertIsDisplayed()
        assertEquals("Happening now", stored().units[0].topics[0].title)
        assertEquals(revision + 1, stored().revision)
    }

    @Test fun thePreviewCanBeSolvedWithoutTouchingProgress() = appTest(seed, height = 1000) {
        click("Grammar in Use")
        click("Present continuous")
        menu("Edit topic…", index = 0)
        fill(1, "is singing")
        click("Check")
        text("All correct").assertIsDisplayed()
        screenshot("edit-topic-preview-checked")
        click("Try again")
        click("Exercise 2 · choice")
        text("sleeps").assertIsDisplayed()
        screenshot("edit-topic-preview-second")
        assertTrue(container.storage.journal.all().isEmpty())
    }

    @Test fun aUnitPreviewsItsTheoryAndTopicsAndAsksBeforeDroppingOne() = appTest(seed, height = 1000) {
        click("Grammar in Use")
        menu("Edit unit…", index = 0)
        text("Valid · theory · 2 topics · 3 exercises").assertIsDisplayed()
        test.onNode(hasText("cycling", substring = true) and !hasContentDescription("JSON")).assertIsDisplayed()
        screenshot("edit-unit")
        click("Questions")
        test.onNode(hasText("What", substring = true) and !hasContentDescription("JSON")).assertIsDisplayed()
        screenshot("edit-unit-topic")

        val unit = stored().units[0]
        val json = container.courseService.partJson(stored().id, com.polylingy.core.content.CoursePart.Unit(unit.id))!!
        val cut = json.indexOf("    {\n      \"id\": \"${unit.topics[1].id}\"")
        type(json.substring(0, cut).trimEnd().removeSuffix(",") + "\n  ]\n}")
        click("Save")
        text("Delete 1 topic?").assertIsDisplayed()
        screenshot("edit-unit-dropped")
        click("Save and delete")
        assertEquals(listOf("Things happening now"), stored().units[0].topics.map { it.title })
    }

    @Test fun thePreviewChecksTyposByTheCourseSettings() = appTest({
        it.seedCourse(course.replace("\"name\": \"Grammar in Use\",", "\"name\": \"Grammar in Use\", \"rules\": { \"grading\": { \"type\": \"all-correct\", \"typos\": false } },"))
    }, height = 1000) {
        click("Grammar in Use")
        click("Present continuous")
        menu("Edit topic…", index = 0)
        fill(1, "is sinigng")
        click("Check")
        text("1 mistake").assertIsDisplayed()
        screenshot("edit-topic-preview-typo")
        assertTrue(container.storage.journal.all().isEmpty())
    }

    /** "Questions" was answered before it is deleted: the answer stays in the journal and no longer counts. */
    private val answered: (AppContainer) -> Unit = { container ->
        val course = container.seedCourse(course)
        val topic = course.units[0].topics[1]
        container.study.answer(course.id, topic.id, topic.exercises[0].id, com.polylingy.content.Response.Choice(listOf("are you doing")))
    }

    @Test fun topicsAndUnitsAreDeletedAfterAsking() = appTest(answered) {
        click("Grammar in Use")
        click("Present continuous")
        menu("Delete topic…", index = 1)
        text("Delete “Questions”?").assertIsDisplayed()
        screenshot("edit-delete-topic")
        click("Delete topic")
        assertEquals(listOf("Things happening now"), stored().units[0].topics.map { it.title })
        assertEquals(1, container.storage.journal.all().size)
        text("Next: Things happening now · 0 of 1 learned").assertIsDisplayed()

        openMenu(0)
        text("Delete topic…").assertIsNotEnabled()
        text("The last topic can’t be deleted").assertIsDisplayed()
        screenshot("edit-last-topic")
        test.onNode(hasText("Edit topic…")).assertIsDisplayed()
    }

    @Test fun aUnitIsDeletedButNotTheLastOne() = appTest(seed) {
        click("Grammar in Use")
        menu("Delete unit…", index = 1)
        click("Delete unit")
        assertEquals(listOf("Present continuous"), stored().units.map { it.title })
        openMenu(0)
        text("Delete unit…").assertIsNotEnabled()
        text("A course keeps at least one unit").assertIsDisplayed()
    }

    @Test fun cancellingAChangedEditorAsksFirst() = appTest(seed) {
        click("Grammar in Use")
        click("＋ Add unit")
        click("Cancel")
        text("Units").assertIsDisplayed()

        click("＋ Add unit")
        click("use it as a start")
        click("Cancel")
        text("Discard changes?").assertIsDisplayed()
        click("Discard")
        text("Units").assertIsDisplayed()
        assertEquals(2, stored().units.size)
    }

    @Test fun aTopicIsAddedToAUnitFromItsMenuOrPage() = appTest(seed, height = 900) {
        click("Grammar in Use")
        click("Present simple")
        click("＋ Add topic")
        text("in Unit 2 · Present simple").assertIsDisplayed()
        screenshot("edit-add-topic-empty")
        type("{\n  \"title\": \"Negatives\",\n  \"exercises\": [ { \"type\": \"gaps\", \"items\": [ { \"text\": \"She {{doesn’t work}}.\" } ] } ]\n}")
        click("Add topic")
        text("Negatives").assertIsDisplayed()
        assertEquals(listOf("Habits", "Negatives"), stored().units[1].topics.map { it.title })
    }

    @Test fun aFlatCourseAddsTopics() = appTest({ it.seedCourse(flat) }) {
        click("Irregular verbs")
        click("＋ Add topic")
        click("use it as a start")
        click("Add topic")
        text("Topic title").assertIsDisplayed()
        screenshot("edit-flat-course")
    }

    @Test fun aCourseFromANewerVersionCantBeChanged() = appTest({ it.seedCourse(course.replace("\"formatVersion\": 1", "\"formatVersion\": 8")) }) {
        click("Grammar in Use")
        assertTrue(test.onAllNodesWithText("＋ Add unit").fetchSemanticsNodes().isEmpty())
        assertTrue(test.onAllNodesWithText("⋯").fetchSemanticsNodes().isEmpty())
    }
}

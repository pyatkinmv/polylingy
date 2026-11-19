package com.polylingy.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.polylingy.app.AppContainer
import com.polylingy.content.Response
import com.polylingy.core.journal.AnswerSubmitted
import com.polylingy.core.journal.PracticeAnswered
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Extra practice: where it starts, what its screen looks like, how it ends, and that the plan stays as it was; see docs/testing.md.
class PracticeUiTest {
    private val course = """{ "formatVersion": 1, "name": "English Usage",
        "rules": { "mastery": { "type": "consecutive", "learning": 1, "review": 1 }, "picker": { "type": "conveyor", "newPerDay": 2 } },
        "units": [
          { "title": "Conditionals", "topics": [
              { "title": "Second vs. Third", "explanation": { "format": "blocks", "blocks": [ { "type": "text", "text": "The topic rule." } ] },
                "exercises": [
                  { "type": "gaps", "instruction": "Complete the sentences.", "items": [ { "text": "I {{walk}}[[walk]] home." } ] },
                  { "type": "gaps", "instruction": "Complete the sentences.", "items": [ { "text": "She {{runs}}[[run]] daily." } ] } ] },
              { "title": "Zero vs. First", "exercises": [ { "type": "gaps", "items": [ { "text": "We {{swim}}." } ] } ] } ] },
          { "title": "Modals", "topics": [ { "title": "must vs. have to", "exercises": [ { "type": "gaps", "items": [ { "text": "You {{must}} go." } ] } ] } ] } ] }"""

    /** The plan begins two topics (the daily limit is 2) and passes both with one right answer: nothing is left for today. */
    private val seed: (AppContainer) -> Unit = { container ->
        val c = container.seedCourse(course)
        for (topic in c.allTopics.take(2)) {
            val e = topic.exercises.first()
            val answer = ((e as com.polylingy.content.Exercise.Gaps).items.single().text.blanks.single().answer)
            container.study.answer(c.id, topic.id, e.id, Response.Blanks(listOf(answer)))
        }
    }

    @Test fun practiceStartsFromTheCourseRunsApartFromThePlanAndEndsWithASummary() = appTest(seed, height = 900) {
        val c = container.storage.courses.all().single()
        val before = container.study.progress(c.id)!!
        click("English Usage")
        click("Extra practice")

        text("EXTRA PRACTICE").assertIsDisplayed()
        text("doesn’t change your plan").assertIsDisplayed()
        text("0 answered · 0 right").assertIsDisplayed()
        screenshot("practice-exercise")
        // a topic's exercises come once each: the first topic has two
        val first = test.onAllNodesWithText("I ", substring = true).fetchSemanticsNodes().isNotEmpty()
        val answer = if (first) "walk" else "runs"
        fill(1, answer)
        click("Check")
        text("All correct").assertIsDisplayed()
        screenshot("practice-checked")
        click("Next →")
        text("1 answered · 1 right").assertIsDisplayed()

        click("Finish")
        text("Practice finished").assertIsDisplayed()
        text("1 answered · 1 right (100%)").assertIsDisplayed()
        text("Your plan and your reviews are exactly as they were.").assertIsDisplayed()
        screenshot("practice-summary")

        click("Back")
        assertEquals(before, container.study.progress(c.id), "practice changed nothing of the plan")
        val events = container.storage.journal.all().map { it.event }
        assertEquals(1, events.count { it is PracticeAnswered })
        assertEquals(2, events.count { it is AnswerSubmitted }, "the plan has only its own two answers")
    }

    @Test fun morePracticeStartsOverAndATopicPageStartsItsOwnPractice() = appTest(seed, height = 900) {
        click("English Usage")
        click("Conditionals")
        click("Extra practice")                       // from the unit: the topics the plan has started in it
        text("EXTRA PRACTICE").assertIsDisplayed()
        click("Finish")
        text("Nothing was answered.").assertIsDisplayed()
        click("Practise more")
        text("EXTRA PRACTICE").assertIsDisplayed()
        click("Finish")
        click("Back")

        click("Second vs. Third")                      // a resting topic: its page offers practice of itself
        text("Done for now").assertIsDisplayed()
        screenshot("practice-topic-page")
        click("Extra practice")
        text("EXTRA PRACTICE").assertIsDisplayed()
    }

    @Test fun nothingStartedMeansNothingToPractise() = appTest(seed = { it.seedCourse(course) }, height = 900) {
        click("English Usage")
        text("Extra practice").assertDoesNotExist()
        assertTrue(true)
    }
}

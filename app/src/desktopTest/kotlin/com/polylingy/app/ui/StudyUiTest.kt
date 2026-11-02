package com.polylingy.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextInput
import com.polylingy.app.AppContainer
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

// Studying, from the course list to "done for today" and back the next day; see docs/testing.md.
class StudyUiTest {
    private val sectionB = "0a1b2c3d-0000-4000-8000-0000000000b1"

    /** Two units; one right answer passes a topic, so a whole round fits in a test. */
    private val course = """{ "formatVersion": 1, "name": "Grammar in Use",
        "description": "Theory pages and exercises, unit by unit.",
        "rules": { "mastery": { "type": "consecutive", "learning": 1, "review": 1 } },
        "units": [
          { "title": "Present continuous", "subtitle": "I am doing",
            "theory": { "format": "blocks", "blocks": [
              { "type": "section", "label": "A", "title": "Happening now",
                "blocks": [ { "type": "text", "text": "He**’s cycling** to work." } ] },
              { "type": "section", "id": "$sectionB", "label": "B", "title": "Started, not finished",
                "blocks": [ { "type": "text", "text": "**I am doing** something = I’ve started it, and I haven’t finished." } ] } ] },
            "topics": [
              { "title": "Things happening now", "exercises": [ { "type": "gaps", "instruction": "Complete the sentences.",
                "items": [
                  { "text": "Listen! Somebody {{is singing}}.", "hint": "sing" },
                  { "text": "I {{’m not reading|am not reading}} it now.", "hint": "not / read", "note": "Negative: am / is / are + **not** + -ing." } ],
                "explanation": { "format": "blocks", "blocks": [
                  { "type": "text", "text": "Negatives keep **-ing**: I’m not reading, she isn’t reading." },
                  { "type": "seeAlso", "links": [ { "section": "$sectionB" } ] } ] } } ] },
              { "title": "Questions",
                "explanation": { "format": "blocks", "blocks": [ { "type": "text", "text": "Questions put **am / is / are** first." } ] },
                "exercises": [ { "type": "choice",
                "items": [ { "text": "What {{are you doing}}?", "options": ["do you do", "are you doing"] } ] } ] } ] },
          { "title": "Present simple", "subtitle": "I do",
            "topics": [ { "title": "Habits", "exercises": [ { "type": "gaps", "items": [ { "text": "She {{works}} here." } ] } ] } ] } ] }"""

    private val seed: (AppContainer) -> Unit = { it.seedCourse(course) }

    @Test fun aNewUnitStartsWithItsTheoryThenGoesThroughItsTopicsToDoneForToday() = appTest(seed, height = 900) {
        click("Grammar in Use")
        text("3 new").assertIsDisplayed()
        text("Next: Things happening now · 0 of 3 learned").assertIsDisplayed()
        screenshot("course")

        click("Present continuous")
        text("Unit 1").assertIsDisplayed()
        screenshot("unit")

        click("Continue →")
        text("Start exercises →").assertIsDisplayed()
        screenshot("theory-start")
        click("Start exercises →")

        text("Complete the sentences.").assertIsDisplayed()
        fill(1, "is singing")
        fill(2, "don’t read")
        screenshot("exercise")
        click("Check")
        text("1 mistake").assertIsDisplayed()
        text("Negative: am / is / are + not + -ing.").assertIsDisplayed()
        screenshot("exercise-checked")

        click("B · Started, not finished")
        test.onNode(hasContentDescription("Linked section")).assertIsDisplayed()
        screenshot("theory-from-link")
        click("← Back to the exercise")
        text("1 mistake").assertIsDisplayed()

        click("Next →")
        fill(1, "is singing")
        fill(2, "am not reading")
        click("Check")
        text("All correct").assertIsDisplayed()
        text("Topic passed — next review tomorrow").assertIsDisplayed()
        text("Negatives keep -ing: I’m not reading, she isn’t reading.").assertIsDisplayed()
        screenshot("exercise-passed")

        click("Next →")
        click("are you doing")
        click("Check")
        text("Questions put am / is / are first.").assertIsDisplayed()
        screenshot("exercise-topic-explanation")
        click("Next →")
        text("That’s all for today").assertIsDisplayed()
        text("Nothing is due in this unit. The next review is tomorrow.").assertIsDisplayed()
        screenshot("done-for-today")

        click("Back to the unit")
        assertEquals(2, test.onAllNodesWithText("Back tomorrow").fetchSemanticsNodes().size)
        click("Questions")
        text("Done for now").assertIsDisplayed()
        text("It comes back for review tomorrow.").assertIsDisplayed()
        screenshot("topic-resting")
    }

    @Test fun theNextDayTheCourseShowsWhatIsDue() = appTest(seed, height = 800) {
        click("Grammar in Use")
        click("Present simple")
        click("Continue →")
        fill(1, "works")
        click("Check")
        click("Next →")
        text("That’s all for today").assertIsDisplayed()

        clock.now = LocalDateTime(2026, 1, 16, 9, 0)
        click("Courses")
        text("1 to review").assertIsDisplayed()
        text("1 of 3 learned").assertIsDisplayed()
        screenshot("course-list-due")
        click("Grammar in Use")
        assertEquals(2, test.onAllNodesWithText("1 to review").fetchSemanticsNodes().size, "the course and its unit")
        click("Present simple")
        text("To review").assertIsDisplayed()
        click("Continue →")
        text("Review").assertIsDisplayed()
        screenshot("exercise-review")
    }

    @Test fun aFlatCourseListsItsTopicsWithTheAddTopicStub() = appTest(seed = {
        it.seedCourse("""{ "formatVersion": 1, "name": "Irregular verbs", "theme": { "accent": "#9A4B12" },
            "topics": [
              { "title": "be — was / were — been", "exercises": [ { "type": "gaps", "items": [ { "text": "I {{was}} here." } ] } ] },
              { "title": "begin — began — begun", "exercises": [ { "type": "gaps", "items": [ { "text": "It {{began}}." } ] } ] } ] }""")
    }) {
        click("Irregular verbs")
        text("＋ Add topic").assertIsDisplayed()
        text("Topics").assertIsDisplayed()
        screenshot("course-flat")
        click("be — was / were — been")
        fill(1, "was")
        click("Check")
        click("Next →")
        click("Courses")
        click("Irregular verbs")
        text("Learning").assertIsDisplayed()
    }

    @Test fun theDailyLimitEndsTheDayEvenWithNewTopicsLeft() = appTest(seed = {
        it.seedCourse("""{ "formatVersion": 1, "name": "Colours", "settings": { "newPerDay": 1 },
            "rules": { "mastery": { "type": "consecutive", "learning": 1, "review": 1 } },
            "topics": [
              { "title": "red", "exercises": [ { "type": "gaps", "items": [ { "text": "{{rojo}}" } ] } ] },
              { "title": "green", "exercises": [ { "type": "gaps", "items": [ { "text": "{{verde}}" } ] } ] } ] }""")
    }) {
        text("1 new for today").assertIsDisplayed()
        click("Colours")
        click("Continue →")
        fill(1, "rojo")
        click("Check")
        click("Next →")
        text("That’s all for today").assertIsDisplayed()
        click("Courses")
        text("✓ Done for today").assertIsDisplayed()
        text("1 of 2 learned").assertIsDisplayed()
        screenshot("course-list-limit-done")
    }

    @Test fun aBlankTakesAtMostFortyCharacters() = appTest(seed) {
        click("Grammar in Use")
        click("Present simple")
        click("Continue →")
        test.onNode(hasContentDescription("Blank 1")).performTextInput("x".repeat(45))
        test.waitForIdle()
        test.onNode(hasContentDescription("Blank 1") and hasText("x".repeat(40), substring = false)).assertExists()
        screenshot("blank-full")
    }
}

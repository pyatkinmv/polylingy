package com.polylingy.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.polylingy.app.AppContainer
import com.polylingy.content.Response
import com.polylingy.core.content.GradingSpec
import com.polylingy.core.content.MasterySpec
import com.polylingy.core.content.PickerSpec
import com.polylingy.core.content.Preset
import com.polylingy.core.content.RulesSpec
import com.polylingy.core.content.SchedulerSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The study settings of a course; see docs/testing.md and docs/learning-model.md, section 7.
class SettingsUiTest {
    private val course = """{ "formatVersion": 2, "name": "Irregular verbs",
        "topics": [
          { "title": "be", "exercises": [ { "type": "gaps", "items": [ { "text": "I {{was}} there." } ] } ] },
          { "title": "go", "exercises": [ { "type": "gaps", "items": [ { "text": "She {{went}} home." } ] } ] },
          { "title": "see", "exercises": [ { "type": "gaps", "items": [ { "text": "We {{saw}} it." } ] } ] } ] }"""

    private val seed: (AppContainer) -> Unit = { it.seedCourse(course) }

    /** "be" was answered right once: the ladder wants three in a row, so it is still being learned. */
    private val answeredOnce: (AppContainer) -> Unit = { container ->
        val c = container.seedCourse(course)
        val be = c.topics[0]
        container.study.answer(c.id, be.id, be.exercises[0].id, Response.Blanks(listOf("was")))
    }

    private fun AppHarness.stored() = container.storage.courses.all().single { it.name == "Irregular verbs" }

    private fun AppHarness.settings() = container.storage.courseSettings.get(stored().id)!!

    private fun AppHarness.open() {
        click("Irregular verbs")
        click("Study settings")
        text("Fill in questions 1–3 with a ready set").assertIsDisplayed()
    }

    private fun AppHarness.step(label: String) {
        test.onNode(hasContentDescription(label)).performScrollTo().performClick()
        test.waitForIdle()
    }

    /** Clicks [text] on the page, scrolling to it first; the bar on top doesn't scroll. */
    private fun AppHarness.tap(text: String) {
        val node = test.onNodeWithText(text)
        runCatching { node.performScrollTo() }
        node.performClick()
        test.waitForIdle()
    }

    private fun AppHarness.see(text: String, substring: Boolean = false) =
        test.onNodeWithText(text, substring = substring).performScrollTo().assertIsDisplayed()

    @Test fun theSettingsOpenFromTheCourseAndFromItsMenu() = appTest(seed) {
        openMenu(0)
        text("Study settings…").assertIsEnabled()
        screenshot("settings-menu")
        click("Study settings…")
        text("Your answers 1–3 match “Spaced review”.").assertIsDisplayed()
        text("Save").assertIsNotEnabled()
        screenshot("settings")
        click("Cancel")
        text("Courses").assertIsDisplayed()

        click("Irregular verbs")
        click("Study settings")
        see("Nothing that affects progress has changed. Question 4 and the daily limit never recount progress.")
        screenshot("settings-bottom")
    }

    @Test fun aReadySetFillsInTheFirstThreeQuestionsAndProgressIsRecounted() = appTest(answeredOnce, height = 1100) {
        open()
        click("Quick pass")
        text("Your answers 1–3 match “Quick pass”.").assertIsDisplayed()
        see("Questions 1–3 changed", substring = true)
        see("learned 0 → 1", substring = true)
        see("Done today → never offered by itself again; you can still open it and practise.")
        screenshot("settings-quick-pass")
        tap("Save")

        assertEquals(SchedulerSpec.Once, settings().scheduler)
        assertEquals(MasterySpec.Consecutive(1, 1), settings().mastery)
        assertTrue(test.onAllNodes(hasText("1 of 3 learned", substring = true)).fetchSemanticsNodes().isNotEmpty(), "the course page counts with the new rules")
        screenshot("settings-saved-course")
    }

    @Test fun eachQuestionCanBeChangedAndItsExampleFollows() = appTest(seed, height = 1400) {
        open()
        tap("Most items right")
        step("Share of items, more")
        see("Example: 5 of 6 right (83%) → counts; 1 of 2 right (50%) → doesn’t count.")
        tap("Forgive one typo per item")
        see("“is wroking” for “is working” → wrong.")

        step("First time in a row, more")
        see("First time: ✗ ✓ ✓ ✓ ✓ → done on answer 5; a ✗ starts the count again.")
        step("Review in a row, more")
        see("Your answers 1–3 are your own — no set matches. That’s fine.")

        tap("After the last step — learned for good (otherwise the gap doubles each time)")
        see("Done today, 15 Jan → 16 Jan → 19 Jan → 26 Jan → 9 Feb → 11 Mar → 10 May → …")
        screenshot("settings-custom")

        tap("Alternate")
        see("Another topic after every answer", substring = true)
        tap("No limit")
        step("New topics a day, fewer")
        screenshot("settings-custom-bottom")
        tap("Save")

        val saved = settings()
        assertEquals(GradingSpec.Threshold(0.8, typos = false), saved.grading)
        assertEquals(MasterySpec.Consecutive(4, 2), saved.mastery)
        assertEquals(SchedulerSpec.Ladder(retire = false), saved.scheduler)
        assertEquals(PickerSpec.Alternate(newPerDay = 9), saved.picker)
        assertTrue(test.onAllNodes(hasText("9 new a day", substring = true)).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun growingGapsShowsTheGapsOfSm2() = appTest(seed, height = 1100) {
        open()
        click("Lifelong review")
        see("Done today, 15 Jan → 18 Jan → 26 Jan → 17 Feb → 22 Apr → …")
        see("Gaps of 3, 8, 22, 64 days… Mistakes make them shorter.")
        see("Done today, 15 Jan → 18 Jan", substring = true)
        screenshot("settings-sm2")
        tap("It doesn’t")
        see("Your answers 1–3 are your own — no set matches. That’s fine.")
    }

    @Test fun theOrderAndTheLimitNeverRecountProgress() = appTest(answeredOnce) {
        open()
        tap("Shuffle")
        step("New topics a day, more")
        see("Nothing that affects progress has changed. Question 4 and the daily limit never recount progress.")
        tap("Save")
        assertEquals(PickerSpec.Shuffle(newPerDay = 11), settings().picker)
        assertEquals(RulesSpec(Preset.LADDER).resolved().mastery, settings().mastery)
    }

    @Test fun cancellingChangesAsksFirst() = appTest(seed) {
        open()
        click("Quick pass")
        click("Cancel")
        text("Discard changes?").assertIsDisplayed()
        screenshot("settings-discard")
        click("Discard")
        text("Study settings").assertIsDisplayed()
        assertEquals(SchedulerSpec.Ladder(), settings().scheduler, "nothing saved")
    }

    @Test fun aTypoNotForgivenIsShownAsWrongButKeptAsATypo() = appTest(seed) {
        open()
        tap("Forgive one typo per item")
        tap("Save")
        val c = stored()
        val go = c.topics[1]
        val outcome = container.study.answer(c.id, go.id, go.exercises[0].id, Response.Blanks(listOf("wnet")))
        assertEquals(com.polylingy.content.ItemResult.WRONG, outcome.checks.single().result)
        val journal = container.storage.journal.all().single().event as com.polylingy.core.journal.AnswerSubmitted
        assertEquals(listOf(com.polylingy.content.ItemResult.TYPO), journal.results)
    }

    @Test fun aCourseFromANewerVersionHasNoSettings() = appTest({ it.seedCourse(course.replace("\"formatVersion\": 2", "\"formatVersion\": 3")) }) {
        click("Irregular verbs")
        assertTrue(test.onAllNodesWithText("Study settings").fetchSemanticsNodes().isEmpty())
        click("Courses")
        openMenu(0)
        text("Study settings…").assertIsNotEnabled()
    }
}

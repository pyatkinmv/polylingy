package com.polylingy.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.polylingy.app.MovableClock
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The course-list part of the parity checklist in docs/migration-plan.md.
class CourseListUiTest {
    private val topicJson = """{"subject": "Greetings", "exercises": [{"task": "hello", "answer": "hola"}]}"""

    @Test fun firstLaunchShowsTheSampleCourseWithCounters() = appTest(seed = { it.seedSample() }) {
        text("Spanish Basics").assertIsDisplayed()
        text("4 new · 0 to review · 0 done today").assertIsDisplayed()
        text("Start").assertIsEnabled()
        screenshot("course-list")
    }

    @Test fun coursesAreListedOldestFirst() = appTest(seed = {
        val clock = it.clock as MovableClock
        clock.now = LocalDateTime(2026, 1, 10, 12, 0)
        it.courseService.create("Newer")
        clock.now = LocalDateTime(2026, 1, 5, 12, 0)
        it.courseService.create("Older")
    }) {
        val older = text("Older").fetchSemanticsNode().boundsInRoot.top
        val newer = text("Newer").fetchSemanticsNode().boundsInRoot.top
        assertTrue(older < newer, "Older course should be above the newer one")
    }

    @Test fun startIsDisabledWhenThereIsNothingToStudy() = appTest(seed = { it.courseService.create("Empty") }) {
        text("0 new · 0 to review · 0 done today").assertIsDisplayed()
        text("Start").assertIsNotEnabled()
    }

    @Test fun newCourseNeedsANameOfAtMostFiftyCharacters() = appTest {
        click("+ New course")
        text("OK").assertIsNotEnabled()
        text("Course name").performTextInput("x".repeat(60))
        text("50/50").assertIsDisplayed()
        screenshot("new-course-dialog")

        text("Course name").performTextClearance()
        text("Course name").performTextInput("French")
        click("OK")
        text("French").assertIsDisplayed()
    }

    @Test fun renameCourse() = appTest(seed = { it.courseService.create("Old name") }) {
        menu("Rename course")
        text("Course name").performTextClearance()
        text("Course name").performTextInput("New name")
        click("OK")
        text("New name").assertIsDisplayed()
        text("Old name").assertDoesNotExist()
    }

    @Test fun removeCourseAsksFirst() = appTest(seed = { it.courseService.create("Doomed") }) {
        menu("Remove course")
        text("Are you sure you want to remove \"Doomed\"? This cannot be undone.").assertIsDisplayed()
        click("Cancel")
        text("Doomed").assertIsDisplayed()

        menu("Remove course")
        click("OK")
        text("Doomed").assertDoesNotExist()
    }

    @Test fun addTopicShowsWhatIsWrongThenAddsAValidTopic() = appTest(seed = { it.courseService.create("Spanish") }) {
        menu("Add topic")
        text("Topic JSON").performTextInput("""{"subject": "x", "exercises": []}""")
        click("OK")
        text("topic.exercises: a topic needs at least one exercise").assertIsDisplayed()
        screenshot("add-topic-error")

        text("Topic JSON").performTextClearance()
        text("Topic JSON").performTextInput(topicJson)
        click("OK")
        text("1 new · 0 to review · 0 done today").assertIsDisplayed()
    }

    @Test fun importReadsTheChosenFile() = appTest {
        files.toOpen = com.polylingy.client.DesktopStorage.sampleCourseJson()
        click("Import")
        text("Spanish Basics").assertIsDisplayed()
    }

    @Test fun importErrorIsShownInADialog() = appTest {
        files.toOpen = "[]"
        click("Import")
        text("Couldn't import course").assertIsDisplayed()
        text("course: expected a JSON object").assertIsDisplayed()
        click("OK")
        text("Couldn't import course").assertDoesNotExist()
    }

    @Test fun exportSavesTheCourseFile() = appTest(seed = { it.seedSample() }) {
        menu("Export course")
        val (name, json) = files.saved!!
        assertEquals("spanish_basics.json", name)
        assertContains(json, "\"subject\": \"Greetings\"")
    }
}

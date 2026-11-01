package com.polylingy.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.polylingy.app.APP_VERSION
import com.polylingy.app.MovableClock
import com.polylingy.client.CourseArchive
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Course list scenarios; see docs/testing.md.
class CourseListUiTest {
    private fun flat(name: String) = """{ "formatVersion": 1, "name": "$name",
        "topics": [ { "title": "Greetings", "exercises": [ { "type": "gaps", "items": [ { "text": "{{Hola}}!" } ] } ] } ] }"""

    @Test fun firstLaunchShowsTheSampleCourse() = appTest(seed = { it.seedSample() }) {
        text("English Grammar Sampler").assertIsDisplayed()
        text("A short sample course: one unit with a page of theory and one exercise of every kind.").assertIsDisplayed()
        text("1 unit").assertIsDisplayed()
        screenshot("course-list")
    }

    @Test fun appVersionIsShownOnTheCourseList() = appTest {
        text("v$APP_VERSION").assertIsDisplayed()
    }

    @Test fun coursesAreListedOldestFirst() = appTest(seed = {
        val clock = it.clock as MovableClock
        clock.now = LocalDateTime(2026, 1, 10, 12, 0)
        it.seedCourse(flat("Newer"))
        clock.now = LocalDateTime(2026, 1, 5, 12, 0)
        it.seedCourse(flat("Older"))
    }) {
        val older = text("Older").fetchSemanticsNode().boundsInRoot.top
        val newer = text("Newer").fetchSemanticsNode().boundsInRoot.top
        assertTrue(older < newer, "Older course should be above the newer one")
        assertEquals(2, test.onAllNodesWithText("1 topic").fetchSemanticsNodes().size)
    }

    @Test fun renameCourse() = appTest(seed = { it.seedCourse(flat("Old name")) }) {
        menu("Rename course")
        text("Course name").performTextClearance()
        text("Course name").performTextInput("New name")
        click("OK")
        text("New name").assertIsDisplayed()
        text("Old name").assertDoesNotExist()
    }

    @Test fun removeCourseAsksFirst() = appTest(seed = { it.seedCourse(flat("Doomed")) }) {
        menu("Remove course")
        text("Are you sure you want to remove \"Doomed\"? This cannot be undone.").assertIsDisplayed()
        click("Cancel")
        text("Doomed").assertIsDisplayed()

        menu("Remove course")
        click("OK")
        text("Doomed").assertDoesNotExist()
    }

    @Test fun importReadsTheChosenFile() = appTest {
        files.open("spanish.json", flat("Spanish Basics"))
        click("Import")
        text("Spanish Basics").assertIsDisplayed()
    }

    @Test fun importingTheSameCourseAgainAddsANumberedCopy() = appTest(seed = { it.seedCourse(flat("Spanish")) }) {
        files.open("spanish.json", flat("Spanish"))
        click("Import")
        text("Spanish (2)").assertIsDisplayed()
    }

    @Test fun importListsEveryProblemOfTheFile() = appTest {
        files.open("broken.json", """{ "formatVersion": 1, "name": "",
            "topics": [ { "title": "T", "exercises": [ { "type": "gaps", "items": [ { "text": "no blank" } ] } ] } ] }""")
        click("Import")
        text("Couldn't import broken.json").assertIsDisplayed()
        text("• name: must not be empty\n• topics[0].exercises[0].items[0].text: needs at least one blank {{…}}").assertIsDisplayed()
        screenshot("import-errors")
        click("OK")
        text("Couldn't import broken.json").assertDoesNotExist()
    }

    @Test fun aLongListOfProblemsIsCut() = appTest {
        val items = (1..20).joinToString(",") { """{ "text": "no blank $it" }""" }
        files.open("many.json", """{ "formatVersion": 1, "name": "M", "topics": [ { "title": "T", "exercises": [ { "type": "gaps", "items": [ $items ] } ] } ] }""")
        click("Import")
        test.onNodeWithText("…and 5 more", substring = true).assertIsDisplayed()
    }

    @Test fun aCourseFromANewerVersionIsImportedWithANotice() = appTest {
        files.open("future.json", """{ "formatVersion": 2, "name": "Future",
            "topics": [ { "title": "T", "exercises": [ { "type": "dictation", "audio": "a.mp3" } ] } ] }""")
        click("Import")
        text("Update Polylingy").assertIsDisplayed()
        screenshot("import-newer-version")
        click("OK")
        text("1 topic · made with a newer version").assertIsDisplayed()

        menu("Export course")
        text("Couldn't export \"Future\"").assertIsDisplayed()
    }

    @Test fun aFailedPictureDownloadIsAWarning() = appTest {
        files.open("cat.json", """{ "formatVersion": 1, "name": "Cats",
            "topics": [ { "title": "T", "exercises": [ { "type": "pictures",
              "items": [ { "image": { "src": "https://img.example/cat.png", "alt": "A cat" }, "text": "It {{sleeps}}." } ] } ] } ] }""")
        click("Import")
        text("Imported \"Cats\" with warnings").assertIsDisplayed()
        click("OK")
        text("Cats").assertIsDisplayed()
    }

    @Test fun cancellingTheFileDialogDoesNothing() = appTest {
        click("Import")
        text("Polylingy").assertIsDisplayed()
    }

    @Test fun exportSavesTheSampleAsAZipWithItsPictures() = appTest(seed = { it.seedSample() }) {
        menu("Export course")
        val (name, bytes) = files.saved!!
        assertEquals("english_grammar_sampler.zip", name)
        val source = CourseArchive.read(name, bytes)
        assertContains(source.json, "\"name\": \"English Grammar Sampler\"")
        assertEquals(setOf("images/cycling.png", "images/cooking.png", "images/swimming.png"), source.files.keys)
    }
}

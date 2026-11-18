package com.polylingy.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.polylingy.app.APP_VERSION
import com.polylingy.app.MovableClock
import com.polylingy.client.CourseArchive
import com.polylingy.client.DesktopStorage
import kotlinx.datetime.LocalDateTime
import java.nio.file.Files
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
        text("6 new").assertIsDisplayed()
        text("0 of 6 learned").assertIsDisplayed()
        text("0%").assertIsDisplayed()
        screenshot("course-list")
    }

    @Test fun appVersionIsShownOnTheCourseList() = appTest {
        text(APP_VERSION).assertIsDisplayed()
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
        assertEquals(2, test.onAllNodesWithText("0 of 1 learned").fetchSemanticsNodes().size)
    }

    @Test fun hoveringACourseOpensNoPopup() = appTest(seed = { it.seedSample() }) {
        text("English Grammar Sampler").performMouseInput { moveTo(center) }
        test.waitForIdle()
        assertEquals(1, test.onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes().size, "only the window, no popup")
        screenshot("course-list-hover")
    }

    @Test fun cardsShowWhatIsLeftForTodayUnderTheDailyLimit() = appTest(seed = {
        it.seedCourse(flat("No limit"))
        it.seedCourse("""{ "formatVersion": 1, "name": "German A1", "settings": { "newPerDay": 2 },
            "topics": [ ${(1..5).joinToString(",") { n -> """{ "title": "T$n", "exercises": [ { "type": "gaps", "items": [ { "text": "{{a$n}}" } ] } ] }""" }} ] }""")
    }) {
        text("1 new").assertIsDisplayed()
        text("2 new for today").assertIsDisplayed()
        text("0 of 5 learned").assertIsDisplayed()
        screenshot("course-list-limit")
    }

    @Test fun editNameAndDescription() = appTest(seed = { it.seedCourse(flat("Old name")) }) {
        menu("Edit name and description…")
        screenshot("course-edit-details")
        test.onNode(hasSetTextAction() and hasText("Old name")).performTextClearance()
        test.onNode(hasSetTextAction() and hasText("Name")).performTextInput("New name")
        test.onNode(hasSetTextAction() and hasText("Description")).performTextInput("Words for *every* day.")
        click("Save")
        text("New name").assertIsDisplayed()
        text("Words for every day.").assertIsDisplayed()
        text("Old name").assertDoesNotExist()
    }

    @Test fun badMarkupInTheDescriptionIsShownInTheDialog() = appTest(seed = { it.seedCourse(flat("Spanish")) }) {
        menu("Edit name and description…")
        test.onNode(hasSetTextAction() and hasText("Description")).performTextInput("**open")
        click("Save")
        text("** at 1 is not closed").assertIsDisplayed()
    }

    @Test fun removeCourseAsksFirst() = appTest(seed = { it.seedCourse(flat("Doomed")) }) {
        menu("Remove")
        text("Are you sure you want to remove \"Doomed\"? This cannot be undone.").assertIsDisplayed()
        click("Cancel")
        text("Doomed").assertIsDisplayed()

        menu("Remove")
        click("OK")
        text("Doomed").assertDoesNotExist()
    }

    @Test fun importReadsTheChosenFile() = appTest {
        files.open("spanish.json", flat("Spanish Basics"))
        click("＋ Import")
        text("Spanish Basics").assertIsDisplayed()
    }

    private fun versioned(name: String, version: Int, id: String = "7d0b0e5a-1c2f-4e57-9b64-0f3a8e2d1c10") = """{ "formatVersion": 12, "id": "$id", "name": "$name", "courseVersion": $version,
        "topics": [ { "id": "8e1c1f6b-2d30-4f68-8c75-1a4b9f3e2d21", "title": "Greetings", "exercises": [ { "type": "gaps", "items": [ { "text": "{{Hola}}!" } ] } ] } ] }"""

    @Test fun aNewerFileUpdatesTheCourseAfterAskingFirst() = appTest(seed = { it.seedCourse(versioned("Spanish", 1)) }) {
        files.open("spanish.json", versioned("Spanish, second edition", 2))
        menu("Update from file…")
        text("Update course").assertIsDisplayed()
        screenshot("course-update-confirm")
        click("OK")
        text("Updated \"Spanish, second edition\"").assertIsDisplayed()
        text("Now at version 2.").assertIsDisplayed()
        screenshot("course-update-done")
        click("OK")
        text("Spanish, second edition").assertIsDisplayed()
        text("Spanish").assertDoesNotExist()
    }

    @Test fun aFileThatIsNotNewerIsRefusedWithTheReason() = appTest(seed = { it.seedCourse(versioned("Spanish", 3)) }) {
        files.open("spanish.json", versioned("Spanish again", 3))
        menu("Update from file…")
        click("OK")
        text("Couldn't update \"Spanish\" from spanish.json").assertIsDisplayed()
        test.onNodeWithText("already at 3", substring = true).assertIsDisplayed()
        screenshot("course-update-refused")
        click("OK")
        text("Spanish").assertIsDisplayed()
    }

    @Test fun cancellingTheUpdateChangesNothing() = appTest(seed = { it.seedCourse(versioned("Spanish", 1)) }) {
        menu("Update from file…")
        click("Cancel")
        text("Update course").assertDoesNotExist()
        text("Spanish").assertIsDisplayed()
    }

    @Test fun importingTheSameCourseAgainAddsANumberedCopy() = appTest(seed = { it.seedCourse(flat("Spanish")) }) {
        files.open("spanish.json", flat("Spanish"))
        click("＋ Import")
        text("Spanish (2)").assertIsDisplayed()
    }

    @Test fun importListsEveryProblemOfTheFile() = appTest {
        files.open("broken.json", """{ "formatVersion": 1, "name": "",
            "topics": [ { "title": "T", "exercises": [ { "type": "gaps", "items": [ { "text": "no blank" } ] } ] } ] }""")
        click("＋ Import")
        text("Couldn't import broken.json").assertIsDisplayed()
        text("• name: must not be empty\n• topics[0].exercises[0].items[0].text: needs at least one blank {{…}}").assertIsDisplayed()
        screenshot("import-errors")
        click("OK")
        text("Couldn't import broken.json").assertDoesNotExist()
    }

    @Test fun aLongListOfProblemsIsCut() = appTest {
        val items = (1..20).joinToString(",") { """{ "text": "no blank $it" }""" }
        files.open("many.json", """{ "formatVersion": 1, "name": "M", "topics": [ { "title": "T", "exercises": [ { "type": "gaps", "items": [ $items ] } ] } ] }""")
        click("＋ Import")
        test.onNodeWithText("…and 5 more", substring = true).assertIsDisplayed()
    }

    @Test fun aCourseFromANewerVersionIsImportedWithANotice() = appTest {
        files.open("future.json", """{ "formatVersion": 13, "name": "Future",
            "topics": [ { "title": "T", "exercises": [ { "type": "dictation", "audio": "a.mp3" } ] } ] }""")
        click("＋ Import")
        text("\"Future\" was made with a newer Polylingy").assertIsDisplayed()
        screenshot("import-newer-version")
        click("OK")
        text("Made in a newer Polylingy · read-only").assertIsDisplayed()

        openMenu()
        text("Export").assertIsNotEnabled()
        text("Edit name and description…").assertIsNotEnabled()
        text("Update Polylingy to edit or export this course").assertIsDisplayed()
        screenshot("course-menu-newer-version")
    }

    @Test fun aFailedPictureDownloadIsAWarning() = appTest {
        files.open("cat.json", """{ "formatVersion": 1, "name": "Cats",
            "topics": [ { "title": "T", "exercises": [ { "type": "pictures",
              "items": [ { "image": { "src": "https://img.example/cat.png", "alt": "A cat" }, "text": "It {{sleeps}}." } ] } ] } ] }""")
        click("＋ Import")
        text("Imported \"Cats\" with warnings").assertIsDisplayed()
        click("OK")
        text("Cats").assertIsDisplayed()
    }

    @Test fun cancellingTheFileDialogDoesNothing() = appTest {
        click("＋ Import")
        text("Polylingy").assertIsDisplayed()
    }

    @Test fun exportSavesTheSampleAsAZipWithItsPictures() = appTest(seed = { it.seedSample() }) {
        menu("Export")
        val (name, bytes) = files.saved!!
        assertEquals("english_grammar_sampler.zip", name)
        val source = CourseArchive.read(name, bytes)
        assertContains(source.json, "\"name\": \"English Grammar Sampler\"")
        assertEquals(setOf("images/cycling.png", "images/cooking.png", "images/swimming.png"), source.files.keys)
    }

    @Test fun aStoredCourseThatNoLongerReadsIsShownAndCanBeRemoved() {
        val dir = Files.createTempDirectory("polylingy-ui")
        DesktopStorage.open(dir).use { storage ->
            val course = com.polylingy.core.content.CourseJson.read(flat("Old format"), kotlin.time.Clock.System.now()).course
            storage.courses.save(course.copy(revision = 1))
        }
        java.sql.DriverManager.getConnection("jdbc:sqlite:${dir.resolve("polylingy.db")}").use {
            it.createStatement().executeUpdate("UPDATE course_document SET body = replace(body, '\"topics\"', '\"topix\"')")
        }
        appTest(storage = { DesktopStorage.open(dir) }) {
            text("Old format").assertIsDisplayed()
            text("Can't open this course").assertIsDisplayed()
            screenshot("course-list-broken")
            menu("Remove")
            click("OK")
            text("Old format").assertDoesNotExist()
        }
        dir.toFile().deleteRecursively()
    }
}

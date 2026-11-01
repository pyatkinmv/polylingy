package com.polylingy.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.polylingy.client.sampleCourse
import com.polylingy.content.Content
import com.polylingy.content.Exercise
import com.polylingy.content.ItemCheck
import com.polylingy.content.ItemResult
import com.polylingy.content.Response
import com.polylingy.content.compose.ContentColors
import com.polylingy.content.compose.ContentTheme
import com.polylingy.content.compose.ContentView
import com.polylingy.content.compose.ExerciseView
import com.polylingy.content.compose.ImageLoader
import com.polylingy.content.emptyResponse
import com.polylingy.core.content.CourseJson
import com.polylingy.core.rules.CheckSettings
import com.polylingy.core.rules.Checking
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

/**
 * Every exercise type and the theory page of the sample course, drawn by content-compose outside the app:
 * before checking, after checking and on a narrow window. See docs/testing.md.
 */
class ShowcaseUiTest {
    private val sample = sampleCourse()
    private val course = CourseJson.read(sample.json, Clock.System.now()).course
    private val exercises = course.allTopics.flatMap { it.exercises }
    private val images = ImageLoader { src -> sample.files[src]?.let { Image.makeFromEncoded(it).toComposeImageBitmap() } }

    private inline fun <reified E : Exercise> exercise(): E = exercises.filterIsInstance<E>().single()

    /** The exercise with a Check button; [checked] receives the result of every check. */
    private fun showcase(
        exercise: Exercise,
        width: Int = 832,
        accent: String? = null,
        checked: (List<ItemCheck>) -> Unit = {},
        block: ComposeUiTest.() -> Unit,
    ) = runDesktopComposeUiTest(width = width, height = 900) {
        setContent {
            var response by remember { mutableStateOf(exercise.emptyResponse()) }
            var checks by remember { mutableStateOf<List<ItemCheck>?>(null) }
            val check = { checks = Checking.check(exercise, response, CheckSettings()).also(checked) }
            MaterialTheme {
                ContentTheme(ContentColors.forAccent(accent), images) {
                    Column(
                        Modifier.fillMaxSize().background(Color(0xFFFAF6EC)).verticalScroll(rememberScrollState()).padding(24.dp).widthIn(max = 720.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        ExerciseView(exercise, response, checks, { response = it }, onSubmit = check, seed = 3)
                        if (checks == null) Button(onClick = check) { Text("Check") }
                    }
                }
            }
        }
        block()
    }

    private fun ComposeUiTest.type(blank: Int, text: String) = onNodeWithContentDescription("Blank $blank").performTextInput(text)
    private fun ComposeUiTest.click(text: String) = onNodeWithText(text).performClick().also { waitForIdle() }

    @Test fun gaps() {
        var result: List<ItemCheck>? = null
        showcase(exercise<Exercise.Gaps>(), checked = { result = it }) {
            type(1, "is singing")
            type(2, "are")
            type(3, "wearign")
            type(4, "is getting")
            type(5, "don’t read")
            screenshot("ex-gaps")
            click("Check")
            screenshot("ex-gaps-checked")
            onNodeWithText("Negative: am / is / are + not + -ing.").assertIsDisplayed()
            onNodeWithText("Explanation").assertIsDisplayed()
        }
        assertEquals(listOf(ItemResult.CORRECT, ItemResult.CORRECT, ItemResult.TYPO, ItemResult.CORRECT, ItemResult.WRONG), result!!.map { it.result })
    }

    @Test fun tabMovesToTheNextBlankAndEnterChecks() {
        var result: List<ItemCheck>? = null
        showcase(exercise<Exercise.Gaps>(), checked = { result = it }) {
            type(1, "is singing")
            onNodeWithContentDescription("Blank 1").performKeyInput { pressKey(Key.Tab) }
            waitForIdle()
            onNodeWithContentDescription("Blank 2").performTextInput("are")
            onNodeWithContentDescription("Blank 2").performKeyInput { pressKey(Key.Enter) }
            waitForIdle()
        }
        assertEquals(ItemResult.CORRECT, result!![1].result)
    }

    @Test fun gapsOnANarrowWindow() = showcase(exercise<Exercise.Gaps>(), width = 480) {
        type(1, "is singing")
        screenshot("ex-gaps-narrow")
    }

    @Test fun choice() {
        var result: List<ItemCheck>? = null
        showcase(exercise<Exercise.Choice>(), checked = { result = it }) {
            click("’s raining")
            click("’m not understanding")
            click("Do you go")
            screenshot("ex-choice")
            click("Check")
            screenshot("ex-choice-checked")
        }
        assertEquals(listOf(ItemResult.CORRECT, ItemResult.WRONG, ItemResult.CORRECT), result!!.map { it.result })
    }

    @Test fun match() {
        var result: List<ItemCheck>? = null
        showcase(exercise<Exercise.Match>(), checked = { result = it }) {
            click("I’m tired,"); click("so I’m going to bed.")
            click("Be quiet!"); click("It rains a lot here.")
            click("Where’s Dan?"); click("He’s having a shower.")
            click("She’s learning Italian")
            screenshot("ex-match")
            click("because she’s moving to Rome.")
            click("Check")
            screenshot("ex-match-checked")
        }
        assertEquals(listOf(ItemResult.CORRECT, ItemResult.WRONG, ItemResult.CORRECT, ItemResult.CORRECT), result!!.map { it.result })
    }

    @Test fun clickingAMatchedLeftSideUndoesThePair() {
        var result: List<ItemCheck>? = null
        showcase(exercise<Exercise.Match>(), checked = { result = it }) {
            click("I’m tired,"); click("The baby is sleeping.")
            click("I’m tired,")
            click("Check")
        }
        assertEquals(ItemResult.WRONG, result!![0].result)
        assertEquals("so I’m going to bed.", result!![0].rightAnswer)
    }

    @Test fun order() {
        var result: List<ItemCheck>? = null
        showcase(exercise<Exercise.Order>(), checked = { result = it }) {
            for (word in listOf("What", "are", "you")) click(word)
            screenshot("ex-order")
            for (word in listOf("doing", "tonight")) click(word)
            for (word in listOf("Is", "it", "now", "raining")) click(word)
            click("Check")
            screenshot("ex-order-checked")
        }
        assertEquals(listOf(ItemResult.CORRECT, ItemResult.CORRECT), result!!.map { it.result })
    }

    @Test fun clickingAPlacedWordTakesItBack() {
        var result: List<ItemCheck>? = null
        showcase(exercise<Exercise.Order>(), checked = { result = it }) {
            click("tonight")
            click("tonight")
            for (word in listOf("What", "are", "you", "doing", "tonight")) click(word)
            click("Check")
        }
        assertEquals(ItemResult.CORRECT, result!![0].result)
    }

    @Test fun table() {
        var result: List<ItemCheck>? = null
        showcase(exercise<Exercise.Table>(), checked = { result = it }) {
            listOf("am", "Am", "is", "Are", "are", "Are").forEachIndexed { i, text -> type(i + 1, text) }
            screenshot("ex-table")
            click("Check")
            screenshot("ex-table-checked")
        }
        assertEquals(listOf(true, true, true, false, true, true), result!!.map { it.result == ItemResult.CORRECT })
    }

    @Test fun picturesWithACourseAccent() {
        var result: List<ItemCheck>? = null
        showcase(exercise<Exercise.Pictures>(), accent = "#2F6B5E", checked = { result = it }) {
            type(1, "’s cycling")
            type(2, "is cookign")
            screenshot("ex-pictures")
            click("Check")
            screenshot("ex-pictures-checked")
        }
        assertEquals(listOf(ItemResult.CORRECT, ItemResult.TYPO, ItemResult.WRONG), result!!.map { it.result })
    }

    @Test fun picturesOnANarrowWindow() = showcase(exercise<Exercise.Pictures>(), width = 480) { screenshot("ex-pictures-narrow") }

    private fun theory(width: Int, name: String) = runDesktopComposeUiTest(width = width, height = 1500) {
        setContent {
            MaterialTheme {
                ContentTheme(ContentColors.forAccent(null), images) {
                    Column(Modifier.fillMaxSize().background(Color(0xFFFAF6EC)).padding(24.dp)) {
                        ContentView(course.units.single().theory as Content)
                    }
                }
            }
        }
        onNodeWithText("Happening now").assertIsDisplayed()
        onNodeWithText("cycling\nworking\ndoing etc.").assertIsDisplayed()
        screenshot(name)
    }

    @Test fun theoryPage() = theory(832, "theory")

    @Test fun theoryPageOnANarrowWindow() = theory(480, "theory-narrow")

    @Test fun unknownContentShowsANotice() = runDesktopComposeUiTest {
        val unknown = com.polylingy.content.ContentJson.decode(Content.serializer(), """{ "format": "blocks", "blocks": [ { "type": "video" } ] }""")
        setContent { MaterialTheme { ContentView(unknown) } }
        onNodeWithText("This part needs a newer version of Polylingy.").assertIsDisplayed()
    }

    @Test fun aMissingPictureShowsItsDescription() = runDesktopComposeUiTest {
        setContent { MaterialTheme { ContentView(course.units.single().theory as Content) } }
        onNodeWithText("A man cycling to work").assertIsDisplayed()
    }

    @Test fun responsesKeepTheirShape() {
        assertEquals(Response.Blanks(List(6) { "" }), exercise<Exercise.Table>().emptyResponse())
    }
}

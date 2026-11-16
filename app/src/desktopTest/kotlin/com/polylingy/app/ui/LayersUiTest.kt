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
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.polylingy.client.sampleCourse
import com.polylingy.content.ContentJson
import com.polylingy.content.Exercise
import com.polylingy.content.ItemCheck
import com.polylingy.content.ItemResult.CORRECT
import com.polylingy.content.ItemResult.TYPO
import com.polylingy.content.ItemResult.WRONG
import com.polylingy.content.compose.ContentColors
import com.polylingy.content.compose.ContentTheme
import com.polylingy.content.compose.ExerciseView
import com.polylingy.content.compose.ImageLoader
import com.polylingy.content.emptyResponse
import com.polylingy.core.rules.Checking
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The layers of an exercise (look, aside, word bank around the body) on several types, and the types expand and fix,
 * before and after checking. See docs/content-format.md, section 5.
 */
class LayersUiTest {
    private val sample = sampleCourse()
    private val images = ImageLoader { src -> sample.files[src]?.let { Image.makeFromEncoded(it).toComposeImageBitmap() } }

    private fun exercise(json: String) = ContentJson.decode(Exercise.serializer(), json)

    private fun showcase(
        exercise: Exercise, width: Int = 832, checked: (List<ItemCheck>) -> Unit = {},
        uri: androidx.compose.ui.platform.UriHandler? = null, block: ComposeUiTest.() -> Unit,
    ) =
        runDesktopComposeUiTest(width = width, height = 900) {
            setContent {
                var response by remember { mutableStateOf(exercise.emptyResponse()) }
                var checks by remember { mutableStateOf<List<ItemCheck>?>(null) }
                val check = { checks = Checking.check(exercise, response).also(checked) }
                MaterialTheme {
                    ContentTheme(ContentColors.forAccent(null), images) {
                        androidx.compose.runtime.CompositionLocalProvider(
                            *listOfNotNull(uri?.let { androidx.compose.ui.platform.LocalUriHandler provides it }).toTypedArray(),
                        ) {
                            Column(
                                Modifier.fillMaxSize().background(Color(0xFFFAF6EC)).verticalScroll(rememberScrollState()).padding(24.dp).widthIn(max = 760.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                ExerciseView(exercise, response, checks, { response = it }, onSubmit = check, seed = 3)
                                if (checks == null) Button(onClick = check) { Text("Check") }
                            }
                        }
                    }
                }
            }
            block()
        }

    private fun ComposeUiTest.type(field: String, text: String) = onNodeWithContentDescription(field).performTextInput(text)
    private fun ComposeUiTest.click(text: String) = onNodeWithText(text).performClick().also { waitForIdle() }

    private val email = exercise("""
        { "id": "6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d", "type": "gaps",
          "instruction": "Complete the email. Past simple or past continuous.",
          "look": { "type": "email", "from": "Maya", "to": "Theo", "subject": "The strangest commute ever" },
          "items": [ { "text": "You won’t believe my morning. I {{was waiting}} (wait) for the tram when a man next to me {{dropped}} (drop) his phone. While he {{was looking}} (look) for it under the bench, a pigeon {{landed}} (land) right on his head.\n\nSee you on Friday,\nMaya" } ] }
    """)

    @Test fun emailLookAroundOneTextOfGaps() {
        var result: List<ItemCheck>? = null
        showcase(email, checked = { result = it }) {
            onNodeWithContentDescription("Email").assertIsDisplayed()
            onNodeWithText("to Theo").assertIsDisplayed()
            assertTrue(onAllNodesWithText("1").fetchSemanticsNodes().isEmpty(), "one item has no number")
            type("Blank 1", "was waiting")
            type("Blank 2", "dropped")
            type("Blank 3", "was lookign")
            screenshot("layers-email")
            type("Blank 4", "was landing")
            click("Check")
            screenshot("layers-email-checked")
        }
        assertEquals(listOf(CORRECT, CORRECT, TYPO, WRONG), result!!.map { it.result })
    }

    private val blog = exercise("""
        { "id": "6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d", "type": "gaps",
          "instruction": "Complete the blog post with the verbs from the box in a suitable tense.",
          "look": { "type": "article", "source": "notes-from-the-road.blog", "title": "Day one on the coast" },
          "aside": { "image": { "src": "images/cycling.png", "alt": "A cyclist", "caption": "My ride this morning" }, "side": "right", "size": "medium" },
          "wordBank": ["arrive", "feel", "wait", "seem"],
          "items": [ { "text": "I {{arrived}} here this morning after a long night bus. I {{felt}} a bit dizzy, so I {{waited|was waiting}} in a café for my room to be ready. The town {{seems}} wonderful already." } ] }
    """)

    @Test fun articleLookWithAPictureAndAWordBank() = showcase(blog) {
        onNodeWithContentDescription("Article").assertIsDisplayed()
        onNodeWithContentDescription("Word bank").assertIsDisplayed()
        onNodeWithContentDescription("Aside").assertIsDisplayed()
        type("Blank 1", "arrived")
        screenshot("layers-article")
    }

    private val clipping = exercise("""
        { "id": "6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d", "type": "choice",
          "instruction": "How many times does each letter appear in the headline?",
          "look": { "type": "newspaper", "source": "Daily News · Tuesday", "headline": "افتتاح المستشفى الجديد\nفي بيروت يوم الثلاثاء" },
          "items": [ { "text": "ب = {{3|2|4}}", "mark": "ب" }, { "text": "ي = {{5|4|6}}", "mark": "ي" } ] }
    """)

    @Test fun aVideoLayerShowsItsCardAboveTheExercise() {
        val opened = mutableListOf<String>()
        val exercise = exercise("""
            { "id": "6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d", "type": "order", "instruction": "Put the greetings in the order you hear them.",
              "video": { "title": "Video: Greetings", "url": "https://www.youtube.com/watch?v=abc123", "note": "Watch it first." },
              "items": [ { "parts": ["Good morning.", "Hello.", "Goodbye."] } ] }
        """)
        showcase(exercise, uri = object : androidx.compose.ui.platform.UriHandler { override fun openUri(uri: String) { opened += uri } }) {
            onNodeWithContentDescription("Video").assertIsDisplayed()
            screenshot("layers-video")
            onNodeWithText("Video: Greetings").performClick()
            assertEquals(listOf("https://www.youtube.com/watch?v=abc123"), opened)
        }
    }

    @Test fun aPassageIsShownAboveTheExerciseAsInTheTheory() = showcase(exercise("""
        { "id": "6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d", "type": "choice", "instruction": "Read the dialogue and choose the right word.",
          "passage": { "format": "blocks", "blocks": [
            { "type": "dialogue", "lines": [ { "who": "Ana", "text": "Where is the tram stop?" }, { "who": "Theo", "text": "It is next to the bank." } ] } ] },
          "items": [ { "text": "The tram stop is {{next to the bank|behind the school}}." } ] }
    """)) {
        onNodeWithText("Where is the tram stop?").assertIsDisplayed()
        onNodeWithText("It is next to the bank.").assertIsDisplayed()
        screenshot("layers-passage")
    }

    @Test fun newspaperLookFramesAHeadline() = showcase(clipping) {
        onNodeWithContentDescription("Newspaper").assertIsDisplayed()
        screenshot("layers-newspaper")
    }

    private val marks = exercise("""
        { "id": "6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d", "type": "choice",
          "instruction": "How many times does each letter appear in the headline?",
          "look": { "type": "newspaper", "headline": "افتتاح المستشفى الجديد\nفي بيروت يوم الثلاثاء" },
          "items": [ { "text": "ب = {{13|12|14}}", "mark": "ب" }, { "text": "ي = {{25|24|26}}", "mark": "ي" } ] }
    """)

    @Test fun aCheckedItemMarksItsPieceInTheFramesText() = showcase(marks) {
        onNodeWithText("12").performClick()
        onNodeWithText("24").performClick()
        click("Check")
        // The first item with a mark is chosen at once.
        screenshot("layers-newspaper-marked")
        // Each item is a button now; choosing the second one moves the marks to its piece.
        onRoot().performMouseInput { click(androidx.compose.ui.geometry.Offset(50f, 308f)) }
        waitForIdle()
        screenshot("layers-newspaper-marked-2")
    }

    @Test fun onANarrowWindowThePictureGoesOnTop() = showcase(blog, width = 480) {
        onNodeWithContentDescription("Aside").assertIsDisplayed()
        screenshot("layers-article-narrow")
    }

    private val choice = exercise("""
        { "id": "6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d", "type": "choice",
          "instruction": "Choose the more likely form in each place.",
          "look": { "type": "email", "from": "Nina", "to": "Uncle Leo", "subject": "Re: How’s the new job?" },
          "items": [ { "text": "Thanks for your message, and I {{apologise|’m apologising}} for not writing sooner. You were right: I {{admit|’m admitting}} the first month was hard. I {{’m considering|consider}} evening classes in accounting." } ] }
    """)

    @Test fun choiceInsideAText() {
        var result: List<ItemCheck>? = null
        showcase(choice, checked = { result = it }) {
            click("apologise")
            click("’m admitting")
            click("’m considering")
            screenshot("layers-choice")
            click("Check")
            screenshot("layers-choice-checked")
        }
        assertEquals(listOf(CORRECT, WRONG, CORRECT), result!!.map { it.result })
    }

    private val expand = exercise("""
        { "id": "6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d", "type": "expand",
          "instruction": "Expand one set of notes to finish each reply.",
          "wordBank": ["constantly / check / phone", "always / borrow / charger", "continually / interrupt / me"],
          "example": "**A** Where are my keys again?\n**B** You’re **forever losing your keys**.",
          "items": [
            { "text": "**A** Sorry — one quick message, then I’m all yours.\n**B** You’re {{constantly checking your phone}}.", "keys": [["constantly", "checking", "phone"]] },
            { "text": "**A** Can I take yours? Mine’s flat.\n**B** You’re {{always borrowing my charger}}.", "keys": [["always", "borrowing", "charger"]],
              "note": "After *always* the verb takes **-ing**." },
            { "text": "**A** Wait — let me just finish my point!\n**B** You’re {{continually interrupting me}}.", "keys": [["continually", "interrupting", "me"]] } ] }
    """)

    @Test fun expandSaysWhyAnAnswerIsWrong() {
        var result: List<ItemCheck>? = null
        showcase(expand, checked = { result = it }) {
            type("Answer 1", "constantly checking the phone")
            type("Answer 2", "always borrow my charger")
            type("Answer 3", "interrupting me continually")
            screenshot("ex-expand")
            click("Check")
            onNodeWithText("Not found: borrowing").assertIsDisplayed()
            onNodeWithText("Wrong order: continually before interrupting").assertIsDisplayed()
            screenshot("ex-expand-checked")
        }
        assertEquals(listOf(CORRECT, WRONG, WRONG), result!!.map { it.result })
    }

    private val fix = exercise("""
        { "id": "6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d", "type": "fix",
          "instruction": "Correct the verbs where they are wrong; keep ✓ where they are right.",
          "aside": { "image": { "src": "images/cooking.png", "alt": "The chef", "caption": "**Chef Ana Ruiz**\nowner of the café" }, "side": "left", "size": "small" },
          "items": [ { "text": "“Over the last ten years I {{have been cooking}} in four countries. I {{have been opening => have opened|’ve opened}} three cafés so far, and in 2021 one of them {{has been winning => won}} a national prize.”" } ] }
    """)

    @Test fun fixKeepsOrFixesEachMarkedWord() {
        var result: List<ItemCheck>? = null
        showcase(fix, checked = { result = it }) {
            type("Fix 2", "have opened")
            screenshot("ex-fix")
            click("Check")
            screenshot("ex-fix-checked")
        }
        assertEquals(listOf(CORRECT, CORRECT, WRONG), result!!.map { it.result })
    }

    @Test fun theTickTakesAFixBack() {
        var result: List<ItemCheck>? = null
        showcase(fix, checked = { result = it }) {
            type("Fix 1", "cooked")
            onNodeWithContentDescription("Keep 1").performClick()
            waitForIdle()
            click("Check")
        }
        assertEquals(CORRECT, result!![0].result)
    }

    private val pairs = exercise("""
        { "id": "6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d", "type": "fix",
          "instruction": "One sentence in each pair is wrong. Correct it.",
          "items": [
            { "text": "a  Once you {{signed => ’ve signed|have signed}} the form, you can’t cancel the order.\nb  Once she {{signed}}, the deal was done." },
            { "text": "a  The minute I {{landed}}, I switched my phone on.\nb  I’ll text you the minute I {{landed => land|’ve landed|have landed}}." } ] }
    """)

    @Test fun fixInPairs() = showcase(pairs) {
        type("Fix 1", "’ve signed")
        screenshot("ex-fix-pairs")
    }
}

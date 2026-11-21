package com.polylingy.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.polylingy.content.Content
import com.polylingy.content.ContentJson
import com.polylingy.content.Exercise
import com.polylingy.content.ItemCheck
import com.polylingy.content.ItemResult.CORRECT
import com.polylingy.content.compose.ContentColors
import com.polylingy.content.compose.ContentTheme
import com.polylingy.content.compose.ContentView
import com.polylingy.content.compose.ExerciseView
import com.polylingy.content.compose.Palette
import com.polylingy.content.compose.Palettes
import com.polylingy.content.emptyResponse
import com.polylingy.core.rules.Checking
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Text of the course can be selected and copied; what is clicked, typed into or only an annotation is left out. */
@OptIn(ExperimentalTestApi::class)
class TextSelectionUiTest {
    private val page = ContentJson.decode(Content.serializer(), """
        { "format": "blocks", "blocks": [
          { "type": "text", "text": "A plain paragraph with **bold** words, one line." },
          { "type": "text", "text": "[老师](lǎoshī)说：[你好](nǐhǎo)！" },
          { "type": "table", "head": ["Word", "Meaning"], "rows": [["cat", "a small animal"], ["dog", "a loyal animal"]] } ] }
    """)

    private val choice = ContentJson.decode(Exercise.serializer(), """
        { "id": "6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d", "type": "choice", "instruction": "Pick the animal.",
          "items": [ { "text": "Please say {{zebra|yak}} loudly." } ] }
    """)

    /** The system clipboard is shared with every program: it can be busy for a moment, so each use is tried again. */
    private fun <T> clipboard(use: (java.awt.datatransfer.Clipboard) -> T): T {
        var last: Exception? = null
        repeat(20) {
            try { return use(Toolkit.getDefaultToolkit().systemClipboard) } catch (e: IllegalStateException) { last = e; Thread.sleep(150) }
        }
        throw last!!
    }

    /** Another program can hold the clipboard for good (a clipboard manager, remote desktop): then these scenarios can't run here. */
    private val clipboardUsable: Boolean by lazy {
        try { clipboard { it.setContents(StringSelection(""), null) }; true } catch (e: IllegalStateException) { println("The system clipboard is busy: the selection scenarios are skipped"); false }
    }

    /** The clipboard can be taken by another program at any moment, not only at the start: then the scenario is skipped. */
    private fun skippedWhenBusy(scenario: () -> Unit) {
        try { scenario() } catch (e: IllegalStateException) {
            if (e.message?.contains("clipboard") != true) throw e
            println("The system clipboard is busy: the selection scenario is skipped")
        }
    }

    private fun clipboardText() = clipboard { it.getData(DataFlavor.stringFlavor) as String }

    private fun ComposeUiTest.copyAll(toX: Float, toY: Float): String {
        clipboard { it.setContents(StringSelection(""), null) }
        onRoot().performMouseInput { moveTo(Offset(18f, 20f)); press(); moveTo(Offset(toX, toY / 2)); moveTo(Offset(toX, toY)); release() }
        waitForIdle()
        // Copy is Cmd+C on macOS and Ctrl+C elsewhere.
        val modifier = if (System.getProperty("os.name").orEmpty().startsWith("Mac")) Key.MetaLeft else Key.CtrlLeft
        onRoot().performKeyInput { keyDown(modifier); keyDown(Key.C); keyUp(Key.C); keyUp(modifier) }
        waitForIdle()
        return clipboardText()
    }

    private fun showPage(palette: Palette, name: String): String {
        var copied = ""
        runDesktopComposeUiTest(width = 700, height = 330) {
            setContent {
                MaterialTheme {
                    ContentTheme(palette.colors) {
                        Column(Modifier.fillMaxSize().background(palette.colors.background).padding(16.dp)) {
                            ContentView(page)
                            Text("outside the page", color = palette.colors.ink)
                        }
                    }
                }
            }
            copied = copyAll(560f, 250f)
            screenshot(name)
        }
        return copied
    }

    @Test fun aPageIsSelectedAndCopiedWithoutTheAnnotationsAndNotBeyondItself() = skippedWhenBusy {
        if (!clipboardUsable) return@skippedWhenBusy
        val copied = showPage(Palettes.softGreen, "selection-page")
        assertTrue("A plain paragraph with bold words, one line." in copied, copied)
        assertTrue("老师" in copied && "你好" in copied, copied)
        assertTrue("cat" in copied && "a loyal animal" in copied, copied)
        assertFalse("lǎoshī" in copied || "nǐhǎo" in copied, "the annotations stay out of the copy: $copied")
        assertFalse("outside" in copied, copied)
    }

    @Test fun theSelectionIsDrawnInEveryPalette() = skippedWhenBusy {
        if (!clipboardUsable) return@skippedWhenBusy
        for (palette in Palettes.all) {
            val copied = showPage(palette, "selection-page-${palette.id}")
            assertTrue("A plain paragraph" in copied, "${palette.id}: $copied")
        }
    }

    @Test fun anOptionIsStillClickedButItsTextIsNotSelected() = skippedWhenBusy {
        if (!clipboardUsable) return@skippedWhenBusy
        var result: List<ItemCheck>? = null
        var copied = ""
        runDesktopComposeUiTest(width = 700, height = 220) {
            setContent {
                var response by remember { mutableStateOf(choice.emptyResponse()) }
                var checks by remember { mutableStateOf<List<ItemCheck>?>(null) }
                val check = { checks = Checking.check(choice, response).also { result = it } }
                MaterialTheme {
                    ContentTheme(ContentColors.forAccent(null)) {
                        Column(Modifier.fillMaxSize().background(Color(0xFFFAF6EC)).padding(16.dp)) {
                            ExerciseView(choice, response, checks, { response = it }, onSubmit = check, seed = 3)
                            if (checks == null) Button(onClick = check) { Text("Check") }
                        }
                    }
                }
            }
            copied = copyAll(600f, 120f)
            screenshot("selection-exercise")
            onNodeWithText("zebra").performClick()
            onNodeWithText("Check").performClick()
            waitForIdle()
        }
        // a sentence with a field is built of word chunks: each is a line in the copy (the known limit of the first version)
        assertTrue("Please" in copied && "say" in copied && "loudly." in copied, copied)
        assertFalse("zebra" in copied || "yak" in copied, "the options are not part of the copied text: $copied")
        assertEquals(listOf(CORRECT), result!!.map { it.result })
    }
}

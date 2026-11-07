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
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.polylingy.content.Content
import com.polylingy.content.ContentJson
import com.polylingy.content.Exercise
import com.polylingy.content.ItemCheck
import com.polylingy.content.ItemResult.CORRECT
import com.polylingy.content.compose.AudioPlayer
import com.polylingy.content.compose.ContentColors
import com.polylingy.content.compose.ContentTheme
import com.polylingy.content.compose.ContentView
import com.polylingy.content.compose.ExerciseView
import com.polylingy.content.emptyResponse
import com.polylingy.core.rules.Checking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Annotations over words (on and off), sounds in theory and exercises, and text without spaces. See docs/content-format.md. */
class AnnotationsAudioUiTest {
    private val played = mutableListOf<String>()
    private val accent = "#1784BE"

    private val theory = ContentJson.decode(Content.serializer(), """
        { "format": "blocks", "blocks": [
          { "type": "section", "label": "A", "title": "Диалог", "blocks": [
            { "type": "dialogue", "lines": [
              { "who": "[安娜](Ānnà)", "text": "[你好](Nǐ hǎo)！", "audio": "audio/d1-1.mp3" },
              { "who": "[王老师](Wáng lǎoshī)", "text": "[你好](Nǐ hǎo)！[你](Nǐ)[是](shì)[学生](xuésheng)[吗](ma)？", "audio": "audio/d1-2.mp3" },
              { "who": "[安娜](Ānnà)", "text": "[是](Shì)，[我](wǒ)[是](shì)[留学生](liúxuéshēng)。[您](Nín)[是](shì)[老师](lǎoshī)[吗](ma)？", "audio": "audio/d1-3.mp3" } ] } ] },
          { "type": "section", "label": "B", "title": "Новые слова", "blocks": [
            { "type": "table", "rows": [
              [{ "audio": "audio/w-nihao.mp3" }, "你好", "nǐ hǎo", "Здравствуйте!"],
              [{ "audio": "audio/w-laoshi.mp3" }, "老师", "lǎoshī", "учитель"] ] } ] },
          { "type": "section", "label": "C", "title": "Без подписей и пробелов", "blocks": [
            { "type": "text", "text": "我是学生。她不是老师，她是留学生。我们都是北京大学的学生，我们的老师姓王，他是一位很好的老师。" } ] } ] }
    """)

    private fun page(annotations: Boolean, width: Int = 832, block: ComposeUiTest.() -> Unit) = runDesktopComposeUiTest(width = width, height = 900) {
        setContent {
            MaterialTheme {
                ContentTheme(ContentColors.forAccent(accent), audio = AudioPlayer { played += it }, annotations = annotations) {
                    Column(Modifier.fillMaxSize().background(Color(0xFFFAF6EC)).verticalScroll(rememberScrollState()).padding(24.dp).widthIn(max = 760.dp)) {
                        ContentView(theory)
                    }
                }
            }
        }
        block()
    }

    @Test fun dialogueWithAnnotationsAndSound() = page(annotations = true) {
        onNodeWithContentDescription("Dialogue").assertIsDisplayed()
        onAllNodesWithText("xuésheng")[0].assertIsDisplayed()
        onAllNodesWithContentDescription("Play")[1].performClick()
        waitForIdle()
        assertEquals(listOf("audio/d1-2.mp3"), played)
        onAllNodesWithContentDescription("Play")[3].performClick()
        assertEquals("audio/w-nihao.mp3", played.last())
        screenshot("theory-annotations")
    }

    @Test fun annotationsTurnedOffShowOnlyTheWords() = page(annotations = false) {
        assertTrue(onAllNodesWithText("xuésheng").fetchSemanticsNodes().isEmpty())
        onNodeWithText("你好！你是学生吗？").assertIsDisplayed()
        screenshot("theory-no-annotations")
    }

    @Test fun textWithoutSpacesWrapsOnANarrowWindow() = page(annotations = true, width = 420) {
        screenshot("theory-annotations-narrow")
    }

    private fun exercise(json: String) = ContentJson.decode(Exercise.serializer(), json)

    private fun showcase(exercise: Exercise, checked: (List<ItemCheck>) -> Unit = {}, block: ComposeUiTest.() -> Unit) =
        runDesktopComposeUiTest(width = 832, height = 700) {
            setContent {
                var response by remember { mutableStateOf(exercise.emptyResponse()) }
                var checks by remember { mutableStateOf<List<ItemCheck>?>(null) }
                val check = { checks = Checking.check(exercise, response).also(checked) }
                MaterialTheme {
                    ContentTheme(ContentColors.forAccent(accent), audio = AudioPlayer { played += it }) {
                        Column(Modifier.fillMaxSize().background(Color(0xFFFAF6EC)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            ExerciseView(exercise, response, checks, { response = it }, onSubmit = check, seed = 3)
                            if (checks == null) Button(onClick = check) { Text("Check") }
                        }
                    }
                }
            }
            block()
        }

    @Test fun listenAndChoose() {
        var result: List<ItemCheck>? = null
        showcase(exercise("""{ "id": "6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d", "type": "choice", "instruction": "Послушайте и выберите слово.",
            "audio": "audio/w-xuesheng.mp3", "items": [ { "text": "{{学生|老师|谢谢|名字}}" } ] }"""), checked = { result = it }) {
            onNodeWithContentDescription("Play").performClick()
            assertEquals(listOf("audio/w-xuesheng.mp3"), played)
            onNodeWithText("学生").performClick()
            screenshot("ex-listen")
            onNodeWithText("Check").performClick()
            waitForIdle()
            screenshot("ex-listen-checked")
        }
        assertEquals(listOf(CORRECT), result!!.map { it.result })
    }

    @Test fun tilesWithAnnotations() {
        var result: List<ItemCheck>? = null
        showcase(exercise("""{ "id": "6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d", "type": "order", "instruction": "Сделайте предложение отрицательным: 我是老师。",
            "items": [ { "answer": "[我](wǒ) [不](bú) [是](shì) [老师](lǎoshī)。" } ] }"""), checked = { result = it }) {
            for (word in listOf("我", "不")) onNodeWithText(word).performClick()
            screenshot("ex-order-annotations")
            for (word in listOf("是", "老师")) onNodeWithText(word).performClick()
            onNodeWithText("Check").performClick()
            waitForIdle()
        }
        assertEquals(listOf(CORRECT), result!!.map { it.result })
    }

    @Test fun linesOfADialogueInOrder() {
        var result: List<ItemCheck>? = null
        showcase(exercise("""{ "id": "6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d", "type": "order", "instruction": "Расставьте реплики по порядку.",
            "items": [ { "parts": ["[你好](Nǐ hǎo)！", "[你好](Nǐ hǎo)！[你](nǐ)[是](shì)[老师](lǎoshī)[吗](ma)？", "[不是](Bú shì)，[我](wǒ)[是](shì)[学生](xuésheng)。"] } ] }"""),
            checked = { result = it }) {
            onAllNodesWithText("你好", substring = true)[0].performClick()
            waitForIdle()
            screenshot("ex-order-parts")
        }
        assertTrue(result == null)
    }
}

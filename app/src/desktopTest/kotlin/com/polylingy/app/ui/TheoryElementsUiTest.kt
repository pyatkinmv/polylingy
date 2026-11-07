package com.polylingy.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.polylingy.content.Content
import com.polylingy.content.ContentJson
import com.polylingy.content.compose.ContentColors
import com.polylingy.content.compose.ContentTheme
import com.polylingy.content.compose.ContentView
import com.polylingy.content.compose.LocalContentStrings
import com.polylingy.content.compose.RussianContentStrings
import androidx.compose.runtime.CompositionLocalProvider
import kotlin.test.Test

// The kinds of remarks, the lead paragraph and numbered examples, on texts of the example courses.
class TheoryElementsUiTest {
    private val theory = """{ "format": "blocks", "blocks": [
        { "type": "section", "title": "Initials", "blocks": [
          { "type": "text", "role": "lead", "text": "There are 21 initials. By where the sound is made:" },
          { "type": "tip", "blocks": [ { "type": "text", "text": "The pairs b–p, d–t, g–k differ by **aspiration**, not by voicing." } ] },
          { "type": "tip", "kind": "rule", "blocks": [ { "type": "text", "text": "Question: Subject + 是 + noun + 吗？" } ] },
          { "type": "tip", "kind": "note", "blocks": [ { "type": "text", "text": "The answer to a greeting often differs from the greeting." } ] },
          { "type": "tip", "kind": "warning", "blocks": [ { "type": "text", "text": "After *does* and *doesn’t* the verb has no -s." } ] },
          { "type": "text", "text": "这 / 那" },
          { "type": "examples", "numbered": true, "items": [
            { "text": "这是汉语课本。", "translation": "This is a Chinese textbook." },
            { "text": "这是老师的书。", "translation": "This is the teacher’s book." },
            { "text": "那是音乐杂志。", "translation": "That is a music magazine." } ] },
          { "type": "examples", "items": [ { "text": "Plain example without numbers" } ] },
          { "type": "video", "title": "Greetings in Arabic", "url": "https://www.youtube.com/watch?v=abc", "note": "Watch and repeat." },
          { "type": "video", "title": "Another site", "url": "https://vimeo.com/123" } ] } ] }"""

    @Test fun remarksLeadAndNumbersAreDrawn() = runDesktopComposeUiTest(width = 700, height = 1000) {
        val content = ContentJson.decode(Content.serializer(), theory)
        setContent {
            MaterialTheme {
                ContentTheme(ContentColors.forAccent(null)) {
                    Column(Modifier.fillMaxSize().background(Color(0xFFFBF8F1)).padding(24.dp)) { ContentView(content) }
                }
            }
        }
        for (caption in listOf("Tip", "Rule", "Note", "Warning", "1", "2", "3")) onNodeWithText(caption).assertIsDisplayed()
        onNodeWithText("There are 21 initials. By where the sound is made:").assertIsDisplayed()
        screenshot("theory-elements")
    }

    @Test fun theWordsAroundTheContentFollowTheLanguageOfTheApp() = runDesktopComposeUiTest(width = 700, height = 1000) {
        val content = ContentJson.decode(Content.serializer(), theory)
        setContent {
            MaterialTheme {
                ContentTheme(ContentColors.forAccent(null)) {
                    CompositionLocalProvider(LocalContentStrings provides RussianContentStrings) {
                        Column(Modifier.fillMaxSize().background(Color(0xFFFBF8F1)).padding(24.dp)) { ContentView(content) }
                    }
                }
            }
        }
        for (caption in listOf("Совет", "Правило", "Примечание", "Осторожно")) onNodeWithText(caption).assertIsDisplayed()
        screenshot("theory-elements-ru")
    }
}

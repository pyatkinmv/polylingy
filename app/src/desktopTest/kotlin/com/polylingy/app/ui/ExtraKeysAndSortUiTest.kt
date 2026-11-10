package com.polylingy.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.polylingy.content.ContentJson
import com.polylingy.content.Exercise
import com.polylingy.content.ItemCheck
import com.polylingy.content.ItemResult.CORRECT
import com.polylingy.content.ItemResult.WRONG
import com.polylingy.content.compose.ContentColors
import com.polylingy.content.compose.ContentTheme
import com.polylingy.content.compose.ExerciseView
import com.polylingy.content.emptyResponse
import com.polylingy.core.rules.Checking
import kotlin.test.Test
import kotlin.test.assertEquals

/** The keys a course adds to a text field (`extraKeys`) and the sort exercise. See docs/content-format.md. */
class ExtraKeysAndSortUiTest {
    private fun exercise(json: String) = ContentJson.decode(Exercise.serializer(), json)

    private fun showcase(exercise: Exercise, extraKeys: List<String>, checked: (List<ItemCheck>) -> Unit = {}, block: ComposeUiTest.() -> Unit) =
        runDesktopComposeUiTest(width = 832, height = 700) {
            setContent {
                var response by remember { mutableStateOf(exercise.emptyResponse()) }
                var checks by remember { mutableStateOf<List<ItemCheck>?>(null) }
                val check = { checks = Checking.check(exercise, response).also(checked) }
                MaterialTheme {
                    ContentTheme(ContentColors.forAccent(null), extraKeys = extraKeys) {
                        Column(
                            Modifier.fillMaxSize().background(Color(0xFFFAF6EC)).padding(24.dp).widthIn(max = 760.dp),
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

    private val gaps = exercise("""
        { "id": "6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d", "type": "gaps", "instruction": "Впишите слово по значению.",
          "items": [ { "text": "«молоко» — {{һүн}}" }, { "text": "«день» — {{үдэр}}" } ] }
    """)

    @Test fun theKeysShowWhileAFieldHasFocusAndPutTheirLetterWhereTheCursorIs() {
        var result: List<ItemCheck>? = null
        showcase(gaps, listOf("ө", "ү", "һ"), checked = { result = it }) {
            onNodeWithContentDescription("Key ү").assertDoesNotExist()
            onNodeWithContentDescription("Blank 1").performClick()
            waitForIdle()
            onNodeWithContentDescription("Key ү").assertIsDisplayed()
            screenshot("extra-keys-bubble")
            onNodeWithContentDescription("Key һ").performClick()
            onNodeWithContentDescription("Key ү").performClick()
            waitForIdle()
            onNodeWithContentDescription("Blank 1").performTextInput("н")
            // the bubble follows the focus: a click away from the fields takes it back
            onNodeWithText("Впишите слово по значению.").performClick()
            waitForIdle()
            onNodeWithContentDescription("Key ү").assertDoesNotExist()
            onNodeWithContentDescription("Blank 2").performTextInput("үдэр")
            onNodeWithText("Check").performClick()
            waitForIdle()
            screenshot("extra-keys-checked")
        }
        assertEquals(listOf(CORRECT, CORRECT), result!!.map { it.result })
    }

    @Test fun withoutExtraKeysThereIsNoBubble() = showcase(gaps, emptyList()) {
        onNodeWithContentDescription("Blank 1").performClick()
        waitForIdle()
        onNodeWithContentDescription("Key ү").assertDoesNotExist()
    }

    private val sort = exercise("""
        { "id": "6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d", "type": "sort", "instruction": "К какому ряду относятся слова?",
          "groups": [ { "title": "задний ряд", "items": ["шатар", "ном"] }, { "title": "передний ряд", "items": ["үдэр", "гэр"] } ] }
    """)

    @Test fun wordsAreSortedByClickingAWordAndItsGroup() {
        var result: List<ItemCheck>? = null
        showcase(sort, emptyList(), checked = { result = it }) {
            screenshot("sort-pile")
            for ((word, group) in listOf("шатар" to 1, "ном" to 1, "үдэр" to 2, "гэр" to 1)) {
                onNodeWithText(word).performClick()
                onNodeWithContentDescription("Group $group").performClick()
                waitForIdle()
            }
            screenshot("sort-placed")
            onNodeWithText("Check").performClick()
            waitForIdle()
            screenshot("sort-checked")
        }
        assertEquals(listOf(CORRECT, CORRECT, CORRECT, WRONG), result!!.map { it.result })
    }
}

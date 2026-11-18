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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.polylingy.content.ContentJson
import com.polylingy.content.Exercise
import com.polylingy.content.ItemCheck
import com.polylingy.content.ItemResult.CORRECT
import com.polylingy.content.compose.ContentColors
import com.polylingy.content.compose.ContentTheme
import com.polylingy.content.compose.ExerciseView
import com.polylingy.content.emptyResponse
import com.polylingy.core.rules.Checking
import kotlin.test.Test
import kotlin.test.assertEquals

/** A blank's own hint, `{{answer}}[[hint]]`: grey in the empty field, one line under the sentence after the check. */
class BlankHintUiTest {
    private val exercise = ContentJson.decode(Exercise.serializer(), """
        { "id": "6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d", "type": "gaps", "instruction": "Second vs. Third Conditional",
          "items": [ { "text": "If he {{hadn't missed|had not missed}}[[not miss]] the bus yesterday, he {{would have been}}[[be]] here on time." } ] }
    """)

    @Test fun everyBlankShowsItsOwnHintAndTheCheckListsThem() {
        var result: List<ItemCheck>? = null
        runDesktopComposeUiTest(width = 900, height = 360) {
            setContent {
                var response by remember { mutableStateOf(exercise.emptyResponse()) }
                var checks by remember { mutableStateOf<List<ItemCheck>?>(null) }
                val check = { checks = Checking.check(exercise, response).also { result = it } }
                MaterialTheme {
                    ContentTheme(ContentColors.forAccent(null)) {
                        Column(Modifier.fillMaxSize().background(Color(0xFFFAF6EC)).padding(24.dp)) {
                            ExerciseView(exercise, response, checks, { response = it }, onSubmit = check, seed = 3)
                            if (checks == null) Button(onClick = check) { Text("Check") }
                        }
                    }
                }
            }
            onNodeWithText("not miss").assertIsDisplayed()
            onNodeWithText("be").assertIsDisplayed()
            screenshot("blank-hints-empty")
            onNodeWithContentDescription("Blank 1").performTextInput("had not missed")
            onNodeWithContentDescription("Blank 2").performTextInput("would have been")
            onNodeWithText("Check").performClick()
            waitForIdle()
            onNodeWithText("(not miss / be)").assertIsDisplayed()
            screenshot("blank-hints-checked")
        }
        assertEquals(listOf(CORRECT, CORRECT), result!!.map { it.result })
    }
}

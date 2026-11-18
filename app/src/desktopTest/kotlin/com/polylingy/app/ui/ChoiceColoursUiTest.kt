package com.polylingy.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.polylingy.content.ContentJson
import com.polylingy.content.Exercise
import com.polylingy.content.ItemCheck
import com.polylingy.content.compose.ContentTheme
import com.polylingy.content.compose.ExerciseView
import com.polylingy.content.compose.Palette
import com.polylingy.content.compose.Palettes
import com.polylingy.content.emptyResponse
import com.polylingy.core.rules.Checking
import kotlin.test.Test

/** A chosen option and the same option once checked: one green (or red) with the outline kept, in every palette. */
class ChoiceColoursUiTest {
    private val choice = ContentJson.decode(Exercise.serializer(), """
        { "id": "6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d", "type": "choice",
          "items": [ { "text": "Please say {{zebra|yak}} loudly." } ] }
    """)

    @Composable
    private fun Tile(palette: Palette, caption: String, modifier: Modifier) {
        var response by remember { mutableStateOf(choice.emptyResponse()) }
        var checks by remember { mutableStateOf<List<ItemCheck>?>(null) }
        Column(modifier.background(palette.colors.card, RoundedCornerShape(10.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${palette.name}: $caption", color = palette.colors.muted, fontSize = 12.sp)
            ContentTheme(palette.colors) { ExerciseView(choice, response, checks, { response = it }, onSubmit = {}, seed = 3) }
            if (checks == null) Button(onClick = { checks = Checking.check(choice, response) }) { Text("Check") }
        }
    }

    @Test fun everyPaletteChosenAndChecked() = runDesktopComposeUiTest(width = 900, height = 1250) {
        setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().background(Color(0xFFFAF7F0)).padding(10.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (p in Palettes.all) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile(p, "right one chosen", Modifier.weight(1f))
                        Tile(p, "wrong one chosen", Modifier.weight(1f))
                    }
                }
            }
        }
        for (k in 0 until Palettes.all.size * 2) onAllNodesWithText(if (k % 2 == 0) "zebra" else "yak")[k].performClick()
        screenshot("choice-colours-chosen")
        repeat(Palettes.all.size * 2) { onAllNodesWithText("Check")[0].performClick() }
        screenshot("choice-colours-checked")
    }
}

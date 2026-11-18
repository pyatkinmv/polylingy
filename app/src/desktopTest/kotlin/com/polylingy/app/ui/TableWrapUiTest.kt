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
import kotlin.test.Test
import kotlin.test.assertTrue

// A table cell with a long text wraps inside the page instead of running off its right edge; see docs/testing.md.
class TableWrapUiTest {
    private val long = "on the south — Mongolia; on the south-west — the Republic of Tuva; on the north-west — Irkutsk Oblast; on the east — Zabaykalsky Krai"
    private val theory = """{ "format": "blocks", "blocks": [
        { "type": "table", "head": ["What", "How it is"], "rows": [ ["Area", "351,3 thousand square kilometres"], ["Neighbours", "$long"], ["Capital", "Ulan-Ude"] ] },
        { "type": "table", "head": ["A", "B", "C", "D", "E"], "rows": [ ["one", "two", "three", "four", "five"] ] } ] }"""

    @Test fun aLongCellWrapsInsideThePage() = runDesktopComposeUiTest(width = 700, height = 600) {
        val content = ContentJson.decode(Content.serializer(), theory)
        setContent {
            MaterialTheme {
                ContentTheme(ContentColors.forAccent(null)) {
                    Column(Modifier.fillMaxSize().background(Color(0xFFFBF8F1)).padding(24.dp)) { ContentView(content) }
                }
            }
        }
        val cell = onNodeWithText(long)
        cell.assertIsDisplayed()
        val bounds = cell.fetchSemanticsNode().boundsInWindow
        assertTrue(bounds.right <= 700f, "the cell ends at ${bounds.right}, past the page (700)")
        assertTrue(bounds.height > 30f, "the long cell takes several lines, its height is ${bounds.height}")
        onNodeWithText("351,3 thousand square kilometres").assertIsDisplayed()
        screenshot("table-wrap")
    }
}

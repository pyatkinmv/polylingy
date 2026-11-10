package com.polylingy.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.ComposeUiTest
import kotlin.test.assertTrue
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.layout.boundsInRoot
import kotlin.math.abs
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.polylingy.content.Content
import com.polylingy.content.ContentJson
import com.polylingy.content.Exercise
import com.polylingy.content.compose.ContentColors
import com.polylingy.content.compose.ContentTheme
import com.polylingy.content.compose.ContentView
import com.polylingy.content.compose.ExerciseView
import com.polylingy.content.compose.ImageLoader
import com.polylingy.content.emptyResponse
import kotlin.test.Test

/**
 * How pictures sit in the content: the size words of an image block, a situation as a card or without a frame, a small
 * picture as a thumbnail, the picture cards of a match, the caps of an aside. See docs/content-format.md, sections 4 and 5.
 */
class PictureLayoutUiTest {
    private fun picture(background: Color, spot: Color): ImageBitmap {
        val bitmap = ImageBitmap(640, 640)
        val canvas = Canvas(bitmap)
        canvas.drawRect(0f, 0f, 640f, 640f, Paint().apply { color = background })
        canvas.drawCircle(Offset(320f, 320f), 210f, Paint().apply { color = spot })
        canvas.drawCircle(Offset(320f, 170f), 60f, Paint().apply { color = Color.White })
        return bitmap
    }

    private val bitmaps = listOf(
        Color(0xFFE9D8B8) to Color(0xFF8A5A2B), Color(0xFFCFE3D2) to Color(0xFF3E7C59), Color(0xFFD6DCEE) to Color(0xFF45609A),
        Color(0xFFF1D3D3) to Color(0xFFB4495B), Color(0xFFEADFF3) to Color(0xFF7A4E8C),
    ).map { (bg, spot) -> picture(bg, spot) }
    private val images = ImageLoader { src -> bitmaps[(src.filter { it.isDigit() }.toIntOrNull() ?: 0) % bitmaps.size] }

    private fun img(n: Int, alt: String, size: String? = null) =
        """{ "type": "image", "src": "images/p$n.png", "alt": "$alt"${size?.let { """, "size": "$it" """ } ?: ""} }"""

    private val theory = """{ "format": "blocks", "blocks": [
        { "type": "text", "text": "A picture block with no size word is medium: it does not fill a wide window." },
        ${img(1, "Medium picture")},
        { "type": "text", "text": "Small, and large." },
        ${img(2, "Small picture", "small")},
        ${img(3, "Large picture", "large")},
        { "type": "text", "text": "The phrase card: a small picture is a square thumbnail, the text stays beside it." },
        { "type": "situation", "aside": ${img(4, "Thumbnail", "small")}, "blocks": [
          { "type": "text", "text": "**أَهْلاً وَسَهْلاً!** ahlan wa sahlan" }, { "type": "text", "text": "Hello and welcome!" } ] },
        { "type": "text", "text": "Without a frame: a picture that simply stands beside a paragraph." },
        { "type": "situation", "look": "plain", "aside": ${img(0, "Beside a paragraph", "medium")}, "blocks": [
          { "type": "text", "text": "Many Arabic words look like English ones: bitza, bas, sinima. The paragraph goes on beside the picture, as on a page of a textbook, and takes the rest of the width." } ] } ] }"""

    @Test fun picturesKeepToTheirSizes() = runDesktopComposeUiTest(width = 900, height = 1700) {
        val content = ContentJson.decode(Content.serializer(), theory)
        setContent {
            MaterialTheme {
                ContentTheme(ContentColors.forAccent(null), images) {
                    Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFFFBF8F1)).verticalScroll(rememberScrollState()).padding(24.dp)) { ContentView(content) }
                }
            }
        }
        onNodeWithContentDescription("Medium picture").assertWidthIsEqualTo(300.dp)
        onNodeWithContentDescription("Small picture").assertWidthIsEqualTo(160.dp)
        onNodeWithContentDescription("Large picture").assertWidthIsEqualTo(560.dp)
        onNodeWithContentDescription("Thumbnail").assertWidthIsEqualTo(120.dp).assertHeightIsEqualTo(120.dp)
        onNodeWithContentDescription("Beside a paragraph").assertWidthIsEqualTo(180.dp)
        screenshot("picture-sizes")
    }

    private val match = ContentJson.decode(Exercise.serializer(), """
        { "id": "6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d", "type": "match",
          "instruction": "Look at the photos and read the sentences. Who is saying what? Match the photo and the sentence.",
          "pairs": [
            [ { "image": { "src": "images/p1.png", "alt": "Photo 1" } }, "أنا مُنيرة وهذا ميدو. هو ابني." ],
            [ { "image": { "src": "images/p2.png", "alt": "Photo 2" } }, "أنا جيهان وهذا أبي." ],
            [ { "image": { "src": "images/p3.png", "alt": "Photo 3" } }, "هذه أختي وهذا زوج أختي." ],
            [ { "image": { "src": "images/p4.png", "alt": "Photo 4" } }, "أنا أنور وهذه زينب. هي بنتي." ],
            [ { "image": { "src": "images/p5.png", "alt": "Photo 5" } }, "هذا أخي، بدر، وهذه أختي، وردة." ] ] }
    """)

    private fun ComposeUiTest.widthDp(description: String) = with(density) { onNodeWithContentDescription(description).fetchSemanticsNode().size.width.toDp() }

    private fun exerciseTest(exercise: Exercise, width: Int, height: Int, block: androidx.compose.ui.test.ComposeUiTest.() -> Unit) =
        runDesktopComposeUiTest(width = width, height = height) {
            setContent {
                var response by remember { mutableStateOf(exercise.emptyResponse()) }
                MaterialTheme {
                    ContentTheme(ContentColors.forAccent(null), images) {
                        Column(
                            Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFFFAF6EC)).verticalScroll(rememberScrollState()).padding(24.dp).widthIn(max = 760.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) { ExerciseView(exercise, response, null, { response = it }, onSubmit = {}, seed = 3) }
                    }
                }
            }
            block()
        }

    @Test fun aMatchOfPicturesShowsCardsNotWideTiles() = exerciseTest(match, 900, 1000) {
        for (n in 1..5) onNodeWithContentDescription("Photo $n").assertIsDisplayed()
        assertTrue(widthDp("Photo 1") <= 170.dp, "a picture card is narrow")
        screenshot("match-pictures")
    }

    private fun centreY(node: SemanticsNodeInteraction) = node.fetchSemanticsNode().boundsInRoot.center.y

    @Test fun pictureCardsAreSquareAndTheRightCardsLineUpWithThem() = exerciseTest(match, 900, 1000) {
        for (n in 1..5) onNodeWithContentDescription("Photo $n").assertWidthIsEqualTo(128.dp).assertHeightIsEqualTo(128.dp)
        val pictures = (1..5).map { centreY(onNodeWithContentDescription("Photo $it")) }.sorted()
        val texts = listOf("أنا مُنيرة وهذا ميدو. هو ابني.", "أنا جيهان وهذا أبي.", "هذه أختي وهذا زوج أختي.", "أنا أنور وهذه زينب. هي بنتي.", "هذا أخي، بدر، وهذه أختي، وردة.")
            .map { centreY(onNodeWithText(it)) }.sorted()
        // Row by row: the i-th picture card and the i-th right card share one horizontal line, so the columns are equally long.
        for ((p, t) in pictures.zip(texts)) assertTrue(abs(p - t) < 2f, "a right card stands level with a picture card ($p vs $t)")
        screenshot("match-pictures-rows")
    }

    @Test fun anAsideOnTopStaysWithinItsCap() = exerciseTest(ContentJson.decode(Exercise.serializer(), """
        { "id": "6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d", "type": "choice", "instruction": "Pick the right one.",
          "aside": { "image": { "src": "images/p2.png", "alt": "A big picture" }, "side": "top", "size": "large" },
          "items": [ { "text": "1 = {{a|b|c}}" } ] }
    """), 1200, 1100) {
        assertTrue(widthDp("Aside") <= 520.dp, "the aside keeps to its cap")
        screenshot("aside-cap")
    }
}

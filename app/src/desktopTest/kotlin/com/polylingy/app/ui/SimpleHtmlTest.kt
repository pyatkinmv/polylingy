package com.polylingy.app.ui

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals

class SimpleHtmlTest {
    private fun text(html: String) = htmlToAnnotatedString(html).text

    @Test fun plainTextPassesThrough() = assertEquals("hello world", text("hello   world"))

    @Test fun boldAndItalicBecomeSpans() {
        val result = htmlToAnnotatedString("a <b>bold</b> and <em>it</em>")
        assertEquals("a bold and it", result.text)
        val (bold, italic) = result.spanStyles
        assertEquals(FontWeight.Bold, bold.item.fontWeight)
        assertEquals("bold", result.text.substring(bold.start, bold.end))
        assertEquals(FontStyle.Italic, italic.item.fontStyle)
        assertEquals("it", result.text.substring(italic.start, italic.end))
    }

    @Test fun breaksAndParagraphsBecomeNewLines() = assertEquals("one\ntwo\nthree", text("<p>one</p><p>two<br>three</p>"))

    @Test fun listItemsGetBullets() = assertEquals("Colors:\n• rojo\n• azul", text("Colors:<ul><li>rojo</li><li>azul</li></ul>"))

    @Test fun entitiesAreDecoded() = assertEquals("a < b & \"c\" é", text("a &lt; b &amp; &quot;c&quot; &#233;"))

    @Test fun unknownTagsAreDroppedButTextKept() = assertEquals("red word", text("""<span style="color:red">red</span> word"""))

    @Test fun mismatchedTagsDoNotCrash() = assertEquals("x y", text("<b>x <i>y</b></i>"))
}

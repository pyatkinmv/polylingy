package com.polylingy.content.compose

import com.polylingy.content.Block
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentStringsTest {
    @Test fun everyKindOfRemarkHasACaptionInEverySet() {
        for (set in listOf(EnglishContentStrings, RussianContentStrings)) {
            val captions = Block.Tip.Kind.entries.map { set.remark(it) }
            assertEquals(captions.size, captions.toSet().size, "captions differ in $set")
            assertTrue(captions.none { it.isBlank() })
        }
    }

    @Test fun onlyYouTubeAddressesGetTheYouTubeMark() {
        assertTrue(isYouTube("https://www.youtube.com/watch?v=abc"))
        assertTrue(isYouTube("https://youtu.be/abc"))
        assertTrue(isYouTube("https://m.youtube.com/watch?v=abc"))
        assertFalse(isYouTube("https://vimeo.com/123"))
        assertFalse(isYouTube("https://notyoutube.com/x"))
        assertFalse(isYouTube("https://youtube.com.evil.example/x"))
    }
}

class OptionOrderTest {
    @Test fun numbersGoInAscendingOrderAndWordsAreShuffled() {
        val random = kotlin.random.Random(1)
        assertEquals(listOf("0", "1", "2", "3", "10"), optionsInOrder(listOf("3", "10", "0", "2", "1"), random))
        assertEquals(listOf("1.5", "2", "11"), optionsInOrder(listOf("11", "1.5", "2"), random))
        val words = listOf("a", "b", "c", "d", "e", "f")
        assertEquals(words.toSet(), optionsInOrder(words, kotlin.random.Random(7)).toSet())
        assertEquals(setOf("1", "x"), optionsInOrder(listOf("1", "x"), random).toSet(), "one non-number is enough to shuffle")
    }
}

class MarkTest {
    @Test fun markedPiecesAreFoundEverywhereInTheText() {
        val marked = mark(androidx.compose.ui.text.AnnotatedString("abcab"), "ab", androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
        assertEquals(listOf(0 to 2, 3 to 5), marked.spanStyles.map { it.start to it.end })
        assertEquals(0, mark(androidx.compose.ui.text.AnnotatedString("abc"), null, androidx.compose.ui.text.SpanStyle()).spanStyles.size)
    }
}

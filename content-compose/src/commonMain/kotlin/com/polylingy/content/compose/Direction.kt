package com.polylingy.content.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.polylingy.content.Inline
import com.polylingy.content.InlineNode

/** Whether [c] belongs to a script written from right to left: Hebrew, Arabic and its extensions. */
internal fun isRightToLeft(c: Char): Boolean = c.code in 0x0590..0x08FF || c.code in 0xFB1D..0xFDFF || c.code in 0xFE70..0xFEFF

/**
 * Whether a text runs from right to left: when nearly all of its letters (at least [RightToLeftShare]) belong to a
 * right-to-left script. Markup (`**`, `[`, `{{`), digits and punctuation don't count, nor do the notes over words and the
 * wrong options of a choice: only what is read. So a Russian sentence with an Arabic word or a phrase in it runs left to right,
 * with the Arabic inside, and an Arabic phrase with its transliteration over it runs right to left.
 */
internal fun String.runsRightToLeft(): Boolean {
    val letters = count { it.isLetter() }
    return letters > 0 && count { it.isLetter() && isRightToLeft(it) } >= letters * RightToLeftShare
}

/** How much of a text must be in a right-to-left script for the text to run from the right. */
internal const val RightToLeftShare = 0.8

internal fun Inline.runsRightToLeft(): Boolean {
    var rtl = 0
    var other = 0
    fun count(s: String) {
        for (c in s) if (c.isLetter()) { if (isRightToLeft(c)) rtl++ else other++ }
    }
    fun walk(nodes: List<InlineNode>) {
        for (n in nodes) when (n) {
            is InlineNode.Text -> count(n.text)
            is InlineNode.Bold -> walk(n.children)
            is InlineNode.Italic -> walk(n.children)
            is InlineNode.Blank -> count(n.answer)
            is InlineNode.Annotated -> count(n.base)
            InlineNode.LineBreak -> {}
        }
    }
    walk(nodes)
    return rtl + other > 0 && rtl >= (rtl + other) * RightToLeftShare
}

/**
 * Lays [content] out from the right when [rightToLeft], from the left otherwise: the first word, tile or blank stands at the
 * right edge, the way a reader of Arabic or Hebrew meets it. The page itself stays as it is; only this text, with the blanks
 * and tiles of its exercise. A line of our own words (a hint) inside such content asks for `Direction(false)`.
 */
@Composable
internal fun Direction(rightToLeft: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides if (rightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr, content = content)
}

/** Letters of right-to-left scripts are drawn larger than Latin ones (by two fifths) of the same size: their forms are small and carry marks. */
internal const val RightToLeftScale = 1.4f

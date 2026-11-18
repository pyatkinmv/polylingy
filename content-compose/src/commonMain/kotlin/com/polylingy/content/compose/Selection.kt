package com.polylingy.content.compose

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/** Set while we are inside a [Selectable]: a selectable inside another one shares it instead of starting its own. */
private val LocalInSelection = staticCompositionLocalOf { false }

/**
 * Makes the text inside selectable (drag, double click for a word, triple click for a paragraph, Ctrl+C, and "Copy" in the
 * right-click menu), highlighted in the accent of the course's palette. What is clicked or typed into (options, tiles, cards, fields,
 * links, sounds) and the small annotations over words are left out with `DisableSelection`.
 */
@Composable
fun Selectable(content: @Composable () -> Unit) {
    if (LocalInSelection.current) {
        content()
        return
    }
    CompositionLocalProvider(
        LocalTextSelectionColors provides TextSelectionColors(handleColor = colors.accent, backgroundColor = colors.accent.copy(alpha = SELECTION_ALPHA)),
        LocalInSelection provides true,
    ) {
        SelectionContainer(content = content)
    }
}

/** How strong the accent is as the background of a selection: the text on it stays readable in every palette (PalettesTest). */
const val SELECTION_ALPHA = 0.3f

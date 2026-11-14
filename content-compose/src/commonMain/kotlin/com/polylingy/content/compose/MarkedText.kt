package com.polylingy.content.compose

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDirection
import com.polylingy.content.Inline

/** The piece of text the learner chose in a checked exercise (see `ChoiceItem.mark`); frames mark it in their own text. */
internal val LocalMarked = staticCompositionLocalOf<String?> { null }

/** [text] with every place of [piece] marked; the text itself when [piece] is null or not found. */
internal fun mark(text: AnnotatedString, piece: String?, highlight: SpanStyle): AnnotatedString {
    if (piece.isNullOrEmpty()) return text
    return buildAnnotatedString {
        append(text)
        var from = text.text.indexOf(piece)
        while (from >= 0) {
            addStyle(highlight, from, from + piece.length)
            from = text.text.indexOf(piece, from + piece.length)
        }
    }
}

/** An [InlineText] of a frame, with the chosen piece marked. */
@Composable
internal fun MarkedText(inline: Inline, modifier: Modifier = Modifier, style: TextStyle) {
    val piece = LocalMarked.current
    if (piece == null || inline.hasAnnotations) return InlineText(inline, modifier, style)
    val highlight = SpanStyle(background = colors.accent.copy(alpha = 0.3f))
    Text(
        mark(inline.annotated(rememberMarkPaint()), piece, highlight),
        modifier,
        style = style.copy(textDirection = if (inline.runsRightToLeft()) TextDirection.Rtl else TextDirection.Ltr),
    )
}

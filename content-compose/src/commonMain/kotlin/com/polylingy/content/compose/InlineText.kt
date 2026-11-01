package com.polylingy.content.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.polylingy.content.Inline
import com.polylingy.content.InlineNode

/** A text with markup and no blanks, as one paragraph. */
@Composable
fun InlineText(inline: Inline, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current) {
    Text(inline.annotated(), modifier, style = style)
}

/** Bold and italic as text styles; blanks, which can't be shown here, as their answer. */
fun Inline.annotated(): AnnotatedString = buildAnnotatedString { append(nodes, bold = false, italic = false) }

private fun AnnotatedString.Builder.append(nodes: List<InlineNode>, bold: Boolean, italic: Boolean) {
    for (node in nodes) when (node) {
        is InlineNode.Text -> withStyle(style(bold, italic)) { append(node.text) }
        is InlineNode.Bold -> append(node.children, true, italic)
        is InlineNode.Italic -> append(node.children, bold, true)
        is InlineNode.Blank -> withStyle(style(bold, italic)) { append(node.answer) }
        InlineNode.LineBreak -> append('\n')
    }
}

private fun style(bold: Boolean, italic: Boolean) = SpanStyle(
    fontWeight = if (bold) FontWeight.SemiBold else null,
    fontStyle = if (italic) FontStyle.Italic else null,
)

/**
 * A text with blanks: words flow and wrap like text, and each blank is drawn by [blank] in its place.
 * A word and a blank written without a space between them (`{{is}}.`) stay together.
 */
@Composable
fun InlineFlow(inline: Inline, modifier: Modifier = Modifier, blank: @Composable (InlineNode.Blank) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (line in chunks(inline)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                for (chunk in line) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        for (piece in chunk) when (piece) {
                            is Piece.Words -> Text(piece.text)
                            is Piece.Gap -> blank(piece.blank)
                        }
                    }
                }
            }
        }
    }
}

/** Part of a chunk: styled text without spaces, or a blank. */
internal sealed interface Piece {
    data class Words(val text: AnnotatedString) : Piece
    data class Gap(val blank: InlineNode.Blank) : Piece
}

/** The text as lines (split at line breaks) of chunks (split at spaces) of pieces. */
internal fun chunks(inline: Inline): List<List<List<Piece>>> {
    val lines = mutableListOf<MutableList<List<Piece>>>(mutableListOf())
    var chunk = mutableListOf<Piece>()
    var text = AnnotatedString.Builder()

    fun endText() {
        if (text.length > 0) chunk += Piece.Words(text.toAnnotatedString())
        text = AnnotatedString.Builder()
    }
    fun endChunk() {
        endText()
        if (chunk.isNotEmpty()) lines.last() += chunk
        chunk = mutableListOf()
    }
    fun walk(nodes: List<InlineNode>, bold: Boolean, italic: Boolean) {
        for (node in nodes) when (node) {
            is InlineNode.Text -> node.text.forEach { c ->
                if (c.isWhitespace()) endChunk() else text.withStyle(style(bold, italic)) { append(c) }
            }
            is InlineNode.Bold -> walk(node.children, true, italic)
            is InlineNode.Italic -> walk(node.children, bold, true)
            is InlineNode.Blank -> {
                endText()
                chunk += Piece.Gap(node)
            }
            InlineNode.LineBreak -> {
                endChunk()
                lines.add(mutableListOf())
            }
        }
    }
    walk(inline.nodes, bold = false, italic = false)
    endChunk()
    return lines
}

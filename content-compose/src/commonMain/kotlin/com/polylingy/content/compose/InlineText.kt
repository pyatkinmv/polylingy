package com.polylingy.content.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.polylingy.content.Inline
import com.polylingy.content.InlineNode
import com.polylingy.content.CourseStyle
import com.polylingy.content.MarkTone
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextDecoration

/**
 * A text with markup and no blanks, as one paragraph. Annotated words (`[老师](lǎoshī)`) show their annotation over
 * them while annotations are on ([ContentTheme]); off, only the words show.
 */
@Composable
fun InlineText(inline: Inline, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current) {
    if (inline.hasAnnotations && LocalShowAnnotations.current) {
        InlineFlow(inline, modifier, style) { Text(it.answer, style = style) }
    } else {
        Text(inline.annotated(rememberMarkPaint()), modifier, style = style.copy(textDirection = if (inline.runsRightToLeft()) TextDirection.Rtl else TextDirection.Ltr))
    }
}

/** Bold and italic as text styles; blanks, which can't be shown here, as their answer; annotated words as the words. */
fun Inline.annotated(): AnnotatedString = annotated(MarkPaint.Default)

/** The same with marks painted by [paint]: the palette of the course and the style it is drawn in. */
internal fun Inline.annotated(paint: MarkPaint): AnnotatedString = buildAnnotatedString { append(nodes, bold = false, italic = false, paint) }

/** What a mark (`==text==`) looks like: the five marks of a palette, drawn in a style: soft — on a coloured background; strict — underlined. */
internal class MarkPaint(private val marks: List<MarkColor>, private val style: CourseStyle) {
    fun spanStyle(tone: MarkTone): SpanStyle {
        val mark = marks[(tone.number - 1).coerceIn(0, marks.lastIndex)]
        return when (style) {
            CourseStyle.SOFT -> SpanStyle(background = mark.fill, fontWeight = FontWeight.SemiBold)
            CourseStyle.STRICT -> SpanStyle(color = mark.strong, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)
        }
    }

    companion object {
        val Default = MarkPaint(Palettes.default.colors.marks, CourseStyle.SOFT)
    }
}

@Composable
internal fun rememberMarkPaint(): MarkPaint {
    val colors = colors
    val style = LocalCourseStyle.current
    return remember(colors, style) { MarkPaint(colors.marks, style) }
}

private fun AnnotatedString.Builder.append(nodes: List<InlineNode>, bold: Boolean, italic: Boolean, paint: MarkPaint) {
    for (node in nodes) when (node) {
        is InlineNode.Text -> appendScaled(node.text, bold, italic)
        is InlineNode.Bold -> append(node.children, true, italic, paint)
        is InlineNode.Italic -> append(node.children, bold, true, paint)
        is InlineNode.Marked -> withStyle(paint.spanStyle(node.tone)) { append(node.children, bold, italic, paint) }
        is InlineNode.Blank -> appendScaled(node.answer, bold, italic)
        is InlineNode.Annotated -> appendScaled(node.base, bold, italic)
        // A line break between two enlarged runs is enlarged too: with a gap of plain text between them the second run's size
        // would be taken from the first still open (1.4 x 1.4).
        InlineNode.LineBreak -> {
            val previous = toAnnotatedString().text.lastOrNull { !it.isWhitespace() }
            if (previous != null && isRightToLeft(previous)) withStyle(SpanStyle(fontSize = RightToLeftScale.em)) { append('\n') } else append('\n')
        }
    }
}

/** [text] in the given style, its letters of right-to-left scripts larger ([RightToLeftScale]); spaces go with the letters before them. */
private fun AnnotatedString.Builder.appendScaled(text: String, bold: Boolean, italic: Boolean) {
    var start = 0
    var big = false
    fun flush(end: Int) {
        if (end > start) withStyle(style(bold, italic, big)) { append(text.substring(start, end)) }
        start = end
    }
    for ((i, c) in text.withIndex()) {
        val isBig = if (c.isWhitespace()) big else isRightToLeft(c)
        if (isBig != big) {
            flush(i)
            big = isBig
        }
    }
    flush(text.length)
}

private fun style(bold: Boolean, italic: Boolean, big: Boolean = false) = SpanStyle(
    fontSize = if (big) RightToLeftScale.em else TextUnit.Unspecified,
    fontWeight = if (bold) FontWeight.SemiBold else null,
    fontStyle = if (italic) FontStyle.Italic else null,
)

/**
 * A text with blanks: words flow and wrap like text, and each blank is drawn by [blank] in its place.
 * A word and a blank written without a space between them (`{{is}}.`) stay together; there the blank gives way,
 * so a long answer shrinks its field instead of pushing the word into a column of letters.
 * Text without spaces (Chinese, Japanese) may wrap after any of its characters; punctuation stays with the one before.
 * An annotated word carries its annotation over it while annotations are on, with a little room around it, so words
 * of such text are written without spaces between them; a line with annotations lines up its words at the bottom.
 */
@Composable
fun InlineFlow(inline: Inline, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current, blank: @Composable (InlineNode.Blank) -> Unit) {
    val annotations = LocalShowAnnotations.current
    val rightToLeft = inline.runsRightToLeft()
    Direction(rightToLeft) {
      CompositionLocalProvider(LocalTextStyle provides style.copy(textDirection = if (rightToLeft) TextDirection.Rtl else TextDirection.Ltr)) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (line in chunks(inline)) {
                // An empty line (two line breaks) parts paragraphs, as in a letter.
                if (line.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    continue
                }
                val annotated = annotations && line.any { chunk -> chunk.pieces.any { it is Piece.Note } }
                FlowRow(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = if (annotated) Alignment.Bottom else Alignment.CenterVertically,
                ) {
                    for (chunk in line) {
                        Row(Modifier.padding(end = if (chunk.spaceAfter) 5.dp else 0.dp), verticalAlignment = if (annotated) Alignment.Bottom else Alignment.CenterVertically) {
                            for (piece in chunk.pieces) when (piece) {
                                is Piece.Words -> Text(piece.text)
                                // A little room on both sides keeps the annotations of neighbouring words apart.
                                is Piece.Note -> Column(Modifier.padding(horizontal = if (annotations) 2.dp else 0.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (annotations) Text(piece.annotation, style = style.copy(fontSize = style.fontSize * 0.7f, color = colors.muted, fontWeight = FontWeight.Normal, fontStyle = FontStyle.Normal, letterSpacing = 0.02.em))
                                    Text(piece.base)
                                }
                                is Piece.Gap -> Box(Modifier.weight(1f, fill = false)) { blank(piece.blank) }
                            }
                        }
                    }
                }
            }
        }
      }
    }
}

/** Part of a chunk: styled text without spaces, an annotated word, or a blank. */
internal sealed interface Piece {
    data class Words(val text: AnnotatedString) : Piece
    data class Note(val base: AnnotatedString, val annotation: String) : Piece
    data class Gap(val blank: InlineNode.Blank) : Piece
}

/** Pieces that stay together on one line; [spaceAfter] — a space followed them in the text. */
internal data class Chunk(val pieces: List<Piece>, val spaceAfter: Boolean)

/** Characters that may start a line on their own: Chinese and Japanese characters. */
internal fun Char.breaksAnywhere(): Boolean = code in 0x3040..0x30FF || code in 0x3400..0x4DBF || code in 0x4E00..0x9FFF || code in 0xF900..0xFAFF

/** The text as lines (split at line breaks) of chunks (split at spaces, and between characters of text without spaces). */
internal fun chunks(inline: Inline): List<List<Chunk>> {
    val lines = mutableListOf<MutableList<Chunk>>(mutableListOf())
    var chunk = mutableListOf<Piece>()
    var text = AnnotatedString.Builder()

    fun endText() {
        if (text.length > 0) chunk += Piece.Words(text.toAnnotatedString())
        text = AnnotatedString.Builder()
    }
    fun endChunk(spaceAfter: Boolean) {
        endText()
        if (chunk.isNotEmpty()) lines.last() += Chunk(chunk, spaceAfter)
        else if (spaceAfter && lines.last().isNotEmpty()) lines.last()[lines.last().lastIndex] = lines.last().last().copy(spaceAfter = true)
        chunk = mutableListOf()
    }
    fun walk(nodes: List<InlineNode>, bold: Boolean, italic: Boolean) {
        for (node in nodes) when (node) {
            is InlineNode.Text -> node.text.forEach { c ->
                when {
                    c.isWhitespace() -> endChunk(spaceAfter = true)
                    c.breaksAnywhere() -> {
                        endChunk(spaceAfter = false)
                        text.withStyle(style(bold, italic)) { append(c) }
                    }
                    else -> text.withStyle(style(bold, italic, isRightToLeft(c))) { append(c) }
                }
            }
            is InlineNode.Bold -> walk(node.children, true, italic)
            is InlineNode.Italic -> walk(node.children, bold, true)
            // A highlight is not drawn inside text with annotations: the words show, the colour does not.
            is InlineNode.Marked -> walk(node.children, bold, italic)
            is InlineNode.Blank -> {
                endText()
                chunk += Piece.Gap(node)
            }
            is InlineNode.Annotated -> {
                endChunk(spaceAfter = false)
                chunk += Piece.Note(buildAnnotatedString { appendScaled(node.base, bold, italic) }, node.annotation)
            }
            InlineNode.LineBreak -> {
                endChunk(spaceAfter = false)
                lines.add(mutableListOf())
            }
        }
    }
    walk(inline.nodes, bold = false, italic = false)
    endChunk(spaceAfter = false)
    return lines
}

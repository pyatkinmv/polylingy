package com.polylingy.app.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.polylingy.app.ui.Look

/** The editor shows at most this many lines; a longer text scrolls inside it. */
const val EDITOR_LINES = 16

private val Gutter = Color(0xFFF6F2E8)
private val Ghost = Color(0xFFB5AC9A)
private val ErrorLine = Color(0xFFFBE9E6)
private val GutterWidth = 46.dp
private val PadX = 14.dp
private val PadY = 12.dp

/**
 * A plain code editor for JSON: monospace, line numbers on the left, [errorLines] (1-based) marked in red.
 * It is [EDITOR_LINES] lines high and scrolls beyond that. While empty it shows [placeholder] in grey —
 * only drawn, so it can't be selected or copied; typing replaces it.
 */
@Composable
fun JsonEditor(text: String, onChange: (String) -> Unit, placeholder: String, errorLines: Set<Int>, modifier: Modifier = Modifier) {
    // No trimming of the line height: a line number is a one-line text and must sit exactly on its line.
    val style = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.5.sp,
        lineHeight = 22.sp,
        color = Look.Ink,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val density = LocalDensity.current
    val lineHeight = with(density) { style.lineHeight.toDp() }
    val shape = RoundedCornerShape(10.dp)
    val starts = remember(text) { lineStarts(text) }

    Box(
        modifier
            .fillMaxWidth()
            .height(lineHeight * EDITOR_LINES + PadY * 2)
            .clip(shape)
            .background(Look.Card, shape)
            .border(1.dp, Look.LineHover, shape),
    ) {
        Box(Modifier.width(GutterWidth).fillMaxHeight().background(Gutter))
        Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            // Line numbers, each at the top of its line (a long line wraps, so it can take more than one row).
            Box(Modifier.matchParentSize().padding(top = PadY)) {
                val tops: List<Int> = if (text.isEmpty()) placeholder.lines().indices.map { with(density) { (lineHeight * it).roundToPx() } }
                else layout?.let { l -> starts.map { l.getLineTop(l.getLineForOffset(it.coerceAtMost(text.length))).toInt() } }.orEmpty()
                tops.forEachIndexed { i, top ->
                    Text(
                        "${i + 1}",
                        Modifier.width(GutterWidth - 10.dp).offset { IntOffset(0, top) },
                        style = style.copy(color = if (i + 1 in errorLines) Look.Wrong else Ghost, textAlign = TextAlign.End),
                    )
                }
            }
            BasicTextField(
                value = text,
                onValueChange = onChange,
                textStyle = style,
                cursorBrush = SolidColor(Look.Ink),
                onTextLayout = { layout = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = GutterWidth + PadX, end = PadX, top = PadY, bottom = PadY)
                    .semantics { contentDescription = "JSON" }
                    .drawBehind {
                        val l = layout ?: return@drawBehind
                        for (line in errorLines) {
                            val start = starts.getOrNull(line - 1) ?: continue
                            val end = (starts.getOrNull(line)?.minus(1) ?: text.length).coerceAtLeast(start)
                            val top = l.getLineTop(l.getLineForOffset(start))
                            val bottom = l.getLineBottom(l.getLineForOffset(end))
                            val pad = PadX.toPx()
                            drawRect(ErrorLine, Offset(-pad, top), Size(size.width + pad * 2, bottom - top))
                        }
                    },
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) Text(placeholder, style = style.copy(color = Ghost))
                        inner()
                    }
                },
            )
        }
    }
}

/** Offsets where each line of [text] starts. */
private fun lineStarts(text: String): List<Int> = buildList {
    add(0)
    text.forEachIndexed { i, c -> if (c == '\n') add(i + 1) }
}

package com.polylingy.content.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.polylingy.content.Block
import com.polylingy.content.Cell
import com.polylingy.content.Content
import com.polylingy.content.Image
import com.polylingy.content.Inline

/** Below this width an aside goes under the content instead of beside it. */
private val NarrowWidth = 560.dp
private val AsideMaxWidth = 280.dp

/** A page of content: unit theory or an explanation. [onLink] is called for a "see also" link. */
@Composable
fun ContentView(content: Content, modifier: Modifier = Modifier, onLink: (Block.Link) -> Unit = {}) {
    when (content) {
        is Content.Blocks -> Blocks(content.blocks, modifier, onLink)
        is Content.Unknown -> NewerVersion(modifier)
    }
}

@Composable
private fun Blocks(blocks: List<Block>, modifier: Modifier = Modifier, onLink: (Block.Link) -> Unit, spacing: Int = 14) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.dp)) {
        for (block in blocks) BlockView(block, onLink)
    }
}

/** One block, laid out by the rules of docs/content-format.md, section 4. */
@Composable
fun BlockView(block: Block, onLink: (Block.Link) -> Unit = {}) {
    when (block) {
        is Block.Section -> Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(block.label, Modifier.width(32.dp), style = MaterialTheme.typography.headlineMedium, color = colors.accent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                block.title?.let { InlineText(it, style = MaterialTheme.typography.titleMedium) }
                Blocks(block.blocks, onLink = onLink, spacing = 10)
            }
        }
        is Block.Text -> InlineText(block.text)
        is Block.Situation -> Box(
            Modifier.fillMaxWidth().border(1.dp, colors.line, RoundedCornerShape(10.dp)).padding(16.dp),
        ) {
            WithAside(block.aside, asideFirst = false, onLink = onLink) { Blocks(block.blocks, onLink = onLink, spacing = 10) }
        }
        is Block.Picture -> Picture(block.image, Modifier.fillMaxWidth())
        is Block.Table -> CellTable(block.head, block.rows)
        is Block.Quote -> WithAside(block.aside, asideFirst = true, onLink = onLink) {
            Column(
                Modifier.background(colors.panel, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                InlineText(block.text, style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic))
                block.who?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.muted) }
            }
        }
        is Block.Examples -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (example in block.items) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", color = colors.muted)
                val muted = colors.muted
                Text(
                    buildAnnotatedString {
                        append(example.text.annotated())
                        example.not?.let { not ->
                            withStyle(SpanStyle(color = muted)) {
                                append("  (")
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("not ") }
                                append(not.annotated())
                                append(")")
                            }
                        }
                    },
                )
            }
        }
        is Block.Words -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (word in block.words) Text(
                word,
                Modifier.background(colors.panel, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp),
                fontWeight = FontWeight.Medium,
            )
        }
        is Block.Tip -> Box(Modifier.fillMaxWidth().background(colors.accentSoft, RoundedCornerShape(10.dp)).padding(14.dp)) {
            Blocks(block.blocks, onLink = onLink, spacing = 8)
        }
        is Block.SeeAlso -> FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("See also", color = colors.muted)
            for (link in block.links) Text(
                link.theory?.let { "Section $it" } ?: "Another unit",
                Modifier.clickable { onLink(link) },
                color = colors.accent,
                fontWeight = FontWeight.Medium,
            )
        }
        is Block.Unknown -> NewerVersion()
    }
}

/** [main] with an optional [aside] beside it — at most 35 % wide and [AsideMaxWidth] — or under it on a narrow width. */
@Composable
private fun WithAside(aside: Block?, asideFirst: Boolean, onLink: (Block.Link) -> Unit, main: @Composable () -> Unit) {
    if (aside == null) return main()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < NarrowWidth) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                main()
                BlockView(aside, onLink)
            }
        } else {
            val asideWidth = min(maxWidth * 0.35f, AsideMaxWidth)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                if (asideFirst) Box(Modifier.width(asideWidth)) { BlockView(aside, onLink) }
                Box(Modifier.weight(1f)) { main() }
                if (!asideFirst) Box(Modifier.width(asideWidth)) { BlockView(aside, onLink) }
            }
        }
    }
}

/** A picture as wide as its place allows, as tall as its proportions say; its description when it can't be loaded. */
@Composable
fun Picture(image: Image, modifier: Modifier = Modifier) {
    val bitmap = LocalImageLoader.current.load(image.src)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (bitmap != null) {
            Image(
                bitmap,
                contentDescription = image.alt,
                modifier = Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(
                Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(colors.panel, RoundedCornerShape(8.dp))
                    .semantics { contentDescription = image.alt }.padding(12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(image.alt, color = colors.muted, style = MaterialTheme.typography.bodySmall) }
        }
        image.caption?.let { InlineText(it, style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic, color = colors.muted)) }
    }
}

@Composable
private fun NewerVersion(modifier: Modifier = Modifier) {
    Text(
        "This part needs a newer version of Polylingy.",
        modifier.fillMaxWidth().background(colors.panel, RoundedCornerShape(8.dp)).padding(12.dp),
        color = colors.muted,
        style = MaterialTheme.typography.bodySmall,
    )
}

/** Where a table cell goes: its row, its column and how many rows it covers. */
internal data class CellPlace(val row: Int, val column: Int, val rowSpan: Int)

/** Places cells into columns, skipping columns that a cell above still covers (same rule as validation). */
internal fun place(rows: List<List<Cell>>): List<List<CellPlace>> {
    var spans = mapOf<Int, Int>()
    return rows.mapIndexed { r, row ->
        val covered = spans.keys
        val next = spans.filterValues { it > 1 }.mapValues { it.value - 1 }.toMutableMap()
        var column = 0
        val placed = row.map { cell ->
            while (column in covered) column++
            CellPlace(r, column, cell.rowSpan.coerceIn(1, rows.size - r)).also {
                if (it.rowSpan > 1) next[column] = it.rowSpan - 1
                column++
            }
        }
        spans = next
        placed
    }
}

/**
 * A theory table: columns as wide as their content, a cell may span rows. A table wider than its place
 * scrolls sideways inside its frame.
 */
@Composable
private fun CellTable(head: List<Inline>, rows: List<List<Cell>>) {
    val places = place(rows)
    val all = buildList {
        head.forEachIndexed { c, cell -> add(Triple(CellPlace(0, c, 1), cell, true)) }
        val offset = if (head.isEmpty()) 0 else 1
        rows.forEachIndexed { r, row -> row.forEachIndexed { c, cell -> add(Triple(places[r][c].copy(row = places[r][c].row + offset), cell.text, false)) } }
    }
    Box(Modifier.horizontalScroll(rememberScrollState()).border(1.dp, colors.line, RoundedCornerShape(8.dp))) {
        Layout(
            content = {
                for ((_, text, isHead) in all) {
                    InlineText(
                        text,
                        Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = if (isHead) MaterialTheme.typography.labelLarge.copy(color = colors.muted) else MaterialTheme.typography.bodyMedium,
                    )
                }
            },
        ) { measurables, _ ->
            val columns = (all.maxOfOrNull { it.first.column } ?: -1) + 1
            val widths = IntArray(columns)
            measurables.forEachIndexed { i, m -> val col = all[i].first.column; widths[col] = maxOf(widths[col], m.maxIntrinsicWidth(Constraints.Infinity)) }
            val placeables = measurables.mapIndexed { i, m -> m.measure(Constraints.fixedWidth(widths[all[i].first.column])) }
            val rowCount = (all.maxOfOrNull { it.first.row } ?: -1) + 1
            val heights = IntArray(rowCount)
            all.forEachIndexed { i, (place) -> if (place.rowSpan == 1) heights[place.row] = maxOf(heights[place.row], placeables[i].height) }
            all.forEachIndexed { i, (place) ->
                if (place.rowSpan > 1) {
                    val last = place.row + place.rowSpan - 1
                    val have = (place.row..last).sumOf { heights[it] }
                    if (placeables[i].height > have) heights[last] += placeables[i].height - have
                }
            }
            val xs = widths.runningFold(0) { acc, w -> acc + w }
            val ys = heights.toList().runningFold(0) { acc, h -> acc + h }
            layout(xs.last(), ys.last()) {
                all.forEachIndexed { i, (place) -> placeables[i].place(xs[place.column], ys[place.row]) }
            }
        }
    }
}

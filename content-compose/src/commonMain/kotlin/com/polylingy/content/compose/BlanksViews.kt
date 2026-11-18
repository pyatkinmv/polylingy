package com.polylingy.content.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.polylingy.content.Exercise
import com.polylingy.content.Inline
import com.polylingy.content.InlineNode
import com.polylingy.content.ItemCheck
import com.polylingy.content.ItemResult
import com.polylingy.content.Response

/** Index of each item's first blank in [Response.Blanks.values]. */
internal fun offsets(texts: List<Inline>): List<Int> = texts.runningFold(0) { acc, text -> acc + text.blanks.size }

/**
 * [text] with its blanks — fields to type into, or the checked answers — numbered from [offset]. A blank's hint
 * (the verb to use, say) shows in grey inside its empty field, where it doesn't push the sentence apart.
 */
@Composable
private fun BlankText(
    text: Inline,
    offset: Int,
    response: Response.Blanks,
    checks: List<ItemCheck>?,
    onChange: (Response) -> Unit,
    onSubmit: () -> Unit,
) {
    InlineFlow(text) { blank: InlineNode.Blank ->
        val index = offset + blank.index
        val given = response.values[index]
        if (checks == null) {
            BlankField(given, { onChange(Response.Blanks(response.values.toMutableList().also { list -> list[index] = it })) }, "Blank ${index + 1}", onSubmit, blank.hint)
        } else {
            CheckedAnswer(given, checks[index])
        }
    }
}

@Composable
internal fun GapsView(exercise: Exercise.Gaps, response: Response.Blanks, checks: List<ItemCheck>?, onChange: (Response) -> Unit, onSubmit: () -> Unit) {
    val offsets = offsets(exercise.items.map { it.text })
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        exercise.example?.let { example ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("0", Modifier.width(20.dp), color = colors.muted, fontWeight = FontWeight.SemiBold)
                InlineText(example, style = MaterialTheme.typography.bodyLarge.copy(color = colors.muted))
            }
        }
        exercise.items.forEachIndexed { i, item ->
            Item(numberOf(i, exercise.items.size), item.note, showNote = checks != null) {
                BlankText(item.text, offsets[i], response, checks, onChange, onSubmit)
                if (checks != null) item.text.blanks.mapNotNull { it.hint }.takeIf { it.isNotEmpty() }?.let { Text("(${it.joinToString(" / ")})", color = colors.muted, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
internal fun PicturesView(exercise: Exercise.Pictures, response: Response.Blanks, checks: List<ItemCheck>?, onChange: (Response) -> Unit, onSubmit: () -> Unit) {
    val offsets = offsets(exercise.items.map { it.text })
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            exercise.items.forEachIndexed { i, item ->
                Column(
                    Modifier.width(240.dp).background(colors.card, RoundedCornerShape(10.dp))
                        .border(1.dp, colors.line, RoundedCornerShape(10.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Picture(item.image, Modifier.fillMaxWidth())
                    BlankText(item.text, offsets[i], response, checks, onChange, onSubmit)
                    if (checks != null) {
                        item.note?.let { InlineText(it, style = MaterialTheme.typography.bodySmall.copy(color = colors.muted)) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TableView(exercise: Exercise.Table, response: Response.Blanks, checks: List<ItemCheck>?, onChange: (Response) -> Unit, onSubmit: () -> Unit) {
    val cells = exercise.rows.flatten()
    val offsets = offsets(cells)
    val width = exercise.rows.maxOf { it.size }
    // A clean grid: a shaded header in small capitals, a line between rows, a first column without blanks set apart.
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(colors.card, shape).border(1.dp, colors.line, shape)) {
        if (exercise.head.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().background(colors.panel).padding(horizontal = 6.dp)) {
                for (cell in exercise.head) Box(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 10.dp)) {
                    Text(cell.plainText().uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.muted, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        exercise.rows.forEachIndexed { r, row ->
            if (r > 0 || exercise.head.isNotEmpty()) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                row.forEachIndexed { c, cell ->
                    Box(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 10.dp)) {
                        if (c == 0 && cell.blanks.isEmpty()) InlineText(cell, style = LocalTextStyle.current.copy(color = colors.muted, fontWeight = FontWeight.SemiBold))
                        else BlankText(cell, offsets[r * width + c], response, checks, onChange, onSubmit)
                    }
                }
            }
        }
    }
}

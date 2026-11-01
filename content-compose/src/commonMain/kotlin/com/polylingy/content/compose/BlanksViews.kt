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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.polylingy.content.Exercise
import com.polylingy.content.Inline
import com.polylingy.content.InlineNode
import com.polylingy.content.ItemCheck
import com.polylingy.content.ItemResult
import com.polylingy.content.Response

/** Index of each item's first blank in [Response.Blanks.values]. */
private fun offsets(texts: List<Inline>): List<Int> = texts.runningFold(0) { acc, text -> acc + text.blanks.size }

/** [text] with its blanks — fields to type into, or the checked answers — numbered from [offset]. */
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
            BlankField(given, { onChange(Response.Blanks(response.values.toMutableList().also { list -> list[index] = it })) }, "Blank ${index + 1}", onSubmit)
        } else {
            CheckedAnswer(given, checks[index])
        }
    }
}

private fun hasMistake(checks: List<ItemCheck>?, from: Int, count: Int) =
    checks != null && (from until from + count).any { checks[it].result != ItemResult.CORRECT }

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
        WordBank(exercise.wordBank)
        exercise.items.forEachIndexed { i, item ->
            Item(i + 1, item.note, hasMistake(checks, offsets[i], item.text.blanks.size)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    BlankText(item.text, offsets[i], response, checks, onChange, onSubmit)
                    item.hint?.let { Text("($it)", color = colors.muted, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
internal fun PicturesView(exercise: Exercise.Pictures, response: Response.Blanks, checks: List<ItemCheck>?, onChange: (Response) -> Unit, onSubmit: () -> Unit) {
    val offsets = offsets(exercise.items.map { it.text })
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        WordBank(exercise.wordBank)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            exercise.items.forEachIndexed { i, item ->
                Column(
                    Modifier.width(240.dp).background(colors.card, RoundedCornerShape(10.dp))
                        .border(1.dp, colors.line, RoundedCornerShape(10.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Picture(item.image, Modifier.fillMaxWidth())
                    BlankText(item.text, offsets[i], response, checks, onChange, onSubmit)
                    if (hasMistake(checks, offsets[i], item.text.blanks.size)) {
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
    Column(Modifier.fillMaxWidth().background(colors.card, RoundedCornerShape(10.dp)).border(1.dp, colors.line, RoundedCornerShape(10.dp))) {
        if (exercise.head.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                for (cell in exercise.head) Box(Modifier.weight(1f).padding(8.dp)) {
                    InlineText(cell, style = MaterialTheme.typography.labelLarge.copy(color = colors.muted))
                }
            }
        }
        exercise.rows.forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                row.forEachIndexed { c, cell ->
                    Box(Modifier.weight(1f).padding(8.dp)) {
                        BlankText(cell, offsets[r * width + c], response, checks, onChange, onSubmit)
                    }
                }
            }
        }
    }
}

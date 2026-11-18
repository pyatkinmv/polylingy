package com.polylingy.content.compose

import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.polylingy.content.Exercise
import com.polylingy.content.ItemCheck
import com.polylingy.content.ItemResult
import com.polylingy.content.MAX_ANSWER_LENGTH
import com.polylingy.content.Response

/**
 * Each item's blank is a wide field for the whole answer. Once checked, the answer is coloured with the model answer
 * next to it, and a wrong one gets its reason under it ("Not found: …", "Wrong order: …").
 */
@Composable
internal fun ExpandView(exercise: Exercise.Expand, response: Response.Blanks, checks: List<ItemCheck>?, onChange: (Response) -> Unit, onSubmit: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        exercise.example?.let { example ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("0", Modifier.width(20.dp), color = colors.muted, fontWeight = FontWeight.SemiBold)
                InlineText(example, style = MaterialTheme.typography.bodyLarge.copy(color = colors.muted))
            }
        }
        exercise.items.forEachIndexed { i, item ->
            val check = checks?.get(i)
            Item(numberOf(i, exercise.items.size), item.note, showNote = check != null && check.result != ItemResult.CORRECT) {
                InlineFlow(item.text) {
                    val given = response.values[i]
                    if (check == null) {
                        BlankField(
                            given, { onChange(Response.Blanks(response.values.toMutableList().also { list -> list[i] = it })) },
                            "Answer ${i + 1}", onSubmit, maxLength = MAX_ANSWER_LENGTH, minChars = 34,
                        )
                    } else {
                        CheckedAnswer(given, check)
                    }
                }
                check?.reason?.let { Text(it, color = colors.wrong, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

/**
 * Each marked word is shown in italics with a ✓ (kept, the start) and a small field for a fix; typing a fix crosses
 * the word out, clicking ✓ clears the fix. Once checked: a kept word shows as "✓ word" in its result's colour, a fixed
 * one as the crossed-out word and the fix; the right answer follows a wrong one.
 */
@Composable
internal fun FixView(exercise: Exercise.Fix, response: Response.Blanks, checks: List<ItemCheck>?, onChange: (Response) -> Unit, onSubmit: () -> Unit) {
    val offsets = offsets(exercise.items.map { it.text })
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        exercise.items.forEachIndexed { i, item ->
            val marks = item.marks
            Item(numberOf(i, exercise.items.size), item.note, showNote = checks != null) {
                InlineFlow(item.text) { blank ->
                    val index = offsets[i] + blank.index
                    val mark = marks[blank.index]
                    val given = response.values[index]
                    val set = { value: String -> onChange(Response.Blanks(response.values.toMutableList().also { it[index] = value })) }
                    val check = checks?.get(index)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        when {
                            check == null -> {
                                Marked(mark.shown, crossed = given.isNotBlank())
                                Tick(kept = given.isBlank(), label = "Keep ${index + 1}") { set("") }
                                BlankField(given, set, "Fix ${index + 1}", onSubmit, placeholder = "fix…")
                            }
                            given.isBlank() -> {
                                Kept(mark.shown, check.result)
                                if (check.result != ItemResult.CORRECT) Text(check.rightAnswer, color = colors.correct, fontWeight = FontWeight.SemiBold)
                            }
                            else -> {
                                Marked(mark.shown, crossed = true)
                                CheckedAnswer(given, check)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The marked word as written, crossed out once a fix replaces it. */
@Composable
private fun Marked(word: String, crossed: Boolean) {
    Text(
        word,
        color = if (crossed) colors.muted else colors.ink,
        fontStyle = FontStyle.Italic,
        textDecoration = if (crossed) TextDecoration.LineThrough else TextDecoration.Underline,
    )
}

/** The ✓ before the fix field: filled while the word is kept; clicking it keeps the word again. */
@Composable
private fun Tick(kept: Boolean, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(7.dp)
    DisableSelection {
    Text(
        "✓",
        Modifier.clip(shape)
            .background(if (kept) colors.correctBg else colors.card, shape)
            .border(if (kept) 1.5.dp else 1.dp, if (kept) colors.correct else BlankEdge, shape)
            .semantics { contentDescription = label; selected = kept }
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 2.dp),
        color = if (kept) colors.correct else colors.muted,
        fontWeight = FontWeight.Bold,
    )
    }
}

/** A checked word that was kept: "✓ word" in the colour of its result. */
@Composable
private fun Kept(word: String, result: ItemResult) {
    val (fg, bg) = when (result) {
        ItemResult.CORRECT -> colors.correct to colors.correctBg
        ItemResult.TYPO -> colors.typo to colors.typoBg
        ItemResult.WRONG -> colors.wrong to colors.wrongBg
    }
    Text(
        "✓ $word",
        Modifier.background(bg, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp),
        color = fg,
        fontWeight = FontWeight.SemiBold,
        fontStyle = FontStyle.Italic,
    )
}

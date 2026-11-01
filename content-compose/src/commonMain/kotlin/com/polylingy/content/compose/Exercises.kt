package com.polylingy.content.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.polylingy.content.Block
import com.polylingy.content.Exercise
import com.polylingy.content.ItemCheck
import com.polylingy.content.ItemResult
import com.polylingy.content.Response

/**
 * One exercise, any type: the instruction, then the exercise itself. Before checking, [response] is what the learner
 * has entered and [onChange] reports edits; after checking, [checks] (one per item) shows what was right, and the
 * explanation follows. The view never decides what is right: it only shows [checks].
 *
 * [onSubmit] is Enter in a blank. [seed] fixes the shuffled order of options, sides and words for this showing.
 */
@Composable
fun ExerciseView(
    exercise: Exercise,
    response: Response,
    checks: List<ItemCheck>?,
    onChange: (Response) -> Unit,
    modifier: Modifier = Modifier,
    onSubmit: () -> Unit = {},
    onLink: (Block.Link) -> Unit = {},
    seed: Int = 0,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        exercise.instruction?.let { InlineText(it, style = MaterialTheme.typography.titleMedium) }
        when (exercise) {
            is Exercise.Gaps -> GapsView(exercise, response as Response.Blanks, checks, onChange, onSubmit)
            is Exercise.Pictures -> PicturesView(exercise, response as Response.Blanks, checks, onChange, onSubmit)
            is Exercise.Table -> TableView(exercise, response as Response.Blanks, checks, onChange, onSubmit)
            is Exercise.Choice -> ChoiceView(exercise, response as Response.Choice, checks, onChange, seed)
            is Exercise.Match -> MatchView(exercise, response as Response.Match, checks, onChange, seed)
            is Exercise.Order -> OrderView(exercise, response as Response.Order, checks, onChange, seed)
            is Exercise.Unknown -> Text("This exercise needs a newer version of Polylingy.", color = colors.muted)
        }
        if (checks != null) exercise.explanation?.let { ExplanationView(it, onLink) }
    }
}

/** The explanation under a checked exercise: a free page of blocks. */
@Composable
fun ExplanationView(content: com.polylingy.content.Content, onLink: (Block.Link) -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().background(colors.card, RoundedCornerShape(10.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Explanation", style = MaterialTheme.typography.labelLarge, color = colors.muted)
        ContentView(content, onLink = onLink)
    }
}

/** A numbered item with an optional note under it, shown once the item has a mistake. */
@Composable
internal fun Item(number: Int, note: com.polylingy.content.Inline?, showNote: Boolean, content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("$number", Modifier.width(20.dp), color = colors.muted, fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            content()
            if (showNote && note != null) InlineText(note, style = MaterialTheme.typography.bodySmall.copy(color = colors.muted))
        }
    }
}

@Composable
internal fun WordBank(words: List<String>) {
    if (words.isEmpty()) return
    FlowRow(Modifier.semantics { contentDescription = "Word bank" }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (word in words) Text(
            word,
            Modifier.background(colors.accentSoft, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp),
            color = colors.accent,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * A blank to type into. Tab and Shift+Tab move between blanks; Enter submits. Its width follows what is typed,
 * so it doesn't give away the length of the answer.
 */
@Composable
internal fun BlankField(value: String, onValueChange: (String) -> Unit, label: String, onSubmit: () -> Unit) {
    val focus = LocalFocusManager.current
    val line = colors.ink
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.replace("\n", "")) },
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions.Default,
        modifier = Modifier
            .width((maxOf(5, value.length + 1) * 10).dp)
            .semantics { contentDescription = label }
            .onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (it.key) {
                    Key.Tab -> { focus.moveFocus(if (it.isShiftPressed) FocusDirection.Previous else FocusDirection.Next); true }
                    Key.Enter, Key.NumPadEnter -> { onSubmit(); true }
                    else -> false
                }
            }
            .drawBehind { drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx()) }
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

/** What a checked blank shows: what was given, coloured by its result, and the right answer unless it was right. */
@Composable
internal fun CheckedAnswer(given: String, check: ItemCheck) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val (fg, bg) = when (check.result) {
            ItemResult.CORRECT -> colors.correct to colors.correctBg
            ItemResult.TYPO -> colors.typo to colors.typoBg
            ItemResult.WRONG -> colors.wrong to colors.wrongBg
        }
        Text(
            given.ifBlank { "—" },
            Modifier.background(bg, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp),
            color = fg,
            fontWeight = FontWeight.SemiBold,
            textDecoration = if (check.result == ItemResult.WRONG) TextDecoration.LineThrough else null,
        )
        if (check.result != ItemResult.CORRECT) Text(check.rightAnswer, color = colors.correct, fontWeight = FontWeight.SemiBold)
    }
}

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
import androidx.compose.ui.Alignment
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.polylingy.content.MAX_BLANK_LENGTH
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

/** A numbered item with an optional note under it, shown once the exercise is checked. */
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
 * A blank to type into: a white box with a thin edge, so it reads as a field. [placeholder] (e.g. the verb to use)
 * shows in grey while it is empty. Typing only darkens the edge to ink ("quiet ink"): green is kept for right answers.
 * Tab and Shift+Tab move between blanks; Enter submits. Its width follows what is typed, so it doesn't give away the
 * length of the answer; it never gets wider than its place (it scrolls inside) and takes at most [MAX_BLANK_LENGTH]
 * characters, when its edge turns amber so the learner sees why.
 */
@Composable
internal fun BlankField(value: String, onValueChange: (String) -> Unit, label: String, onSubmit: () -> Unit, placeholder: String? = null) {
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val full = value.length >= MAX_BLANK_LENGTH
    val shape = RoundedCornerShape(8.dp)
    val edge = when {
        full -> BlankFull
        focused -> BlankFocus
        else -> BlankEdge
    }
    val shown = maxOf(value.length, placeholder?.length ?: 0, 6)
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.replace("\n", "").take(MAX_BLANK_LENGTH)) },
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.ink),
        keyboardOptions = KeyboardOptions.Default,
        modifier = Modifier
            .width(((minOf(shown, MAX_BLANK_LENGTH) + 2) * 9.5).dp)
            .semantics { contentDescription = label }
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (it.key) {
                    Key.Tab -> { focus.moveFocus(if (it.isShiftPressed) FocusDirection.Previous else FocusDirection.Next); true }
                    Key.Enter, Key.NumPadEnter -> { onSubmit(); true }
                    else -> false
                }
            },
        decorationBox = { inner ->
            Box(
                Modifier
                    .background(colors.card, shape)
                    .border(if (focused || full) 1.5.dp else 1.dp, edge, shape)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty() && placeholder != null) Text(placeholder, color = BlankPlaceholder, maxLines = 1)
                inner()
            }
        },
    )
}

/** The edges of a blank: resting, typing (ink), full (dark amber); and its placeholder — quiet, but clearly a field. */
internal val BlankEdge = androidx.compose.ui.graphics.Color(0xFFD6CCB8)
internal val BlankFocus = androidx.compose.ui.graphics.Color(0xFF4A453D)
private val BlankFull = androidx.compose.ui.graphics.Color(0xFFB07A10)
private val BlankPlaceholder = androidx.compose.ui.graphics.Color(0xFFB5AC9A)

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

package com.polylingy.content.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.polylingy.content.Exercise
import com.polylingy.content.Inline
import com.polylingy.content.ItemCheck
import com.polylingy.content.ItemResult
import com.polylingy.content.Response
import com.polylingy.content.Side
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlin.random.Random

private fun resultColors(result: ItemResult?, colors: ContentColors): Pair<Color, Color> = when (result) {
    ItemResult.CORRECT -> colors.correct to colors.correctBg
    ItemResult.TYPO -> colors.typo to colors.typoBg
    ItemResult.WRONG -> colors.wrong to colors.wrongBg
    null -> colors.ink to colors.card
}

/**
 * A rounded option to click: filled when [selected], coloured by [result] once checked. After the check the [right] option
 * is outlined in green with a tick, picked or not, so the right answer is shown where it already stands and is never written twice.
 */
@Composable
private fun Option(text: String, selected: Boolean, result: ItemResult?, enabled: Boolean, right: Boolean = false, onClick: () -> Unit) {
    val (fg, bg) = when {
        result != null && selected -> resultColors(result, colors)
        right -> colors.correct to colors.correctBg
        selected -> colors.onAccent to colors.accent
        else -> colors.ink to colors.card
    }
    InlineText(
        Inline(if (right && !selected) "✓ $text" else text),
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg, RoundedCornerShape(50))
            .border(if (right) 2.dp else 1.5.dp, if (right) colors.correct else if (selected) bg else colors.line, RoundedCornerShape(50))
            .semantics { this.selected = selected }
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        style = LocalTextStyle.current.copy(color = fg),
    )
}

/**
 * The options of a place in the order they are shown: shuffled, so the right one is not always first; but options that are
 * all numbers ("0", "1", "2" …) go in ascending order — shuffled numbers only make the eye search.
 */
internal fun optionsInOrder(options: List<String>, random: Random): List<String> {
    val numbers = options.map { it.trim().toDoubleOrNull() }
    return if (numbers.all { it != null }) options.sortedBy { it.trim().toDouble() } else options.shuffled(random)
}

/** Each place of a choice shows its options, shuffled, as pills in the sentence; each place is picked on its own. */
@Composable
internal fun ChoiceView(
    exercise: Exercise.Choice, response: Response.Choice, checks: List<ItemCheck>?, onChange: (Response) -> Unit, seed: Int,
    marked: String? = null, onMark: (String?) -> Unit = {},
) {
    val places = exercise.places
    val shuffled = remember(exercise.id, seed) { places.mapIndexed { p, place -> optionsInOrder(place.options, Random(seed + p)) } }
    val offsets = offsets(exercise.items.map { it.text })
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        exercise.items.forEachIndexed { i, item ->
            // Once checked, an item that has a mark can be chosen to see its mark in the text of the frame.
            val choosable = checks != null && item.mark != null
            val chosen = choosable && item.mark == marked
            Item(
                numberOf(i, exercise.items.size), item.note, showNote = checks != null,
                Modifier.then(if (chosen) Modifier.background(colors.accentSoft, RoundedCornerShape(8.dp)) else Modifier)
                    .then(if (choosable) Modifier.clickable(remember { MutableInteractionSource() }, indication = null, role = androidx.compose.ui.semantics.Role.Button) { onMark(item.mark) } else Modifier)
                    .padding(4.dp),
            ) {
                InlineFlow(item.text) { blank ->
                    val p = offsets[i] + blank.index
                    val check = checks?.get(p)
                    // Numbers read 0, 1, 2 from the left even in a right-to-left line: a row of digits has no other order.
                    val numbers = shuffled[p].all { it.trim().toDoubleOrNull() != null }
                    CompositionLocalProvider(if (numbers) LocalLayoutDirection provides LayoutDirection.Ltr else LocalLayoutDirection provides LocalLayoutDirection.current) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            for (option in shuffled[p]) {
                                Option(option, response.picked[p] == option, check?.result, enabled = checks == null, right = check != null && check.rightAnswer == option) {
                                    onChange(Response.Choice(response.picked.toMutableList().also { it[p] = option }))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The colours of matched pairs: a pair shows the same number on the same colour on both sides. */
private val PairColours = listOf(Color(0xFF1E5A7A), Color(0xFF2F7A4F), Color(0xFF9A4B12), Color(0xFF7A4E8C), Color(0xFF8C7210), Color(0xFF3F6F8F))

/** A pair's mark: its left side's number, on that number's colour. */
private data class PairMark(val number: Int) {
    val label: String get() = "$number"
    val colour: Color get() = PairColours[(number - 1) % PairColours.size]
}

@Composable
private fun SideCard(side: Side, badge: PairMark?, selected: Boolean, result: ItemResult?, enabled: Boolean, onClick: () -> Unit) {
    val (fg, bg) = resultColors(result, colors)
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clip(shape)
            .background(bg, shape)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) BlankFocus else colors.line, shape)
            .semantics { this.selected = selected }
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (badge != null) Box(Modifier.size(24.dp).background(badge.colour, CircleShape), contentAlignment = Alignment.Center) {
            Text(badge.label, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        when (side) {
            is Side.Text -> InlineText(side.text, style = MaterialTheme.typography.bodyLarge.copy(color = fg))
            is Side.Picture -> Box(Modifier.width(120.dp)) { Picture(side.image) }
        }
    }
}

/**
 * Left sides in order, right sides (with the extra ones) shuffled. Click a left side, then its match on the right:
 * the pair gets one number on one colour on both sides. Clicking either side of a pair undoes it.
 */
@Composable
internal fun MatchView(exercise: Exercise.Match, response: Response.Match, checks: List<ItemCheck>?, onChange: (Response) -> Unit, seed: Int) {
    val rights = exercise.rightSides
    val order = remember(exercise.id, seed) { rights.indices.shuffled(Random(seed)) }
    var selected by remember(exercise.id) { mutableStateOf<Int?>(null) }
    val leftOfRight = response.matched.withIndex().filter { it.value != null }.associate { it.value!! to it.index }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (checks == null) Text("Click an item on the left, then its match on the right. Click a pair to undo it.", color = colors.muted, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                exercise.pairs.forEachIndexed { i, pair ->
                    val matched = response.matched[i]
                    SideCard(pair[0], if (matched != null) PairMark(i + 1) else null, selected == i, checks?.get(i)?.result, enabled = checks == null) {
                        if (matched != null) onChange(Response.Match(response.matched.toMutableList().also { it[i] = null }))
                        else selected = if (selected == i) null else i
                    }
                    val check = checks?.get(i)
                    if (check != null && check.result != ItemResult.CORRECT) Text("→ ${check.rightAnswer}", color = colors.correct, style = MaterialTheme.typography.bodySmall)
                }
            }
            Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (r in order) {
                    val left = leftOfRight[r]
                    SideCard(rights[r], left?.let { PairMark(it + 1) }, false, null, enabled = checks == null && (selected != null || left != null)) {
                        val l = selected ?: run {
                            if (left != null) onChange(Response.Match(response.matched.toMutableList().also { it[left] = null }))
                            return@SideCard
                        }
                        val matched = response.matched.map { if (it == r) null else it }.toMutableList().also { it[l] = r }
                        onChange(Response.Match(matched))
                        selected = null
                    }
                }
            }
        }
    }
}

/**
 * A word as a small card. [placed] — it stands in the sentence; [result] colours it once checked; a [faded] word is the
 * place a used word leaves in the bank: it keeps the bank still and is neither clickable nor read out.
 */
@Composable
private fun Tile(word: String, result: ItemResult?, enabled: Boolean, faded: Boolean = false, onClick: () -> Unit) {
    val (fg, bg) = when {
        result != null -> resultColors(result, colors)
        faded -> colors.line to colors.panel
        else -> colors.ink to colors.card
    }
    val shape = RoundedCornerShape(10.dp)
    InlineText(
        Inline(word),
        Modifier
            .then(if (faded) Modifier.clearAndSetSemantics {} else Modifier)
            .clip(shape).background(bg, shape)
            .then(if (faded) Modifier.dashedBorder(colors.line, 10.dp) else Modifier.border(1.dp, if (result != null) bg else WordEdge, shape))
            .clickable(enabled = enabled && !faded, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        style = LocalTextStyle.current.copy(color = fg, fontWeight = FontWeight.Medium),
    )
}

/** An empty place for the next word: dashed; the one the next click fills is white with an ink edge, like a typing blank. */
@Composable
private fun Slot(next: Boolean) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .widthIn(min = 60.dp).height(36.dp)
            .background(if (next) colors.card else colors.panel.copy(alpha = 0.5f), shape)
            .then(if (next) Modifier.border(1.5.dp, BlankFocus, shape) else Modifier.dashedBorder(SlotEdge, 10.dp)),
    )
}

private val WordEdge = Color(0xFFD6CCB8)
private val SlotEdge = Color(0xFFC9BFAC)

/** A dashed rounded edge, for places that wait for something. */
private fun Modifier.dashedBorder(colour: Color, radius: androidx.compose.ui.unit.Dp) = drawBehind {
    val stroke = 1.5.dp.toPx()
    drawRoundRect(
        colour,
        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx()),
        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 5f))),
    )
}

/**
 * Each sentence: one place per word — the words placed so far (click one to take it back), then empty places with
 * the next one marked — its fixed ending, and the bank. Used words leave a faded place in the bank, so nothing jumps.
 */
@Composable
internal fun OrderView(exercise: Exercise.Order, response: Response.Order, checks: List<ItemCheck>?, onChange: (Response) -> Unit, seed: Int) {
    val shuffled = remember(exercise.id, seed) { exercise.items.mapIndexed { i, item -> item.words.shuffled(Random(seed + i)) } }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        exercise.items.forEachIndexed { i, item ->
            val placed = response.sentences[i]
            val check = checks?.get(i)
            fun update(words: List<String>) = onChange(Response.Order(response.sentences.toMutableList().also { it[i] = words }))
            Item(numberOf(i, exercise.items.size), item.note, showNote = check != null) {
              Direction(item.words.joinToString(" ").runsRightToLeft()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Whole phrases (the lines of a dialogue) go one under another, with one place for the next.
                    if (item.parts.isNotEmpty()) Column(
                        Modifier.fillMaxWidth().semantics { contentDescription = "Sentence ${i + 1}" },
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        placed.forEachIndexed { w, word ->
                            val right = item.parts.getOrNull(w)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${w + 1}", Modifier.width(18.dp), color = colors.muted, style = MaterialTheme.typography.bodySmall)
                                // Checked: each place shows what was put there, green or red, and for a wrong one what belongs there, on the same row.
                                if (check != null && right != null) FlowRow(
                                    Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                                    itemVerticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Tile(word, result = if (word == right) ItemResult.CORRECT else ItemResult.WRONG, enabled = false) {}
                                    if (word != right) {
                                        Text("→", color = colors.correct, fontWeight = FontWeight.SemiBold)
                                        Tile(right, result = ItemResult.CORRECT, enabled = false) {}
                                    }
                                } else Tile(word, result = check?.result, enabled = checks == null) { update(placed.toMutableList().also { it.removeAt(w) }) }
                            }
                        }
                        if (checks == null && placed.size < item.words.size) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${placed.size + 1}", Modifier.width(18.dp), color = colors.muted, style = MaterialTheme.typography.bodySmall)
                            Slot(next = true)
                            if (placed.isNotEmpty()) Text(
                                "Clear",
                                Modifier.padding(start = 10.dp).clip(RoundedCornerShape(4.dp)).clickable { update(emptyList()) }.padding(horizontal = 4.dp),
                                color = colors.accent,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    } else FlowRow(
                        Modifier.fillMaxWidth().heightIn(min = 40.dp).semantics { contentDescription = "Sentence ${i + 1}" },
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        placed.forEachIndexed { w, word ->
                            Tile(word, result = check?.result, enabled = checks == null) { update(placed.toMutableList().also { it.removeAt(w) }) }
                        }
                        if (checks == null) repeat(item.words.size - placed.size) { k -> Slot(next = k == 0) }
                        if (item.ending.isNotEmpty()) Text(item.ending, fontWeight = FontWeight.SemiBold)
                        if (checks == null && placed.isNotEmpty()) Text(
                            "Clear",
                            Modifier.padding(start = 10.dp).clip(RoundedCornerShape(4.dp)).clickable { update(emptyList()) }.padding(horizontal = 4.dp),
                            color = colors.accent,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (checks == null) {
                        val left = placed.groupingBy { it }.eachCount().toMutableMap()
                        FlowRow(Modifier.semantics { contentDescription = "Words ${i + 1}" }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (word in shuffled[i]) {
                                val used = (left[word] ?: 0) > 0
                                if (used) left[word] = left.getValue(word) - 1
                                Tile(word, result = null, enabled = true, faded = used) { update(placed + word) }
                            }
                        }
                        if (placed.isEmpty()) Direction(false) {
                            Text("Click the words in order. Click a placed word to put it back.", color = colors.muted, style = MaterialTheme.typography.bodySmall)
                        }
                    } else if (check != null && check.result != ItemResult.CORRECT && item.parts.isEmpty()) {
                        InlineText(Inline("→ ${check.rightAnswer}"), style = LocalTextStyle.current.copy(color = colors.correct, fontWeight = FontWeight.SemiBold))
                    }
                }
              }
            }
        }
    }
}

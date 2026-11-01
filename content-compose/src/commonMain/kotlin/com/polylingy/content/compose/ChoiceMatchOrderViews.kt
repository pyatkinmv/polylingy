package com.polylingy.content.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.polylingy.content.Exercise
import com.polylingy.content.ItemCheck
import com.polylingy.content.ItemResult
import com.polylingy.content.Response
import com.polylingy.content.Side
import kotlin.random.Random

private fun resultColors(result: ItemResult?, colors: ContentColors): Pair<Color, Color> = when (result) {
    ItemResult.CORRECT -> colors.correct to colors.correctBg
    ItemResult.TYPO -> colors.typo to colors.typoBg
    ItemResult.WRONG -> colors.wrong to colors.wrongBg
    null -> colors.ink to colors.card
}

/** A rounded option to click: filled when [selected], coloured by [result] once checked. */
@Composable
private fun Option(text: String, selected: Boolean, result: ItemResult?, enabled: Boolean, onClick: () -> Unit) {
    val (fg, bg) = when {
        result != null && selected -> resultColors(result, colors)
        selected -> colors.onAccent to colors.accent
        else -> colors.ink to colors.card
    }
    Text(
        text,
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg, RoundedCornerShape(50))
            .border(1.5.dp, if (selected) bg else colors.line, RoundedCornerShape(50))
            .semantics { this.selected = selected }
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        color = fg,
    )
}

@Composable
internal fun ChoiceView(exercise: Exercise.Choice, response: Response.Choice, checks: List<ItemCheck>?, onChange: (Response) -> Unit, seed: Int) {
    val shuffled = remember(exercise.id, seed) { exercise.items.mapIndexed { i, item -> item.options.shuffled(Random(seed + i)) } }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        exercise.items.forEachIndexed { i, item ->
            val check = checks?.get(i)
            Item(i + 1, item.note, check != null && check.result != ItemResult.CORRECT) {
                InlineFlow(item.text) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        for (option in shuffled[i]) {
                            Option(option, response.picked[i] == option, check?.result, enabled = checks == null) {
                                onChange(Response.Choice(response.picked.toMutableList().also { it[i] = option }))
                            }
                        }
                        if (check != null && check.result != ItemResult.CORRECT) Text("→ ${check.rightAnswer}", color = colors.correct, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun letter(i: Int) = ('A' + i).toString()

@Composable
private fun SideCard(side: Side, badge: String?, selected: Boolean, result: ItemResult?, enabled: Boolean, onClick: () -> Unit) {
    val (fg, bg) = resultColors(result, colors)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg, RoundedCornerShape(8.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.line, RoundedCornerShape(8.dp))
            .semantics { this.selected = selected }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(24.dp).background(if (badge != null) colors.accent else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
            if (badge != null) Text(badge, color = colors.onAccent, style = MaterialTheme.typography.labelMedium)
        }
        when (side) {
            is Side.Text -> InlineText(side.text, style = MaterialTheme.typography.bodyLarge.copy(color = fg))
            is Side.Picture -> Box(Modifier.width(120.dp)) { Picture(side.image) }
        }
    }
}

/**
 * Left sides in order, right sides (with the extra ones) shuffled. Click a left side, then its ending;
 * clicking a matched left side again undoes the pair.
 */
@Composable
internal fun MatchView(exercise: Exercise.Match, response: Response.Match, checks: List<ItemCheck>?, onChange: (Response) -> Unit, seed: Int) {
    val rights = exercise.rightSides
    val order = remember(exercise.id, seed) { rights.indices.shuffled(Random(seed)) }
    var selected by remember(exercise.id) { mutableStateOf<Int?>(null) }
    val leftOfRight = response.matched.withIndex().filter { it.value != null }.associate { it.value!! to it.index }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (checks == null) Text("Pick one on the left, then its ending on the right.", color = colors.muted, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                exercise.pairs.forEachIndexed { i, pair ->
                    val matched = response.matched[i]
                    SideCard(pair[0], if (matched != null) letter(i) else null, selected == i, checks?.get(i)?.result, enabled = checks == null) {
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
                    SideCard(rights[r], left?.let(::letter), false, null, enabled = checks == null && selected != null) {
                        val l = selected ?: return@SideCard
                        val matched = response.matched.map { if (it == r) null else it }.toMutableList().also { it[l] = r }
                        onChange(Response.Match(matched))
                        selected = null
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(word: String, placed: Boolean, result: ItemResult?, enabled: Boolean, onClick: () -> Unit) {
    val (fg, bg) = when {
        result != null -> resultColors(result, colors)
        placed -> colors.onAccent to colors.accent
        else -> colors.ink to colors.card
    }
    Text(
        word,
        Modifier.clip(RoundedCornerShape(8.dp)).background(bg, RoundedCornerShape(8.dp)).border(1.5.dp, if (placed) bg else colors.line, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        color = fg,
    )
}

/** Each sentence: the words placed so far (click one to take it back), its fixed ending, and the words left (click to place). */
@Composable
internal fun OrderView(exercise: Exercise.Order, response: Response.Order, checks: List<ItemCheck>?, onChange: (Response) -> Unit, seed: Int) {
    val shuffled = remember(exercise.id, seed) { exercise.items.mapIndexed { i, item -> item.words.shuffled(Random(seed + i)) } }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        exercise.items.forEachIndexed { i, item ->
            val placed = response.sentences[i]
            val check = checks?.get(i)
            fun update(words: List<String>) = onChange(Response.Order(response.sentences.toMutableList().also { it[i] = words }))
            Item(i + 1, item.note, check != null && check.result != ItemResult.CORRECT) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(
                        Modifier.fillMaxWidth().heightIn(min = 40.dp).semantics { contentDescription = "Sentence ${i + 1}" },
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        placed.forEachIndexed { w, word ->
                            Tile(word, placed = true, result = check?.result, enabled = checks == null) { update(placed.toMutableList().also { it.removeAt(w) }) }
                        }
                        if (item.ending.isNotEmpty()) Text(item.ending, Modifier.align(Alignment.CenterVertically), color = colors.muted)
                    }
                    if (checks == null) {
                        val left = shuffled[i].toMutableList().also { bank -> placed.forEach { bank.remove(it) } }
                        FlowRow(Modifier.semantics { contentDescription = "Words ${i + 1}" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (word in left) Tile(word, placed = false, result = null, enabled = true) { update(placed + word) }
                        }
                    } else if (check != null && check.result != ItemResult.CORRECT) {
                        Text("→ ${check.rightAnswer}", color = colors.correct, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

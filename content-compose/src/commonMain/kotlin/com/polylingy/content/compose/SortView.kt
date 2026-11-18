package com.polylingy.content.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.polylingy.content.Exercise
import com.polylingy.content.ItemCheck
import com.polylingy.content.ItemResult
import com.polylingy.content.Response
import kotlin.random.Random

/**
 * Words to sort: a pile of shuffled words on top, the groups under it. Click a word, then the group it belongs to; click a
 * placed word to take it back. A selected word stays marked until it is placed. Once checked, each word shows its colour,
 * and a wrong one is followed by the group it belongs to.
 */
@Composable
internal fun SortView(exercise: Exercise.Sort, response: Response.Sort, checks: List<ItemCheck>?, onChange: (Response) -> Unit, seed: Int) {
    val entries = exercise.entries
    val order = remember(exercise.id, seed) { entries.indices.shuffled(Random(seed)) }
    var selected by remember(exercise.id, seed) { mutableStateOf<Int?>(null) }
    fun place(entry: Int, group: Int?) = onChange(Response.Sort(response.placed.toMutableList().also { it[entry] = group }))
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (checks == null) {
            val pile = order.filter { response.placed[it] == null }
            FlowRow(
                Modifier.fillMaxWidth().heightIn(min = 40.dp).semantics { contentDescription = "Words to sort" },
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (e in pile) SortWord(entries[e].text, chosen = selected == e) { selected = if (selected == e) null else e }
                if (pile.isEmpty()) Text("Every word is in a group. Click one to take it back.", color = colors.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        @Composable
        fun GroupBox(g: Int, group: Exercise.SortGroup, modifier: Modifier) {
            val shape = RoundedCornerShape(12.dp)
            val ready = checks == null && selected != null
            Column(
                modifier
                    .clip(shape).background(colors.card, shape)
                    .border(if (ready) 1.5.dp else 1.dp, if (ready) colors.ink else colors.line, shape)
                    .semantics { contentDescription = "Group ${g + 1}" }
                    .clickable(enabled = ready) { selected?.let { place(it, g); selected = null } }
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InlineText(group.title, style = MaterialTheme.typography.labelLarge.copy(color = colors.muted, fontWeight = FontWeight.SemiBold))
                FlowRow(
                    Modifier.fillMaxWidth().heightIn(min = 36.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    for (e in order.filter { response.placed[it] == g }) {
                        val check = checks?.get(e)
                        Tile(entries[e].text, result = check?.result, enabled = checks == null) { place(e, null) }
                    }
                }
            }
        }
        if (exercise.groups.size <= 3) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            exercise.groups.forEachIndexed { g, group -> GroupBox(g, group, Modifier.weight(1f)) }
        } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            exercise.groups.forEachIndexed { g, group -> GroupBox(g, group, Modifier.fillMaxWidth()) }
        }
        if (checks != null) {
            val wrong = entries.indices.filter { checks[it].result != ItemResult.CORRECT }
            for (e in wrong) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Tile(entries[e].text, result = ItemResult.WRONG, enabled = false) {}
                Text("→", color = colors.correct, fontWeight = FontWeight.SemiBold)
                InlineText(exercise.groups[entries[e].group].title, style = MaterialTheme.typography.bodyMedium.copy(color = colors.correct, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

/** A word in the pile; [chosen] — it is the one the next click on a group will place. */
@Composable
private fun SortWord(word: String, chosen: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Text(
        word,
        Modifier
            .clip(shape).background(if (chosen) colors.correct else colors.card, shape)
            .border(if (chosen) 2.dp else 1.dp, if (chosen) colors.correct else WordEdge, shape)
            .semantics { this.selected = chosen }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        color = if (chosen) colors.onAccent else colors.ink,
        fontWeight = FontWeight.Medium,
    )
}

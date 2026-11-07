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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlin.uuid.Uuid
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

/**
 * A page of content: unit theory or an explanation. [onLink] is called for a "see also" link.
 * [highlight] is the id of a section to scroll to and light up for a moment, when the page is opened by a link.
 */
@Composable
fun ContentView(content: Content, modifier: Modifier = Modifier, onLink: (Block.Link) -> Unit = {}, highlight: Uuid? = null) {
    CompositionLocalProvider(LocalHighlight provides highlight) {
        when (content) {
            is Content.Blocks -> Blocks(content.blocks, modifier, onLink)
            is Content.Unknown -> NewerVersion(modifier)
        }
    }
}

private val LocalHighlight = staticCompositionLocalOf<Uuid?> { null }

/** How long a section opened by a link stays lit. */
private const val HIGHLIGHT_MILLIS = 2500L

/** Brings the section into view and lights it up for a while, if it is the one a link opened. */
@Composable
private fun Modifier.highlightedSection(id: Uuid?): Modifier {
    if (id == null || id != LocalHighlight.current) return this
    val requester = remember { BringIntoViewRequester() }
    var lit by remember { mutableStateOf(true) }
    val background by animateColorAsState(if (lit) colors.accentSoft else Color.Transparent, tween(600))
    LaunchedEffect(id) {
        requester.bringIntoView()
        delay(HIGHLIGHT_MILLIS)
        lit = false
    }
    return bringIntoViewRequester(requester)
        .semantics { contentDescription = "Linked section" }
        .background(background, RoundedCornerShape(10.dp))
        .padding(10.dp)
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
        is Block.Section -> {
            val label = block.label
            if (label != null) Row(Modifier.highlightedSection(block.id), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(label, Modifier.width(32.dp), style = MaterialTheme.typography.headlineMedium, color = colors.accent)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    block.title?.let { InlineText(it, style = MaterialTheme.typography.titleMedium) }
                    Blocks(block.blocks, onLink = onLink, spacing = 10)
                }
            } else Column(Modifier.highlightedSection(block.id).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                block.title?.let { InlineText(it, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, color = colors.accent)) }
                Blocks(block.blocks, onLink = onLink, spacing = 10)
            }
        }
        is Block.Text -> InlineText(block.text)
        is Block.Track -> TrackView(block)
        is Block.Video -> VideoView(block)
        is Block.Situation -> Box(
            Modifier.fillMaxWidth().border(1.dp, colors.line, RoundedCornerShape(10.dp)).padding(16.dp),
        ) {
            WithAside(block.aside, asideFirst = false, onLink = onLink) { Blocks(block.blocks, onLink = onLink, spacing = 10) }
        }
        is Block.Picture -> Picture(block.image, Modifier.fillMaxWidth())
        is Block.Table -> CellTable(block.head, block.rows)
        is Block.Dialogue -> Column(
            Modifier.fillMaxWidth().background(colors.card, RoundedCornerShape(10.dp)).border(1.dp, colors.line, RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp).semantics { contentDescription = "Dialogue" },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val names = block.lines.any { it.who != null }
            for (line in block.lines) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                line.audio?.let { PlayButton(it) }
                if (names) Box(Modifier.width(DialogueNameWidth)) {
                    line.who?.let { InlineText(it, style = MaterialTheme.typography.bodyMedium.copy(color = colors.muted, fontWeight = FontWeight.SemiBold)) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    InlineText(line.text, style = MaterialTheme.typography.bodyLarge)
                    line.translation?.let { Translation(it) }
                }
            }
        }
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
            for (example in block.items) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                example.audio?.let { PlayButton(it) } ?: Text("•", color = colors.muted)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val muted = colors.muted
                    if (example.text.hasAnnotations && example.not == null) InlineText(example.text)
                    else Text(
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
                    example.translation?.let { Translation(it) }
                }
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
            val names = LocalLinkNames.current
            for (link in block.links) Text(
                names.name(link) ?: "Theory",
                Modifier.clickable { onLink(link) },
                color = colors.accent,
                fontWeight = FontWeight.Medium,
            )
        }
        is Block.Unknown -> NewerVersion()
    }
}

/** A translation under a dialogue line or an example: small and grey, so the original stays first. */
@Composable
private fun Translation(text: Inline) =
    InlineText(text, style = MaterialTheme.typography.bodySmall.copy(color = colors.muted))

/** The names' column of a dialogue. */
private val DialogueNameWidth = 96.dp

/**
 * A round button that plays a course's sound by its `src` ([AudioPlayer]); [large] for the sound an exercise is about.
 * A long recording (see [Playback.docked]) is controlled in the docked player, and its button follows it: it shows
 * a pause sign while the recording plays and pauses it when pressed. [title] is what the docked player calls the recording.
 */
@Composable
fun PlayButton(src: String, large: Boolean = false, title: String? = null) {
    val player = LocalAudioPlayer.current
    val playback by player.playback.collectAsState()
    val dock = LocalDockTitle.current
    val loaded = playback.src == src && playback.docked
    TransportButton(playing = loaded && playback.playing, size = if (large) 64.dp else 28.dp, ring = if (large) 2.dp else 1.5.dp) {
        when {
            loaded && playback.playing -> player.pause()
            loaded -> player.resume()
            else -> {
                dock.text = title
                player.play(src)
            }
        }
    }
}

/** A recording of its own: its button and title in a card; the docked player takes over when it plays. */
@Composable
private fun TrackView(track: Block.Track) {
    val title = track.title.text
    Row(
        Modifier.fillMaxWidth().background(colors.card, RoundedCornerShape(10.dp)).border(1.dp, colors.line, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp).semantics { contentDescription = "Track" },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayButton(track.audio, large = false, title = title)
        InlineText(track.title, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A video on the web: a card that opens its address in the browser; the video itself is not carried by the course. */
@Composable
internal fun VideoView(video: Block.Video) {
    val uri = LocalUriHandler.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, colors.line, RoundedCornerShape(10.dp))
            .clickable(role = Role.Button) { runCatching { uri.openUri(video.url) } }
            .background(colors.card).padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { contentDescription = "Video" },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("▶", color = colors.accent, fontSize = 16.sp)
            InlineText(video.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
        }
        video.note?.let { InlineText(it, style = MaterialTheme.typography.bodyMedium.copy(color = colors.muted)) }
        Text("Opens in the browser · ${video.url.removePrefix("https://").substringBefore('/')}", style = MaterialTheme.typography.bodySmall, color = colors.muted)
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
    val sounds = buildList {
        head.forEach { _ -> add(null) }
        rows.forEach { row -> row.forEach { add(it.audio) } }
    }
    Box(Modifier.horizontalScroll(rememberScrollState()).border(1.dp, colors.line, RoundedCornerShape(8.dp))) {
        Layout(
            content = {
                all.forEachIndexed { i, (_, text, isHead) ->
                    val style = if (isHead) MaterialTheme.typography.labelLarge.copy(color = colors.muted) else MaterialTheme.typography.bodyMedium
                    val sound = sounds[i]
                    if (sound == null) InlineText(text, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = style)
                    else Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PlayButton(sound)
                        if (text.text.isNotEmpty()) InlineText(text, style = style)
                    }
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

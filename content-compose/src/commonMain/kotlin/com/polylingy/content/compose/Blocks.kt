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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.Dp
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
private val SmallAside = 120.dp
private val MediumAside = 180.dp

/** How wide a picture block may be: a word, not pixels; the column is still the limit on a narrow window. */
internal fun pictureMaxWidth(size: com.polylingy.content.Aside.Size?): Dp = when (size) {
    com.polylingy.content.Aside.Size.SMALL -> 160.dp
    null, com.polylingy.content.Aside.Size.MEDIUM -> 300.dp
    com.polylingy.content.Aside.Size.LARGE -> 560.dp
}

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
            val onlyVideo = label == null && block.blocks.singleOrNull() is Block.Video
            // A section that holds just a video has the title only as the name of links to it: the video card is its own heading.
            if (onlyVideo) Column(Modifier.highlightedSection(block.id).padding(top = 6.dp)) { Blocks(block.blocks, onLink = onLink, spacing = 10) }
            else if (label != null) Row(Modifier.highlightedSection(block.id), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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
        is Block.Text -> when (block.role) {
            Block.Text.Role.LEAD -> InlineText(
                block.text,
                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic, fontSize = 20.sp, lineHeight = 30.sp, color = colors.ink),
            )
            null -> InlineText(block.text)
        }
        is Block.Track -> TrackView(block)
        is Block.Video -> VideoView(block)
        is Block.Situation -> {
            val card = block.look != Block.Situation.Style.PLAIN
            // A thumbnail stands before the text, like an avatar; a bigger picture stands after it.
            val inside = @Composable {
                WithAside(block.aside, asideFirst = (block.aside as? Block.Picture)?.size == com.polylingy.content.Aside.Size.SMALL, onLink = onLink) { Blocks(block.blocks, onLink = onLink, spacing = 10) }
            }
            // A frame (a letter, an article) is the card itself; without one the situation is a soft card or plain.
            if (block.frame != null) Framed(block.frame, null) { Box(Modifier.fillMaxWidth()) { inside() } }
            else Box(
                if (card) Modifier.fillMaxWidth().background(colors.accentSoft, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 14.dp)
                else Modifier.fillMaxWidth(),
            ) { inside() }
        }
        is Block.Picture -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Picture(block.image, Modifier.widthIn(max = pictureMaxWidth(block.size)), rounded = true)
        }
        is Block.Table -> CellTable(block.head, block.rows, block.appearance ?: Block.Table.Appearance())
        is Block.Dialogue -> if (block.look == Block.Dialogue.Style.BUBBLES) DialogueBubbles(block) else Column(
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
            for ((i, example) in block.items.withIndex()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                if (block.numbered) Text("${i + 1}", Modifier.width(18.dp), color = colors.accent, fontWeight = FontWeight.Bold)
                example.audio?.let { PlayButton(it) } ?: if (!block.numbered) Text("•", color = colors.muted) else Unit
                val phrase: @Composable () -> Unit = {
                    val muted = colors.muted
                    val paint = rememberMarkPaint()
                    if (example.text.hasAnnotations && example.not == null) InlineText(example.text)
                    else Text(
                        buildAnnotatedString {
                            append(example.text.annotated(paint))
                            example.not?.let { not ->
                                withStyle(SpanStyle(color = muted)) {
                                    append("  (")
                                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("not ") }
                                    append(not.annotated(paint))
                                    append(")")
                                }
                            }
                        },
                    )
                }
                // Numbered examples read like a table: the phrase, and its translation opposite it in the same row.
                if (block.numbered) {
                    Box(Modifier.weight(1f)) { phrase() }
                    Box(Modifier.weight(1f)) { example.translation?.let { Translation(it) } }
                } else Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    phrase()
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
        is Block.Tip -> {
            val (tone, soft) = when (block.kind) {
                Block.Tip.Kind.WARNING -> colors.typo to colors.typoBg
                Block.Tip.Kind.FACT -> colors.info to colors.infoBg
                Block.Tip.Kind.CULTURE -> colors.culture to colors.cultureBg
                Block.Tip.Kind.TIP, Block.Tip.Kind.NOTE, Block.Tip.Kind.RULE -> colors.accent to colors.accentSoft
            }
            Box(Modifier.fillMaxWidth().background(soft, RoundedCornerShape(10.dp)).padding(14.dp).semantics { contentDescription = block.kind.name.lowercase() }) {
                WithAside(block.aside, asideFirst = false, onLink = onLink) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TipIcon(block.kind, tone)
                            // A title names this remark ("Obo"); without one the kind's caption does.
                            val title = block.title
                            if (title != null) InlineText(title, style = MaterialTheme.typography.labelLarge.copy(color = tone, fontWeight = FontWeight.Bold))
                            else Text(LocalContentStrings.current.remark(block.kind), color = tone, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        }
                        Blocks(block.blocks, onLink = onLink, spacing = 8)
                    }
                }
            }
        }
        is Block.Grid -> BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Columns need room: two from NarrowWidth up, three and four from a wider place; otherwise the blocks stand in one column.
            val needed = if (block.columns > 2) GridWideWidth else NarrowWidth
            if (maxWidth < needed) Blocks(block.blocks, onLink = onLink)
            else Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                for (row in block.blocks.chunked(block.columns)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    for (cell in row) Box(Modifier.weight(1f)) { BlockView(cell, onLink) }
                    repeat(block.columns - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
        is Block.SeeAlso -> FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(LocalContentStrings.current.seeAlso, color = colors.muted)
            val names = LocalLinkNames.current
            for (link in block.links) Text(
                names.name(link) ?: LocalContentStrings.current.theory,
                Modifier.clickable { onLink(link) },
                color = colors.accent,
                fontWeight = FontWeight.Medium,
            )
        }
        is Block.Unknown -> NewerVersion()
    }
}

/** Below this width a grid of three or four columns becomes one column. */
private val GridWideWidth = 760.dp

/**
 * A dialogue as speech bubbles: the first speaker's on the left, the second's on the right, any other speaker's on the left; a speaker's
 * portrait (if the block has one) stands next to the bubble, otherwise the name stands over the line.
 */
@Composable
private fun DialogueBubbles(block: Block.Dialogue) {
    val speakers = block.lines.mapNotNull { it.who?.plainText() }.distinct()
    Column(Modifier.fillMaxWidth().semantics { contentDescription = "Dialogue" }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (line in block.lines) {
            val who = line.who?.plainText()
            val right = who != null && speakers.indexOf(who) == 1
            val portrait = block.portraits.firstOrNull { it.who == who }
            val avatar = @Composable {
                if (portrait != null) Picture(portrait.image, Modifier.size(52.dp).clip(CircleShape), crop = true)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, if (right) Alignment.End else Alignment.Start),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (!right) avatar()
                Column(
                    Modifier.widthIn(max = 460.dp).background(if (right) colors.panel else colors.accentSoft, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (portrait == null) line.who?.let { InlineText(it, style = MaterialTheme.typography.labelMedium.copy(color = colors.muted, fontWeight = FontWeight.Bold)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        line.audio?.let { PlayButton(it) }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            InlineText(line.text, style = MaterialTheme.typography.bodyLarge)
                            line.translation?.let { Translation(it) }
                        }
                    }
                }
                if (right) avatar()
            }
        }
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
            VideoIcon(video.url)
            InlineText(video.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
        }
        video.note?.let { InlineText(it, style = MaterialTheme.typography.bodyMedium.copy(color = colors.muted)) }
        Text(LocalContentStrings.current.opensInBrowser(video.url.removePrefix("https://").substringBefore('/')), style = MaterialTheme.typography.bodySmall, color = colors.muted)
    }
}

/** [main] with an optional [aside] beside it — at most 35 % wide and [AsideMaxWidth] — or under it on a narrow width. */
@Composable
private fun WithAside(aside: Block?, asideFirst: Boolean, onLink: (Block.Link) -> Unit, main: @Composable () -> Unit) {
    if (aside == null) return main()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < NarrowWidth) {
            // Narrow: the picture goes over the content when it would stand before it, under it otherwise.
            val picture = aside as? Block.Picture
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (asideFirst) {
                    if (picture != null && picture.size == com.polylingy.content.Aside.Size.SMALL) Picture(picture.image, Modifier.size(SmallAside), crop = true, rounded = true) else BlockView(aside, onLink)
                }
                Box(Modifier.fillMaxWidth()) { main() }
                if (!asideFirst) BlockView(aside, onLink)
            }
        } else {
            val picture = aside as? Block.Picture
            val asideWidth = when (picture?.size) {
                com.polylingy.content.Aside.Size.SMALL -> SmallAside
                com.polylingy.content.Aside.Size.MEDIUM -> MediumAside
                else -> min(maxWidth * 0.35f, AsideMaxWidth)
            }
            val place = @Composable {
                if (picture != null && picture.size == com.polylingy.content.Aside.Size.SMALL) {
                    Picture(picture.image, Modifier.size(asideWidth), crop = true, rounded = true)
                } else Box(Modifier.width(asideWidth)) { BlockView(aside, onLink) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                if (asideFirst) place()
                Box(Modifier.weight(1f)) { main() }
                if (!asideFirst) place()
            }
        }
    }
}

/** A picture as wide as its place allows, as tall as its proportions say; its description when it can't be loaded. */
@Composable
fun Picture(image: Image, modifier: Modifier = Modifier, crop: Boolean = false, rounded: Boolean = false, contain: Boolean = false) {
    val bitmap = LocalImageLoader.current.load(image.src)
    val shape = RoundedCornerShape(if (rounded) 12.dp else 0.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (bitmap != null && contain) {
            // A card: the whole picture, centred, never cut; a square picture fills a square place exactly.
            Image(bitmap, contentDescription = image.alt, modifier = Modifier.fillMaxSize().clip(shape), contentScale = ContentScale.Fit)
        } else if (bitmap != null && crop) {
            // A thumbnail: the middle of the picture fills the whole place.
            Image(bitmap, contentDescription = image.alt, modifier = Modifier.fillMaxSize().clip(shape), contentScale = ContentScale.Crop)
        } else if (bitmap != null) {
            Image(
                bitmap,
                contentDescription = image.alt,
                modifier = Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height).clip(shape),
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
 * A theory table; a cell may span rows. How it is drawn is [appearance] (see [Block.Table.Appearance]). By default the table is as wide as its
 * place, in a soft card; its columns share the width in proportion to their text. A table too wide for its place is narrowed: the widest columns wrap their text.
 */
@Composable
private fun CellTable(head: List<Inline>, rows: List<List<Cell>>, appearance: Block.Table.Appearance) {
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
    val offset = if (head.isEmpty()) 0 else 1
    val fullWidth = appearance.widthOfTable == Block.Table.Appearance.WidthOfTable.FULL_WIDTH
    val card = appearance.background == Block.Table.Appearance.Background.CARD
    val highlightedHeader = appearance.headerRow == Block.Table.Appearance.HeaderRow.HIGHLIGHTED
    val highlightedColumns = appearance.highlightedColumns.toSet()
    val lastRow = rows.size - 1
    val rowLine = colors.line
    val headLine = colors.separator
    val columnLine = colors.separator
    val shape = RoundedCornerShape(12.dp)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val available = with(LocalDensity.current) { maxWidth.roundToPx() }
        // A card as wide as the page, or only as wide as the table when the table is only as wide as its text.
        val frame = if (card) {
            (if (fullWidth) Modifier.fillMaxWidth() else Modifier).clip(shape).background(colors.card, shape).border(1.dp, colors.line, shape)
        } else Modifier
        Box(frame.horizontalScroll(rememberScrollState())) {
            Layout(
                content = {
                    all.forEachIndexed { i, (place, text, isHead) ->
                        val bodyRow = place.row - offset
                        val style = when {
                            isHead -> MaterialTheme.typography.labelLarge.copy(color = colors.accent, fontWeight = FontWeight.Bold)
                            place.column in highlightedColumns -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            else -> MaterialTheme.typography.bodyMedium
                        }
                        val sound = sounds[i]
                        // Row lines: thin under body rows (not under the last one of a card, whose edge is its line); a stronger one under a plain header.
                        val endsTable = place.row + place.rowSpan - 1 - offset >= lastRow
                        val drawLine = if (isHead) !highlightedHeader else appearance.linesBetweenRows && !(card && endsTable)
                        val line = if (isHead) headLine else rowLine
                        val width = if (isHead) 1.5.dp else 1.dp
                        val shade = when {
                            isHead -> if (highlightedHeader) colors.head else Color.Transparent
                            place.column in highlightedColumns -> colors.column
                            appearance.stripedRows && bodyRow % 2 == 1 -> colors.stripe
                            else -> Color.Transparent
                        }
                        val padding = if (card) PaddingValues(horizontal = 16.dp, vertical = 11.dp) else PaddingValues(horizontal = 12.dp, vertical = 9.dp)
                        Box(
                            Modifier.background(shade).drawBehind {
                                if (drawLine) drawLine(line, Offset(0f, size.height - width.toPx() / 2), Offset(size.width, size.height - width.toPx() / 2), width.toPx())
                                // A highlighted column is parted from its neighbours by a line on its right edge.
                                if (place.column in highlightedColumns) drawLine(columnLine, Offset(size.width - 0.5.dp.toPx(), 0f), Offset(size.width - 0.5.dp.toPx(), size.height), 1.dp.toPx())
                            },
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (sound == null) InlineText(text, Modifier.padding(padding), style = style)
                            else Row(Modifier.padding(padding), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                PlayButton(sound)
                                if (text.text.isNotEmpty()) InlineText(text, style = style)
                            }
                        }
                    }
                },
            ) { measurables, _ ->
                val columns = (all.maxOfOrNull { it.first.column } ?: -1) + 1
                val widths = IntArray(columns)
                measurables.forEachIndexed { i, m -> val col = all[i].first.column; widths[col] = maxOf(widths[col], m.maxIntrinsicWidth(Constraints.Infinity)) }
                // A table wider than its place is narrowed, not scrolled: the widest columns give up width first and their text wraps, down to the
                // longest word of each column (only when even that does not fit, the table scrolls sideways as a last resort).
                if (widths.sum() > available) {
                    val least = IntArray(columns)
                    measurables.forEachIndexed { i, m -> val col = all[i].first.column; least[col] = maxOf(least[col], m.minIntrinsicWidth(Constraints.Infinity)) }
                    fun total(limit: Int) = widths.indices.sumOf { maxOf(least[it], minOf(widths[it], limit)) }
                    var low = 0
                    var high = widths.max()
                    while (low < high) {                // the largest column width limit whose table still fits
                        val mid = (low + high + 1) / 2
                        if (total(mid) <= available) low = mid else high = mid - 1
                    }
                    for (c in widths.indices) widths[c] = maxOf(least[c], minOf(widths[c], low))
                }
                // A table that is to fill its place shares the spare width among the columns in proportion to their text.
                val natural = widths.sum()
                if (fullWidth && natural in 1 until available) {
                    val spare = available - natural
                    val shares = widths.copyOf()
                    for (c in widths.indices) widths[c] += (spare.toLong() * shares[c] / natural).toInt()
                }
                // A measurable may be measured once, so the natural heights come from the intrinsics; the cells are then measured to their row.
                val naturalHeights = measurables.mapIndexed { i, m -> m.minIntrinsicHeight(widths[all[i].first.column]) }
                val rowCount = (all.maxOfOrNull { it.first.row } ?: -1) + 1
                val heights = IntArray(rowCount)
                all.forEachIndexed { i, (place) -> if (place.rowSpan == 1) heights[place.row] = maxOf(heights[place.row], naturalHeights[i]) }
                all.forEachIndexed { i, (place) ->
                    if (place.rowSpan > 1) {
                        val last = place.row + place.rowSpan - 1
                        val have = (place.row..last).sumOf { heights[it] }
                        if (naturalHeights[i] > have) heights[last] += naturalHeights[i] - have
                    }
                }
                val xs = widths.runningFold(0) { acc, w -> acc + w }
                val ys = heights.toList().runningFold(0) { acc, h -> acc + h }
                val placeables = measurables.mapIndexed { i, m ->
                    val place = all[i].first
                    m.measure(Constraints.fixed(widths[place.column], ys[place.row + place.rowSpan] - ys[place.row]))
                }
                layout(xs.last(), ys.last()) {
                    all.forEachIndexed { i, (place) -> placeables[i].place(xs[place.column], ys[place.row]) }
                }
            }
        }
    }
}

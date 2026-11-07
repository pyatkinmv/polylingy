package com.polylingy.content.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.polylingy.content.Aside
import com.polylingy.content.Look

/**
 * Layers 3 and 4 of an exercise (see [com.polylingy.content.Exercise]): the [look]'s frame, and inside it the [aside]
 * picture next to the [body]. Without a look there is no frame and the picture stands next to the body; without an
 * aside the body takes the whole width. Neither knows what the body is.
 */
@Composable
internal fun Framed(look: Look?, aside: Aside?, body: @Composable () -> Unit) {
    when (look) {
        null, is Look.Unknown -> WithAside(aside, body)
        is Look.Email -> Frame("Email") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MarkedText(look.subject, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).background(colors.accent, CircleShape), contentAlignment = Alignment.Center) {
                        Text(look.from.trim().take(1).uppercase(), color = colors.onAccent, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text(look.from, fontWeight = FontWeight.SemiBold)
                        look.to?.let { Text("to $it", color = colors.muted, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            FrameBody(aside, body)
        }
        is Look.Newspaper -> Frame("Newspaper") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                look.source?.let { Text(it.uppercase(), color = colors.muted, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp) }
                Direction(look.headline.runsRightToLeft()) {
                    Box(Modifier.fillMaxWidth().background(NewsPaper).dottedEdge(colors.muted).padding(horizontal = 18.dp, vertical = 14.dp)) {
                        // One weight for every script: a heavy cut turns scripts with joined letters and dots (Arabic) into a smear, so the
                        // headline gets its weight from size and the serif face, and its line height follows the text.
                        MarkedText(
                            look.headline,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 1.4.em,
                            ),
                        )
                    }
                }
            }
            FrameBody(aside, body)
        }
        is Look.Article -> Frame("Article") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                look.source?.let { Text(it.uppercase(), color = colors.muted, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp) }
                MarkedText(look.title, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold))
            }
            FrameBody(aside, body)
        }
    }
}

/** The paper of a clipping: a little greyer than the page. */
private val NewsPaper = androidx.compose.ui.graphics.Color(0xFFF1EDE2)

/** A dotted line around the clipping, as if it were cut out. */
private fun Modifier.dottedEdge(colour: androidx.compose.ui.graphics.Color) = drawBehind {
    drawRect(
        colour, style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 1.5.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())),
        ),
    )
}

/** A white card with a thin edge: the header goes first, then a line, then the body. */
@Composable
private fun Frame(label: String, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(colors.card, shape).border(1.dp, colors.line, shape).semantics { contentDescription = label }) {
        content()
    }
}

@Composable
private fun FrameBody(aside: Aside?, body: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
    Box(Modifier.padding(20.dp)) { WithAside(aside, body) }
}

/** Below this width a picture on the left or right goes on top, so the text isn't squeezed. */
private val NarrowWidth = 560.dp

/**
 * The [aside] picture on its side, as wide as its size's share of the width and as tall as its proportions say;
 * the [body] takes the rest. Left and right are two columns: the text doesn't flow under the picture.
 */
@Composable
private fun WithAside(aside: Aside?, body: @Composable () -> Unit) {
    if (aside == null) return body()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val side = if (maxWidth < NarrowWidth && aside.side in setOf(Aside.Edge.LEFT, Aside.Edge.RIGHT)) Aside.Edge.TOP else aside.side
        val picture = @Composable {
            Picture(aside.image, Modifier.width(maxWidth * share(side, aside.size)).semantics { contentDescription = "Aside" })
        }
        when (side) {
            Aside.Edge.LEFT, Aside.Edge.RIGHT -> Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                if (side == Aside.Edge.LEFT) picture()
                Box(Modifier.weight(1f)) { body() }
                if (side == Aside.Edge.RIGHT) picture()
            }
            Aside.Edge.TOP, Aside.Edge.BOTTOM -> Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (side == Aside.Edge.TOP) picture()
                Box(Modifier.fillMaxWidth()) { body() }
                if (side == Aside.Edge.BOTTOM) picture()
            }
        }
    }
}

/** How much of the width a picture takes: left or right ¼, ⅓, ½; top or bottom ⅓, ½, all of it. */
internal fun share(side: Aside.Edge, size: Aside.Size): Float = when (side) {
    Aside.Edge.LEFT, Aside.Edge.RIGHT -> when (size) {
        Aside.Size.SMALL -> 0.25f
        Aside.Size.MEDIUM -> 1f / 3
        Aside.Size.LARGE -> 0.5f
    }
    Aside.Edge.TOP, Aside.Edge.BOTTOM -> when (size) {
        Aside.Size.SMALL -> 1f / 3
        Aside.Size.MEDIUM -> 0.5f
        Aside.Size.LARGE -> 1f
    }
}

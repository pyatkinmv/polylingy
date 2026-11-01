package com.polylingy.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.polylingy.core.learning.TopicStatus
import com.polylingy.core.study.ProgressCounts
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.daysUntil

/** The app's "Notebook" look: warm paper, soft green. A course's own colour is only for its mark and buttons. */
object Look {
    val Paper = Color(0xFFFBF8F1)
    val Card = Color.White
    val Ink = Color(0xFF1F1D1A)
    val Muted = Color(0xFF6B6458)
    val Faint = Color(0xFF8A8273)
    val Line = Color(0xFFE6DFD0)
    val LineHover = Color(0xFFD6CCB8)
    val Panel = Color(0xFFF3EEE2)
    val Green = Color(0xFF2F7A4F)
    val Wrong = Color(0xFFB3261E)
    val WrongBg = Color(0xFFF7E1DD)
    val Warn = Color(0xFF7A5200)
    val WarnBg = Color(0xFFF6E9C8)

    /** Topic states: the same colours in every course, so green always means learned. */
    val LearnedForGood = Color(0xFF1F5A39)
    val Resting = Color(0xFF7DB592)
    val Today = Color(0xFFD98A2B)
    val Learning = Color(0xFFE9C46A)
    val New = Color(0xFFC9BFAC)
}

private val Scheme = lightColorScheme(
    primary = Look.Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4F0E7),
    background = Look.Paper,
    surface = Look.Card,
    surfaceContainerHigh = Look.Card,
    onSurface = Look.Ink,
    onSurfaceVariant = Look.Muted,
    outline = Look.Line,
    outlineVariant = Look.Line,
    error = Look.Wrong,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val base = Typography()
    val typography = base.copy(
        headlineMedium = base.headlineMedium.copy(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold),
    )
    MaterialTheme(colorScheme = Scheme, typography = typography) {
        Surface(color = Look.Paper, contentColor = Look.Ink, content = content)
    }
}

/** A page column of readable width, centred. */
@Composable
fun Page(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier.widthIn(max = 860.dp).fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
    }
}

/** The bar on top: where you are ("Courses / Grammar / Unit 3", earlier parts clickable), actions on the right. */
@Composable
fun TopBar(crumbs: List<Pair<String, (() -> Unit)?>>, actions: @Composable RowScope.() -> Unit = {}) {
    Column {
        Row(
            Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            crumbs.forEachIndexed { i, (text, onClick) ->
                if (i > 0) Text("/", color = Look.New)
                val last = i == crumbs.lastIndex
                Text(
                    text,
                    Modifier.widthIn(max = 260.dp).then(if (onClick != null) Modifier.clip(RoundedCornerShape(4.dp)).clickable(role = Role.Button, onClick = onClick) else Modifier),
                    color = if (last) Look.Ink else Look.Muted,
                    fontWeight = if (last) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            actions()
        }
        HorizontalDivider(color = Look.Line)
    }
}

/**
 * A card that answers the mouse: it lifts a little, gets a shadow and a darker edge; the arrow on the right moves.
 * [onClick] makes the whole card clickable; [hoverContent] shows a popup next to it while hovered.
 */
@Composable
fun Tile(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    arrow: Boolean = onClick != null,
    hoverContent: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift by animateDpAsState(if (hovered && onClick != null) (-2).dp else 0.dp)
    val shadow by animateDpAsState(if (hovered && onClick != null) 8.dp else 0.dp)
    val edge by animateColorAsState(if (hovered && onClick != null) Look.LineHover else Look.Line)
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .offset(y = lift)
                .shadow(shadow, shape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
                .background(Look.Card, shape)
                .border(1.dp, edge, shape)
                .clip(shape)
                .hoverable(interaction)
                .then(if (onClick != null) Modifier.clickable(interaction, indication = null, role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
            if (arrow) {
                val arrowShift by animateDpAsState(if (hovered) 3.dp else 0.dp)
                Text("›", Modifier.offset(x = arrowShift), color = if (hovered) MaterialTheme.colorScheme.primary else Look.New, fontSize = 22.sp)
            }
        }
        if (hovered && hoverContent != null) {
            Popup(alignment = Alignment.TopEnd, offset = IntOffset(-40, 120)) { hoverContent() }
        }
    }
}

/** The course's mark: its initials on its own soft colour. */
@Composable
fun CourseMark(name: String, accent: Color) {
    Box(Modifier.size(42.dp).background(accent.copy(alpha = 0.12f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        Text(initials(name), color = accent, fontWeight = FontWeight.Bold)
    }
}

fun initials(name: String): String =
    name.split(' ', '-').filter { it.isNotBlank() && it.first().isLetterOrDigit() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

/** The soft box on top of a course or unit: what waits today, what comes next, and the main button. */
@Composable
fun Hero(accent: Color, content: @Composable ColumnScope.() -> Unit, action: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(accent.copy(alpha = 0.10f), RoundedCornerShape(12.dp)).padding(horizontal = 22.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
        action()
    }
}

/** A button that is drawn but does nothing yet: dashed, grey. Its behaviour is decided later. */
@Composable
fun StubButton(text: String) {
    OutlinedButton(
        onClick = {},
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Look.New),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Look.Faint),
    ) { Text(text) }
}

@Composable
fun Kicker(text: String) = Text(text.uppercase(), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp), color = Look.Muted)

/** Variant O1: on the right of a course or unit card, what waits today in big type and how far along in small. */
@Composable
fun ProgressNumbers(counts: ProgressCounts, today: LocalDate) {
    Column(Modifier.widthIn(min = 100.dp), horizontalAlignment = Alignment.End) {
        when {
            counts.today > 0 -> {
                Text("${counts.today}", color = Look.Today, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("to do today", color = Look.Faint, fontSize = 12.sp)
            }
            counts.new > 0 -> {
                Text("${counts.new}", color = if (counts.studied == 0) Look.Faint else Look.Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(if (counts.studied == 0) "new" else "new to learn", color = Look.Faint, fontSize = 12.sp)
            }
            else -> {
                Text("✓ Done for today", color = Look.Green, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    counts.nextReview?.let { "next review ${shortDay(it, today)}" } ?: "learned for good",
                    color = Look.Faint,
                    fontSize = 12.sp,
                )
            }
        }
        Text("${counts.studied} / ${counts.total} studied", color = Look.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** The whole picture of a course or unit, shown while its card is hovered: a bar and the count of each state. */
@Composable
fun ProgressDetails(title: String, counts: ProgressCounts) {
    val rows = listOf(
        Triple(Look.LearnedForGood, counts.learnedForGood, "Learned for good"),
        Triple(Look.Resting, counts.resting, "Passed, coming back later"),
        Triple(Look.Today, counts.due, "To review today"),
        Triple(Look.Learning, counts.learning, "Learning"),
        Triple(Look.New, counts.new, "New"),
    ).filter { it.second > 0 }
    Column(
        Modifier.width(270.dp).shadow(12.dp, RoundedCornerShape(10.dp)).background(Look.Card, RoundedCornerShape(10.dp))
            .border(1.dp, Look.Line, RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFEEE8DB))) {
            for ((colour, n, _) in rows) if (colour != Look.New) Box(Modifier.weight(n.toFloat()).height(6.dp).background(colour))
            if (counts.new > 0) Spacer(Modifier.weight(counts.new.toFloat()))
        }
        for ((colour, n, label) in rows) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(9.dp).background(colour, RoundedCornerShape(2.dp)))
            Text(label, Modifier.weight(1f), fontSize = 13.sp)
            Text("$n", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** A topic's state on its card: a dot of the state's colour and a few words. */
@Composable
fun TopicState(status: TopicStatus, today: LocalDate) {
    val (colour, text) = when (status) {
        TopicStatus.New -> Look.New to "New"
        TopicStatus.InProgress -> Look.Learning to "Learning"
        TopicStatus.Due -> Look.Today to "To review today"
        is TopicStatus.Passed -> Look.Resting to "Back ${shortDay(status.due, today)}"
        TopicStatus.Learned -> Look.LearnedForGood to "Learned for good"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        val dot = Modifier.size(9.dp)
        Box(if (status == TopicStatus.New) dot.border(2.dp, colour, CircleShape) else dot.background(colour, CircleShape))
        Text(text, color = Look.Muted, fontSize = 13.sp)
    }
}

/** "tomorrow", "on Thursday" (within a week), "on 12 November". */
fun dayPhrase(date: LocalDate, today: LocalDate): String {
    val days = today.daysUntil(date)
    return when {
        days <= 0 -> "today"
        days == 1 -> "tomorrow"
        days < 7 -> "on ${weekday(date.dayOfWeek)}"
        else -> "on ${date.day} ${monthName(date.month)}"
    }
}

/** Shorter, for tight places: "tomorrow", "Thu", "12 Nov". */
fun shortDay(date: LocalDate, today: LocalDate): String {
    val days = today.daysUntil(date)
    return when {
        days <= 0 -> "today"
        days == 1 -> "tomorrow"
        days < 7 -> "on ${weekday(date.dayOfWeek).take(3)}"
        else -> "on ${date.day} ${monthName(date.month).take(3)}"
    }
}

private fun weekday(day: DayOfWeek) = day.name.lowercase().replaceFirstChar { it.uppercase() }

private fun monthName(month: Month) = month.name.lowercase().replaceFirstChar { it.uppercase() }

package com.polylingy.app.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import com.polylingy.content.compose.ContentColors
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.polylingy.core.learning.TopicStatus
import com.polylingy.core.study.ProgressCounts
import com.polylingy.core.study.TopicGroup
import com.polylingy.core.study.TopicProgress
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.daysUntil

/** The app's "Notebook" look: warm paper, soft green. A course's own colour is only for its mark and buttons. */
object Look {
    /**
     * The colours of a main button. Disabled, it is the same colour only paler (alpha, not a grey of its own) with the white text a
     * little softer: the button stays visible on a light page and on a dark course sheet alike, and reads as "not yet".
     */
    @androidx.compose.runtime.Composable
    fun buttonColors(container: Color = Green) = androidx.compose.material3.ButtonDefaults.buttonColors(
        containerColor = container, disabledContainerColor = container.copy(alpha = 0.45f), disabledContentColor = Color.White.copy(alpha = 0.85f),
    )

    val Paper = Color(0xFFFBF8F1)
    val Card = Color.White
    val Ink = Color(0xFF1F1D1A)
    val Muted = Color(0xFF6B6458)
    val Faint = Color(0xFF8A8273)
    val Line = Color(0xFFE6DFD0)
    val LineHover = Color(0xFFD6CCB8)
    val Green = Color(0xFF2F7A4F)
    val Wrong = Color(0xFFB3261E)
    val WrongBg = Color(0xFFF7E1DD)
    val Warn = Color(0xFF7A5200)
    val WarnBg = Color(0xFFF6E9C8)

    /** Quiet lines and marks: separators, the card arrow, stub buttons. */
    val Rule = Color(0xFFC9BFAC)

    /**
     * Topic groups, one palette from blue to green along a topic's path, the same in every course:
     * new → learning → to review → learned → learned for good.
     */
    val New = Color(0xFFB9D5EC)
    val Learning = Color(0xFF5B9BC8)
    val ToReview = Color(0xFFC3D35A)
    val Learned = Color(0xFF6DB383)
    val LearnedForGood = Color(0xFF1F6B3F)
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

/**
 * The app's theme. [text] is the platform's base text style — Inter with crisp rendering on desktop (`DesktopText`);
 * sizes and weights follow the design canvas: body 15, headings 28 at weight 650.
 */
@Composable
fun AppTheme(text: TextStyle = TextStyle.Default, content: @Composable () -> Unit) {
    val base = Typography()
    fun TextStyle.inter() = text.merge(this).copy(fontFamily = text.fontFamily ?: fontFamily)
    val typography = Typography(
        displayLarge = base.displayLarge.inter(), displayMedium = base.displayMedium.inter(), displaySmall = base.displaySmall.inter(),
        headlineLarge = base.headlineLarge.inter(),
        headlineMedium = base.headlineMedium.inter().copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight(650), letterSpacing = (-0.4).sp),
        headlineSmall = base.headlineSmall.inter(),
        titleLarge = base.titleLarge.inter().copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.inter().copy(fontSize = 16.5.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleSmall = base.titleSmall.inter(),
        bodyLarge = base.bodyLarge.inter().copy(fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
        bodyMedium = base.bodyMedium.inter().copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
        bodySmall = base.bodySmall.inter().copy(letterSpacing = 0.sp),
        labelLarge = base.labelLarge.inter().copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        labelMedium = base.labelMedium.inter(), labelSmall = base.labelSmall.inter(),
    )
    MaterialTheme(colorScheme = Scheme, typography = typography) {
        Surface(color = Look.Paper, contentColor = Look.Ink, content = content)
    }
}

/**
 * The colours of the course whose content is on screen, or null outside a course. The palette paints only the sheet of the course
 * (see [Page]); the bar, the margins and every other screen stay in the colours of the app.
 */
val LocalCourseSheet = staticCompositionLocalOf<ContentColors?> { null }

/**
 * A page column of readable width, centred. Inside a course ([LocalCourseSheet]) it is a sheet in the course's palette, lying on the
 * app's own background, so the content of a course is set apart from the app around it.
 */
@Composable
fun Page(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val sheet = LocalCourseSheet.current
    val shape = RoundedCornerShape(16.dp)
    Box(Modifier.fillMaxWidth().then(if (sheet != null) Modifier.padding(horizontal = 16.dp, vertical = 20.dp) else Modifier), contentAlignment = Alignment.TopCenter) {
        CompositionLocalProvider(LocalContentColor provides (sheet?.ink ?: LocalContentColor.current)) {
            Column(
                Modifier.widthIn(max = 860.dp).fillMaxWidth()
                    .then(if (sheet != null) Modifier.clip(shape).background(sheet.background, shape).border(1.dp, sheet.line, shape) else Modifier)
                    .then(modifier)
                    .padding(horizontal = 32.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                content = content,
            )
        }
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
            // The crumbs share the space the actions leave them and are cut with an ellipsis, so a button at the right
            // keeps its own width however long the titles are (a Row gives the leftover to its last child).
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                crumbs.forEachIndexed { i, (text, onClick) ->
                    if (i > 0) Text("/", color = Look.Rule)
                    val last = i == crumbs.lastIndex
                    Text(
                        text,
                        Modifier.weight(1f, fill = false).widthIn(max = 260.dp)
                            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(4.dp)).clickable(role = Role.Button, onClick = onClick) else Modifier),
                        color = if (last) Look.Ink else Look.Muted,
                        fontWeight = if (last) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            actions()
        }
        HorizontalDivider(color = Look.Line)
    }
}

/**
 * A card that answers the mouse: it lifts a little, gets a shadow and a darker edge; the arrow on the right moves.
 * [onClick] makes the whole card clickable. The hover is caught by the card's place, which never moves — were it caught
 * by the lifted card, a pointer at its edge would lose it, the card would drop, catch it again, and so on.
 */
@Composable
fun Tile(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    arrow: Boolean = onClick != null,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift by animateDpAsState(if (hovered && onClick != null) (-2).dp else 0.dp)
    // The design's box-shadow: 0 6px 18px rgba(60, 50, 30, 0.10), faded in on hover.
    val shadow by animateFloatAsState(if (hovered && onClick != null) 1f else 0f)
    val edge by animateColorAsState(if (hovered && onClick != null) Look.LineHover else Look.Line)
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .fillMaxWidth()
            .hoverable(interaction)
            .then(if (onClick != null) Modifier.clickable(interaction, indication = null, role = Role.Button, onClick = onClick) else Modifier),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .offset(y = lift)
                .dropShadow(shape, Shadow(radius = 18.dp, color = Color(0xFF3C321E), offset = DpOffset(0.dp, 6.dp), alpha = 0.10f * shadow))
                .background(Look.Card, shape)
                .border(1.dp, edge, shape)
                .clip(shape)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
            if (arrow) {
                val arrowShift by animateDpAsState(if (hovered) 3.dp else 0.dp)
                Text("›", Modifier.offset(x = arrowShift), color = if (hovered) MaterialTheme.colorScheme.primary else Look.Rule, fontSize = 22.sp)
            }
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

/** The design's quiet button: white, a thin edge, ink text; the edge darkens on hover (e.g. "＋ Import"). */
@Composable
fun QuietButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(8.dp)
    Text(
        text,
        Modifier
            .clip(shape)
            .background(if (hovered) Color(0xFFFFFDF8) else Look.Card, shape)
            .border(1.dp, if (hovered) Look.Rule else Look.Line, shape)
            .hoverable(interaction)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        color = Look.Ink,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
    )
}

/** A button that is drawn but does nothing yet: dashed, grey. Its behaviour is decided later. */
@Composable
fun StubButton(text: String) {
    OutlinedButton(
        onClick = {},
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Look.Rule),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Look.Faint),
    ) { Text(text) }
}

@Composable
fun Kicker(text: String) = Text(text.uppercase(), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp), color = Look.Muted)

/**
 * What there is to do now, the first line of a course or unit card (variant U4): "● 3 to review · ● 2 learning ·
 * ● 8 new for today" — "for today" only when the course has a daily limit — or "✓ Done for today".
 */
@Composable
fun WorkLine(counts: ProgressCounts, fontSize: androidx.compose.ui.unit.TextUnit = 14.sp) {
    if (counts.done) {
        val text = if (counts.total > 0 && counts.learnedForGood == counts.total) "✓ All learned for good" else "✓ Done for today"
        Text(text, color = Look.LearnedForGood, fontWeight = FontWeight.SemiBold, fontSize = fontSize)
        return
    }
    val parts = listOf(
        Triple(counts.toReview, "to review", Look.ToReview),
        Triple(counts.learning, "learning", Look.Learning),
        Triple(counts.newNow, if (counts.newToday != null) "new for today" else "new", Look.New),
    ).filter { it.first > 0 }
    Row(verticalAlignment = Alignment.CenterVertically) {
        parts.forEachIndexed { i, (n, word, colour) ->
            if (i > 0) Text(" · ", color = Look.Faint, fontSize = fontSize)
            Box(Modifier.padding(end = 6.dp).offset(y = 1.dp).size(8.dp).background(colour, CircleShape))
            Text("$n $word", fontSize = fontSize, maxLines = 1)
        }
    }
}

/** "56 of 400 learned": topics passed at least once, of all. */
fun learnedLine(counts: ProgressCounts) = "${counts.learned} of ${counts.total} learned"

/** The share learned as a ring, in the group colours, with the percentage inside. */
@Composable
fun ProgressRing(counts: ProgressCounts, size: Dp = 54.dp) {
    val total = counts.total.coerceAtLeast(1)
    val parts = listOf(
        counts.learnedForGood to Look.LearnedForGood,
        counts.resting to Look.Learned,
        counts.toReview to Look.ToReview,
        counts.learning to Look.Learning,
    )
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.width * 0.11f
            val inset = stroke / 2
            val arc = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(Look.New, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
            var start = -90f
            for ((n, colour) in parts) {
                if (n == 0) continue
                val sweep = 360f * n / total
                drawArc(colour, start, sweep, false, topLeft, arc, style = Stroke(stroke))
                start += sweep
            }
        }
        Text("${100 * counts.learned / total}%", color = Look.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The right side of a course or unit card: what to do now, how much is learned, and the ring. */
@Composable
fun CardProgress(counts: ProgressCounts) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            WorkLine(counts)
            Text(learnedLine(counts), color = Look.Faint, fontSize = 12.sp)
        }
        ProgressRing(counts)
    }
}

/** A topic's group on its card: a dot of the group's colour and a few words. */
@Composable
fun TopicState(topic: TopicProgress, today: LocalDate) {
    val status = topic.status
    val (colour, text) = when (topic.group) {
        TopicGroup.NEW -> Look.New to "New"
        TopicGroup.LEARNING -> Look.Learning to "Learning"
        TopicGroup.TO_REVIEW -> Look.ToReview to "To review"
        TopicGroup.LEARNED -> Look.Learned to (if (status is TopicStatus.Passed) "Back ${shortDay(status.due, today)}" else "Learned")
        TopicGroup.LEARNED_FOR_GOOD -> Look.LearnedForGood to "Learned for good"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(9.dp).background(colour, CircleShape))
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

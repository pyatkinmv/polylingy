package com.polylingy.app.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.polylingy.app.Navigator
import com.polylingy.app.ui.AppTab
import com.polylingy.app.ui.AppTabs
import com.polylingy.app.ui.Look
import com.polylingy.core.study.DayActivity
import com.polylingy.core.study.Statistics
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.math.min
import kotlin.uuid.Uuid

private val Forecast = Look.ToReview

/** The colours of the activity map by answers in a day: none, 1–14, 15–34, 35–59, 60 and more. */
private val Heat = listOf(Color(0xFFEEE8DB), Color(0xFFCFE5D6), Color(0xFF9CCBAE), Color(0xFF5DA77A), Color(0xFF2F7A4F))

internal fun heatLevel(answers: Int) = when {
    answers == 0 -> 0
    answers < 15 -> 1
    answers < 35 -> 2
    answers < 60 -> 3
    else -> 4
}

private fun DayOfWeek.short() = name.take(3).lowercase().replaceFirstChar { it.uppercase() }

private fun Month.short() = name.take(3).lowercase().replaceFirstChar { it.uppercase() }

private fun LocalDate.label() = "${dayOfWeek.short()} $dayOfMonth ${month.short()}"

/** The statistics tab: four small cards and the activity map of the year, whose squares tell their day when pointed at. */
@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel, go: Navigator) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    val stats = state.stats
    Column(Modifier.fillMaxSize()) {
        AppTabs(AppTab.STATISTICS, stats.streak, onCourses = go::courses, onStatistics = {})
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 1120.dp).fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Statistics", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                    if (state.courses.size > 1) ScopeMenu(state.scope, state.courses, viewModel::selectScope)
                }
                Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TodayCard(stats, Modifier.weight(1.45f).fillMaxSize())
                    StreakCard(stats, Modifier.weight(0.8f).fillMaxSize())
                    LearnedCard(stats, Modifier.weight(0.95f).fillMaxSize())
                    ForecastCard(stats, Modifier.weight(1.2f).fillMaxSize())
                }
                ActivityCard(stats)
            }
        }
    }
}

/** "All courses ▾": which course the numbers are about; shown only when there are several. */
@Composable
private fun ScopeMenu(scope: Uuid?, courses: List<Pair<Uuid, String>>, onPick: (Uuid?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box {
        Text(
            (courses.firstOrNull { it.first == scope }?.second ?: "All courses") + " ▾",
            Modifier.clip(shape).background(Look.Card, shape).border(1.dp, Look.Line, shape)
                .clickable(role = Role.Button) { open = true }.padding(horizontal = 16.dp, vertical = 9.dp),
            fontWeight = FontWeight.SemiBold,
        )
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text("All courses") }, onClick = { open = false; onPick(null) })
            for ((id, name) in courses) DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onPick(id) })
        }
    }
}

// ---- cards

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.clip(shape).background(Look.Card, shape).border(1.dp, Look.Line, shape).padding(horizontal = 20.dp, vertical = 18.dp)) { content() }
}

@Composable
private fun Caption(text: String) = Text(text.uppercase(), color = Look.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp)

@Composable
private fun Small(text: String) = Text(text, color = Look.Muted, fontSize = 13.sp)

/** The day's goal is the course's limit of new topics; without a limit there is no goal, only what was done. */
@Composable
private fun TodayCard(stats: Statistics, modifier: Modifier) = Card(modifier) {
    val work = stats.work
    val limit = work.newPerDay
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        if (limit != null && limit > 0) GoalRing(work.newBegun, limit)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Caption("Today")
            if (limit != null && limit > 0) Text("${work.newBegun} of $limit new topics", fontSize = 18.sp, fontWeight = FontWeight(650))
            else Text("${work.newBegun} new topics", fontSize = 18.sp, fontWeight = FontWeight(650))
            Small("${work.answers} answers · ${work.toReview} to review")
            if (limit == null) Small("No daily limit")
        }
    }
}

@Composable
private fun GoalRing(done: Int, goal: Int, size: Dp = 84.dp) {
    Box(Modifier.size(size).semantics { contentDescription = "$done of $goal new topics begun today" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.width * 0.105f
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            drawArc(Heat[0], 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
            drawArc(Look.Green, -90f, 360f * min(done, goal) / goal, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("$done", fontSize = 20.sp, fontWeight = FontWeight(650))
    }
}

@Composable
private fun StreakCard(stats: Statistics, modifier: Modifier) = Card(modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Caption("Streak")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${stats.streak}", color = Color(0xFF9A4B12), fontSize = 30.sp, fontWeight = FontWeight(650))
            Text(if (stats.streak == 1) "day" else "days", Modifier.padding(bottom = 4.dp), color = Look.Muted, fontSize = 14.sp)
        }
        Small("best ${stats.bestStreak} days")
    }
}

@Composable
private fun LearnedCard(stats: Statistics, modifier: Modifier) = Card(modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Caption("Learned")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${stats.learned}", fontSize = 30.sp, fontWeight = FontWeight(650))
            Text("of ${stats.topicsTotal} topics", Modifier.padding(bottom = 4.dp), color = Look.Muted, fontSize = 14.sp)
        }
        val week = stats.learned - stats.learnedWeekAgo
        Small(if (week > 0) "+$week this week" else "none new this week")
    }
}

/** Topics to review today and on each of the next six days, by the scheduler's own dates. */
@Composable
private fun ForecastCard(stats: Statistics, modifier: Modifier) = Card(modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Caption("Reviews coming up")
        val top = stats.forecast.maxOf { it.topics }.coerceAtLeast(1)
        Row(Modifier.fillMaxWidth().height(100.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            stats.forecast.forEachIndexed { i, day ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text("${day.topics}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(maxOf(3.dp, 40.dp * day.topics / top)).background(if (i == 0) Forecast else Forecast.copy(alpha = 0.55f), RoundedCornerShape(3.dp)))
                    Text(if (i == 0) "Today" else day.date.dayOfWeek.short(), color = Look.Faint, fontSize = 11.sp, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

// ---- the activity map

private val Cell = 16.dp
private val CellGap = 4.dp
private val LabelWidth = 34.dp

@Composable
private fun ActivityCard(stats: Statistics) = Card(Modifier.fillMaxWidth()) {
    Column {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val year = stats.days.filterKeys { it > stats.today.minus(365, DateTimeUnit.DAY) }.values.sumOf { it.answers }
            Text("$year answers in the last year", fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold)
            Text("point at a square to see its day", color = Look.Faint, fontSize = 12.5.sp)
        }
        Box(Modifier.height(14.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val step = Cell + CellGap
            val weeks = ((maxWidth - LabelWidth) / step).toInt().coerceIn(8, 53)
            // The week of today is the last column; weeks start on Monday.
            val first = stats.today.minus(stats.today.dayOfWeek.ordinal + 7 * (weeks - 1), DateTimeUnit.DAY)
            Column {
                Box(Modifier.padding(start = LabelWidth).height(20.dp)) {
                    var last = -1
                    for (w in 0 until weeks) {
                        val month = first.plus(7 * w + 6, DateTimeUnit.DAY).month
                        if (month.ordinal != last) {
                            if (last != -1 || w == 0) Text(month.short(), Modifier.offset(x = step * w), color = Look.Muted, fontSize = 12.sp)
                            last = month.ordinal
                        }
                    }
                }
                Row {
                    Column(Modifier.size(LabelWidth, Cell * 7 + CellGap * 6), verticalArrangement = Arrangement.spacedBy(CellGap)) {
                        for (row in 0..6) Box(Modifier.height(Cell), contentAlignment = Alignment.CenterStart) {
                            if (row % 2 == 0) Text(DayOfWeek.entries[row].short(), color = Look.Faint, fontSize = 11.sp)
                        }
                    }
                    HeatMap(stats, first, weeks)
                }
            }
        }
        Box(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("A quiet day is fine: topics come back by their schedule, not by day.", color = Look.Faint, fontSize = 12.5.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Less", color = Look.Muted, fontSize = 12.sp)
                for (c in Heat) Box(Modifier.size(13.dp).background(c, RoundedCornerShape(3.dp)))
                Text("More", color = Look.Muted, fontSize = 12.sp)
            }
        }
    }
}

private val TipSize = androidx.compose.ui.unit.DpSize(150.dp, 84.dp)

/** The squares, a day each. The tip that names the day floats over them, so pointing at one changes nothing else on the page. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HeatMap(stats: Statistics, first: LocalDate, weeks: Int) {
    val density = LocalDensity.current
    val step = Cell + CellGap
    val width = step * weeks - CellGap
    val height = step * 7 - CellGap
    var hover by remember { mutableStateOf<LocalDate?>(null) }

    fun dayAt(at: Offset): LocalDate? {
        val s = with(density) { step.toPx() }
        val week = (at.x / s).toInt()
        val row = (at.y / s).toInt()
        if (at.x < 0 || at.y < 0 || week >= weeks || row > 6) return null
        return first.plus(7 * week + row, DateTimeUnit.DAY).takeIf { it <= stats.today }
    }

    Box(Modifier.size(width, height)) {
        Canvas(
            Modifier.fillMaxSize().testTag("activity-map")
                .semantics { contentDescription = "Activity of the last $weeks weeks" }
                .onPointerEvent(PointerEventType.Move) { hover = dayAt(it.changes.first().position) }
                .onPointerEvent(PointerEventType.Enter) { hover = dayAt(it.changes.first().position) }
                .onPointerEvent(PointerEventType.Exit) { hover = null },
        ) {
            val cell = Cell.toPx()
            val gap = CellGap.toPx()
            for (w in 0 until weeks) for (row in 0..6) {
                val day = first.plus(7 * w + row, DateTimeUnit.DAY)
                if (day > stats.today) continue
                val topLeft = Offset(w * (cell + gap), row * (cell + gap))
                drawRoundRect(Heat[heatLevel(stats.days[day]?.answers ?: 0)], topLeft, Size(cell, cell), CornerRadius(4.dp.toPx()))
                if (day == hover) drawRoundRect(Look.Ink, topLeft, Size(cell, cell), CornerRadius(4.dp.toPx()), style = Stroke(2.dp.toPx()))
            }
        }
        hover?.let { day ->
            val daysFromFirst = (day.toEpochDays() - first.toEpochDays()).toInt()
            val col = daysFromFirst / 7
            val row = daysFromFirst % 7
            val x = (step * col - (TipSize.width - Cell) / 2).coerceIn(0.dp, maxOf(0.dp, width - TipSize.width))
            val y = if (row >= 3) step * row - TipSize.height - 6.dp else step * row + step + 2.dp
            Tip(day, stats.days[day], Modifier.offset(x = x, y = y).testTag("activity-tip"))
        }
    }
}

@Composable
private fun Tip(day: LocalDate, activity: DayActivity?, modifier: Modifier) {
    val ink = Color(0xFFF4F0E6)
    Column(modifier.size(TipSize).background(Look.Ink, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(day.label(), color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        if (activity == null) Text("No answers", color = ink, fontSize = 12.5.sp)
        else {
            Text("${activity.answers} answers", color = ink, fontSize = 12.5.sp)
            Text("${activity.passed} passed", color = ink, fontSize = 12.5.sp)
            if (activity.newTopics > 0) Text("${activity.newTopics} new topics", color = ink, fontSize = 12.5.sp)
        }
    }
}

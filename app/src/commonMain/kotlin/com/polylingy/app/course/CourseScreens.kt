package com.polylingy.app.course

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.polylingy.app.Navigator
import com.polylingy.app.ui.Hero
import com.polylingy.app.ui.Kicker
import com.polylingy.app.ui.Look
import com.polylingy.app.ui.Page
import com.polylingy.app.ui.ProgressDetails
import com.polylingy.app.ui.ProgressNumbers
import com.polylingy.app.ui.StubButton
import com.polylingy.app.ui.Tile
import com.polylingy.app.ui.TopBar
import com.polylingy.app.ui.TopicState
import com.polylingy.app.ui.dayPhrase
import com.polylingy.content.compose.ContentColors
import com.polylingy.content.compose.InlineText
import com.polylingy.core.content.Course
import com.polylingy.core.learning.TopicStatus
import com.polylingy.core.ports.Clock
import com.polylingy.core.study.NextExercise
import com.polylingy.core.study.ProgressCounts
import com.polylingy.core.study.TopicProgress
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

/** The course's own colour, or the app's green. */
fun Course.accent(): Color = ContentColors.forAccent(theme?.accent).accent

/** Follows a [Start] from a course, unit or topic screen. */
fun Navigator.follow(courseId: Uuid, start: Start) {
    when (start) {
        is Start.Study -> study(courseId, start.unitId, start.topicId)
        is Start.TheoryFirst -> theoryThenStudy(courseId, start.theoryUnit, start.then.unitId, start.then.topicId)
        is Start.TopicPage -> topic(courseId, start.topicId)
        Start.Nothing -> {}
    }
}

@Composable
fun CourseScreen(viewModel: CourseViewModel, clock: Clock, go: Navigator) {
    val progress by viewModel.progress.collectAsState()
    val next by viewModel.next.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    val p = progress ?: return
    val course = p.course
    val today = clock.now().localDate
    val accent = course.accent()

    Column(Modifier.fillMaxSize()) {
        TopBar(listOf("Courses" to go::courses, course.name to null)) {
            if (course.units.isEmpty()) StubButton("＋ Add topic")
        }
        Page(Modifier.verticalScroll(rememberScrollState())) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(course.name, style = MaterialTheme.typography.headlineMedium)
                course.description?.let { InlineText(it, style = MaterialTheme.typography.bodyLarge.copy(color = Look.Muted)) }
            }
            if (course.studyOnly) NewerVersionNote()
            TodayHero(p.counts, next, today, accent) { go.follow(course.id, viewModel.continueStudy()) }
            if (course.units.isNotEmpty()) {
                Text("Units", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    p.units.forEachIndexed { i, unit ->
                        val counts = unit.counts
                        Tile(
                            onClick = { go.unit(course.id, unit.unit.id) },
                            hoverContent = { ProgressDetails("Unit ${i + 1}", counts) },
                        ) {
                            Text("${i + 1}", Modifier.width(28.dp), color = Look.Faint, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                            Column(Modifier.weight(1f)) {
                                Text(unit.unit.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                unit.unit.subtitle?.let { InlineText(it, style = MaterialTheme.typography.bodyMedium.copy(color = Look.Muted)) }
                            }
                            ProgressNumbers(counts, today)
                        }
                    }
                }
            } else {
                Text("Topics", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
                TopicList(p.topics, today) { go.follow(course.id, viewModel.open(it)) }
            }
        }
    }
}

@Composable
fun UnitScreen(viewModel: CourseViewModel, unitId: Uuid, clock: Clock, go: Navigator) {
    val progress by viewModel.progress.collectAsState()
    val next by viewModel.next.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    val p = progress ?: return
    val course = p.course
    val index = p.units.indexOfFirst { it.unit.id == unitId }
    val unit = p.units.getOrNull(index) ?: return
    val today = clock.now().localDate

    Column(Modifier.fillMaxSize()) {
        TopBar(listOf("Courses" to go::courses, course.name to { go.backToCourse(course.id) }, "Unit ${index + 1}" to null)) {
            if (unit.unit.theory != null) TheoryButton(course.accent()) { go.theory(course.id, unitId) }
        }
        Page(Modifier.verticalScroll(rememberScrollState())) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Kicker("Unit ${index + 1}")
                Text(unit.unit.title, style = MaterialTheme.typography.headlineMedium)
                unit.unit.subtitle?.let { InlineText(it, style = MaterialTheme.typography.bodyLarge.copy(color = Look.Muted)) }
            }
            TodayHero(unit.counts, next, today, course.accent()) { go.follow(course.id, viewModel.continueStudy()) }
            Text("Topics", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
            TopicList(unit.topics, today) { go.follow(course.id, viewModel.open(it)) }
        }
    }
}

/** A topic that rests or is learned for good: when it comes back, and extra practice (drawn, not working yet). */
@Composable
fun TopicScreen(viewModel: CourseViewModel, topicId: Uuid, clock: Clock, go: Navigator) {
    val progress by viewModel.progress.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    val p = progress ?: return
    val course = p.course
    val topic = p.allTopics.firstOrNull { it.topic.id == topicId } ?: return
    val unitIndex = p.units.indexOfFirst { u -> u.topics.any { it.topic.id == topicId } }
    val unit = p.units.getOrNull(unitIndex)?.unit
    val today = clock.now().localDate

    Column(Modifier.fillMaxSize()) {
        val crumbs = buildList {
            add("Courses" to go::courses)
            add(course.name to { go.backToCourse(course.id) })
            if (unit != null) add("Unit ${unitIndex + 1}" to { go.backToUnit(course.id, unit.id) })
            add(topic.topic.title to null)
        }
        TopBar(crumbs) { if (unit?.theory != null) TheoryButton(course.accent()) { go.theory(course.id, unit.id) } }
        Page {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Kicker("Topic")
                Text(topic.topic.title, style = MaterialTheme.typography.headlineMedium)
            }
            Column(
                Modifier.fillMaxWidth().background(Look.Card, RoundedCornerShape(12.dp)).border(1.dp, Look.Line, RoundedCornerShape(12.dp)).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (val status = topic.status) {
                    is TopicStatus.Passed -> {
                        Text("Done for now", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text("It comes back for review ${dayPhrase(status.due, today)}.", color = Look.Muted)
                    }
                    TopicStatus.Learned -> {
                        Text("Learned for good", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Look.LearnedForGood)
                        Text("It won’t come back by itself.", color = Look.Muted)
                    }
                    else -> {
                        Text("Waiting for you", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Button(onClick = { go.study(course.id, unit?.id, topicId) }) { Text("Study it") }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StubButton("Extra practice")
                Text("coming later", color = Look.Faint, fontSize = 13.sp)
            }
        }
    }
}

/** What waits today, what is next, and "Continue" — or when the next review is if nothing waits. */
@Composable
private fun TodayHero(counts: ProgressCounts, next: NextExercise?, today: LocalDate, accent: Color, onContinue: () -> Unit) {
    Hero(
        accent,
        content = {
            when {
                counts.today > 0 -> Row {
                    Text("${counts.today}", color = Look.Today, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(" to do today", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                next != null -> Text("New topics to learn", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                else -> Text(
                    counts.nextReview?.let { "Nothing due — next review ${dayPhrase(it, today)}" } ?: "Everything here is learned for good",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            val nextLine = next?.let { "Next: ${it.topic.title} · " }.orEmpty()
            Text("$nextLine${counts.studied} of ${counts.total} studied", color = Look.Muted, fontSize = 13.sp)
        },
    ) {
        if (next != null) Button(onClick = onContinue, colors = ButtonDefaults.buttonColors(containerColor = accent), shape = RoundedCornerShape(8.dp)) {
            Text("Continue →")
        } else StubButton("Extra practice")
    }
}

@Composable
private fun TopicList(topics: List<TopicProgress>, today: LocalDate, onOpen: (Uuid) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (topic in topics) Tile(onClick = { onOpen(topic.topic.id) }) {
            Text(topic.topic.title, Modifier.weight(1f), fontSize = 16.sp)
            TopicState(topic.status, today)
        }
    }
}

@Composable
fun TheoryButton(accent: Color, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, accent),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
    ) { Text("Theory") }
}

@Composable
fun NewerVersionNote() {
    Row(
        Modifier.fillMaxWidth().background(Look.WarnBg, RoundedCornerShape(10.dp)).padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Made with a newer version of Polylingy.", fontWeight = FontWeight.SemiBold)
        Text("You can study it; parts it doesn’t know are skipped.", color = Look.Muted)
    }
}

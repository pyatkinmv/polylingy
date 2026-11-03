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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.polylingy.app.Navigator
import com.polylingy.app.ui.DangerDialog
import com.polylingy.app.ui.Hero
import com.polylingy.app.ui.MenuItem
import com.polylingy.app.ui.MessageDialog
import com.polylingy.app.ui.MoreMenu
import com.polylingy.app.ui.QuietButton
import com.polylingy.app.ui.Kicker
import com.polylingy.app.ui.Look
import com.polylingy.app.ui.Page
import com.polylingy.app.ui.CardProgress
import com.polylingy.app.ui.ProgressRing
import com.polylingy.app.ui.StubButton
import com.polylingy.app.ui.Tile
import com.polylingy.app.ui.TopBar
import com.polylingy.app.ui.TopicState
import com.polylingy.app.ui.WorkLine
import com.polylingy.app.ui.dayPhrase
import com.polylingy.app.ui.learnedLine
import com.polylingy.content.compose.ContentColors
import com.polylingy.content.compose.InlineText
import com.polylingy.core.content.Course
import com.polylingy.core.content.CoursePart
import com.polylingy.core.content.PartProblem
import com.polylingy.core.content.Topic
import com.polylingy.core.learning.TopicStatus
import com.polylingy.core.ports.Clock
import com.polylingy.core.study.NextExercise
import com.polylingy.core.study.ProgressCounts
import com.polylingy.core.study.TopicProgress
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

/**
 * The course's own colour (`theme.accent`), or the app's green. It marks the course — its initials on the course list —
 * and nothing else: buttons, links and the theory stay in the app's green, so a red or brown course doesn't read as a warning.
 */
fun Course.markColour(): Color = ContentColors.forAccent(theme?.accent).accent

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
    val accent = Look.Green
    var dialog by remember { mutableStateOf<PartDialog?>(null) }
    val editable = !course.studyOnly

    Column(Modifier.fillMaxSize()) {
        TopBar(listOf("Courses" to go::courses, course.name to null)) {
            if (editable) QuietButton("Study settings") { go.settings(course.id) }
        }
        Page(Modifier.verticalScroll(rememberScrollState())) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(course.name, style = MaterialTheme.typography.headlineMedium)
                course.description?.let { InlineText(it, style = MaterialTheme.typography.bodyLarge.copy(color = Look.Muted)) }
            }
            if (course.studyOnly) NewerVersionNote()
            TodayHero(p.counts, next, today, accent, p.newPerDay) { go.follow(course.id, viewModel.continueStudy()) }
            if (course.units.isNotEmpty()) {
                ListTitle("Units", if (editable) "＋ Add unit" else null) { go.edit(course.id, CoursePart.Unit(null)) }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    p.units.forEachIndexed { i, unit ->
                        Tile(onClick = { go.unit(course.id, unit.unit.id) }) {
                            Text("${i + 1}", Modifier.width(28.dp), color = Look.Faint, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                            Column(Modifier.weight(1f)) {
                                Text(unit.unit.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                unit.unit.subtitle?.let { InlineText(it, style = MaterialTheme.typography.bodyMedium.copy(color = Look.Muted)) }
                            }
                            CardProgress(unit.counts)
                            if (editable) MoreMenu(
                                listOf(
                                    MenuItem("Edit unit…") { go.edit(course.id, CoursePart.Unit(unit.unit.id)) },
                                    MenuItem("Add topic…") { go.edit(course.id, CoursePart.Topic(unit.unit.id, null)) },
                                    MenuItem("Delete unit…", enabled = p.units.size > 1, danger = true) {
                                        dialog = PartDialog.Delete(CoursePart.Unit(unit.unit.id), "unit", unit.unit.title, unitDeleteText(unit.unit.topics.size))
                                    },
                                ),
                                note = if (p.units.size == 1) "A course keeps at least one unit" else null,
                            )
                        }
                    }
                }
            } else {
                ListTitle("Topics", if (editable) "＋ Add topic" else null) { go.edit(course.id, CoursePart.Topic(null, null)) }
                TopicList(
                    p.topics, today,
                    menu = if (editable) topicMenu(p.topics.size, onEdit = { go.edit(course.id, CoursePart.Topic(null, it.id)) }) {
                        dialog = PartDialog.Delete(CoursePart.Topic(null, it.id), "topic", it.title, topicDeleteText(it.exercises.size))
                    } else null,
                ) { go.follow(course.id, viewModel.open(it)) }
            }
        }
    }
    PartDialogs(dialog, viewModel) { dialog = it }
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
    var dialog by remember { mutableStateOf<PartDialog?>(null) }
    val editable = !course.studyOnly

    Column(Modifier.fillMaxSize()) {
        TopBar(listOf("Courses" to go::courses, course.name to { go.backToCourse(course.id) }, "Unit ${index + 1}" to null)) {
            if (unit.unit.theory != null) TheoryButton(Look.Green) { go.theory(course.id, unitId) }
        }
        Page(Modifier.verticalScroll(rememberScrollState())) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Kicker("Unit ${index + 1}")
                Text(unit.unit.title, style = MaterialTheme.typography.headlineMedium)
                unit.unit.subtitle?.let { InlineText(it, style = MaterialTheme.typography.bodyLarge.copy(color = Look.Muted)) }
            }
            // The daily limit belongs to the course: the unit's new topics are offered only as far as it allows today.
            TodayHero(ProgressCounts.of(unit.topics, p.newToday), next, today, Look.Green, p.newPerDay) {
                go.follow(course.id, viewModel.continueStudy())
            }
            ListTitle("Topics", if (editable) "＋ Add topic" else null) { go.edit(course.id, CoursePart.Topic(unitId, null)) }
            TopicList(
                unit.topics, today,
                menu = if (editable) topicMenu(unit.topics.size, onEdit = { go.edit(course.id, CoursePart.Topic(unitId, it.id)) }) {
                    dialog = PartDialog.Delete(CoursePart.Topic(unitId, it.id), "topic", it.title, topicDeleteText(it.exercises.size))
                } else null,
            ) { go.follow(course.id, viewModel.open(it)) }
        }
    }
    PartDialogs(dialog, viewModel) { dialog = it }
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
        TopBar(crumbs) { if (unit?.theory != null) TheoryButton(Look.Green) { go.theory(course.id, unit.id) } }
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

/**
 * The block on top of a course or unit: what there is to do now, what comes next, how much is learned, the ring and
 * "Continue" — or, when nothing is left, when the next review is.
 */
@Composable
private fun TodayHero(counts: ProgressCounts, next: NextExercise?, today: LocalDate, accent: Color, newPerDay: Int?, onContinue: () -> Unit) {
    Hero(
        accent,
        content = {
            WorkLine(counts, fontSize = 16.sp)
            val details = buildList {
                next?.let { add("Next: ${it.topic.title}") }
                if (next == null) counts.nextReview?.let { add("Next review ${dayPhrase(it, today)}") }
                add(learnedLine(counts))
                newPerDay?.let { add("$it new a day") }
            }
            Text(details.joinToString(" · "), color = Look.Muted, fontSize = 13.sp)
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ProgressRing(counts, 58.dp)
            if (next != null) Button(onClick = onContinue, colors = ButtonDefaults.buttonColors(containerColor = accent), shape = RoundedCornerShape(8.dp)) {
                Text("Continue →")
            } else StubButton("Extra practice")
        }
    }
}

@Composable
private fun TopicList(topics: List<TopicProgress>, today: LocalDate, menu: TopicMenu? = null, onOpen: (Uuid) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (topic in topics) Tile(onClick = { onOpen(topic.topic.id) }) {
            Text(topic.topic.title, Modifier.weight(1f), fontSize = 16.sp)
            TopicState(topic, today)
            menu?.let { m ->
                MoreMenu(
                    listOf(
                        MenuItem("Edit topic…") { m.onEdit(topic.topic) },
                        MenuItem("Delete topic…", enabled = m.canDelete, danger = true) { m.onDelete(topic.topic) },
                    ),
                    note = if (m.canDelete) null else "The last topic can’t be deleted",
                )
            }
        }
    }
}

/** The "⋯" of each topic card, on a course that can be changed. */
private class TopicMenu(val canDelete: Boolean, val onEdit: (Topic) -> Unit, val onDelete: (Topic) -> Unit)

private fun topicMenu(count: Int, onEdit: (Topic) -> Unit, onDelete: (Topic) -> Unit) = TopicMenu(count > 1, onEdit, onDelete)

/** A list's title ("Units", "Topics") with its add button at the right edge, where a new one will appear. */
@Composable
private fun ListTitle(title: String, add: String?, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (add != null) QuietButton(add, onAdd)
    }
}

private sealed interface PartDialog {
    data class Delete(val part: CoursePart, val noun: String, val title: String, val text: String) : PartDialog
    data class Failed(val noun: String, val problems: List<PartProblem>) : PartDialog
}

private fun unitDeleteText(topics: Int) =
    "The unit, its theory and ${if (topics == 1) "its topic" else "its $topics topics"} leave the course with their exercises. " +
        "Your answers to them stay in the history but no longer count."

private fun topicDeleteText(exercises: Int) =
    "The topic and ${if (exercises == 1) "its exercise" else "its $exercises exercises"} leave the course. " +
        "Your answers to it stay in the history but no longer count."

/** Asks before deleting a unit or topic; says why when the course needs it. */
@Composable
private fun PartDialogs(dialog: PartDialog?, viewModel: CourseViewModel, set: (PartDialog?) -> Unit) {
    when (dialog) {
        null -> {}
        is PartDialog.Delete -> DangerDialog(
            title = "Delete “${dialog.title}”?",
            text = dialog.text,
            confirm = "Delete ${dialog.noun}",
            onConfirm = {
                val problems = viewModel.delete(dialog.part)
                set(if (problems.isEmpty()) null else PartDialog.Failed(dialog.noun, problems))
            },
            onDismiss = { set(null) },
        )
        is PartDialog.Failed -> MessageDialog("Can’t delete the ${dialog.noun}", dialog.problems.joinToString("\n") { "• ${it.path}: ${it.message}" }) { set(null) }
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

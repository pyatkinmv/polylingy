package com.polylingy.app.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.polylingy.app.Navigator
import com.polylingy.app.study.CourseLinks
import com.polylingy.app.study.checkLine
import com.polylingy.app.ui.DangerDialog
import com.polylingy.app.ui.Kicker
import com.polylingy.app.ui.Look
import com.polylingy.app.ui.MessageDialog
import com.polylingy.app.ui.Page
import com.polylingy.app.ui.QuietButton
import com.polylingy.app.ui.TopBar
import com.polylingy.content.Exercise
import com.polylingy.content.isComplete
import com.polylingy.content.ItemResult
import com.polylingy.content.compose.ContentColors
import com.polylingy.content.compose.ContentTheme
import com.polylingy.content.compose.ContentView
import com.polylingy.content.compose.ExerciseView
import com.polylingy.content.compose.ImageLoader
import com.polylingy.core.content.CoursePart
import com.polylingy.core.content.PartCheck
import com.polylingy.core.content.PartProblem
import com.polylingy.core.content.Topic

/** At most this many problems are listed under the editor. */
private const val SHOWN_PROBLEMS = 12

private sealed interface EditorDialog {
    data class Dropped(val topics: List<Topic>) : EditorDialog
    data class Failed(val problems: List<PartProblem>) : EditorDialog
    data object Discard : EditorDialog
}

/**
 * The editor of a unit or topic as a page: its JSON on top, what is wrong with it, and below a preview of the part
 * to try — the theory, each topic and each exercise. Saving goes back to where the editor was opened from.
 */
@Composable
fun EditorScreen(viewModel: EditorViewModel, images: ImageLoader, go: Navigator) {
    val course = viewModel.before ?: return
    val text by viewModel.text.collectAsState()
    val check by viewModel.check.collectAsState()
    val edited by viewModel.edited.collectAsState()
    val preview by viewModel.preview.collectAsState()
    var dialog by remember { mutableStateOf<EditorDialog?>(null) }
    val part = viewModel.part
    val isUnit = part is CoursePart.Unit
    val noun = if (isUnit) "unit" else "topic"

    val unitId = when (part) {
        is CoursePart.Unit -> part.unitId
        is CoursePart.Topic -> part.unitId
    }
    val unitIndex = course.units.indexOfFirst { it.id == unitId }
    val unit = course.units.getOrNull(unitIndex)

    fun save(confirmed: Boolean) {
        dialog = when (val outcome = viewModel.save(confirmed)) {
            SaveOutcome.Saved -> { go.back(); null }
            is SaveOutcome.ConfirmDropped -> EditorDialog.Dropped(outcome.topics)
            is SaveOutcome.Failed -> EditorDialog.Failed(outcome.problems)
        }
    }

    Column(Modifier.fillMaxSize()) {
        val crumbs = buildList {
            add("Courses" to go::courses)
            add(course.name to { go.backToCourse(course.id) })
            if (unit != null) add("Unit ${unitIndex + 1}" to { go.backToUnit(course.id, unit.id) })
            add((if (viewModel.isNew) "New $noun" else "Edit $noun") to null)
        }
        TopBar(crumbs) {
            QuietButton("Cancel") { if (viewModel.changed) dialog = EditorDialog.Discard else go.back() }
            Button(
                onClick = { save(confirmed = false) },
                enabled = check?.ok == true && viewModel.changed,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Look.Green),
            ) { Text(if (viewModel.isNew) "Add $noun" else "Save") }
        }
        Page(Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (viewModel.isNew) "New $noun" else edited?.title ?: "",
                        fontSize = 24.sp,
                        fontWeight = FontWeight(650),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (viewModel.isNew && !isUnit) Text(
                        if (unit != null) "in Unit ${unitIndex + 1} · ${unit.title}" else "in ${course.name}",
                        color = Look.Muted,
                        fontSize = 14.sp,
                    )
                }
                Status(check, edited)
            }
            JsonEditor(
                text = text,
                onChange = viewModel::change,
                placeholder = viewModel.template,
                errorLines = check?.problems.orEmpty().mapNotNullTo(mutableSetOf()) { it.line },
            )
            if (text.isEmpty()) TemplateHint(noun, onUse = viewModel::useTemplate)
            else Text(
                "${text.lines().size} lines · ids are the app’s own: keep them and progress stays; new topics and exercises get theirs when saved",
                color = Look.Muted,
                fontSize = 13.sp,
            )
            check?.problems?.takeIf { it.isNotEmpty() }?.let { Problems(it) }
            PreviewCard(viewModel, edited, preview, check, course, images, isUnit)
        }
    }

    when (val d = dialog) {
        null -> {}
        is EditorDialog.Dropped -> {
            val names = d.topics.joinToString(", ") { "“${it.title}”" }
            DangerDialog(
                title = if (d.topics.size == 1) "Delete 1 topic?" else "Delete ${d.topics.size} topics?",
                text = "The unit no longer has $names. Saving deletes ${if (d.topics.size == 1) "it" else "them"} with the exercises. " +
                    "Your answers stay in the history but no longer count.",
                confirm = "Save and delete",
                onConfirm = { save(confirmed = true) },
                onDismiss = { dialog = null },
            )
        }
        is EditorDialog.Failed -> MessageDialog("Couldn't save", d.problems.joinToString("\n") { "• $it" }) { dialog = null }
        EditorDialog.Discard -> DangerDialog(
            title = "Discard changes?",
            text = "What you changed in this $noun is not saved and will be lost.",
            confirm = "Discard",
            onConfirm = { dialog = null; go.back() },
            onDismiss = { dialog = null },
        )
    }
}

/** "● Valid · 2 exercises", or "● 2 problems — fix them to save". Nothing while the editor is empty. */
@Composable
private fun Status(check: PartCheck?, edited: EditedPart?) {
    check ?: return
    val ok = check.ok
    val text = if (ok && edited != null) {
        val exercises = edited.topics.sumOf { it.exercises.size }
        listOfNotNull(
            "Valid",
            "theory".takeIf { edited.theory != null },
            if (edited.topics.size > 1 || edited.theory != null) plural(edited.topics.size, "topic") else null,
            plural(exercises, "exercise"),
        ).joinToString(" · ")
    } else if (ok) "Valid"
    else "${plural(check.problems.size, "problem")} — fix ${if (check.problems.size == 1) "it" else "them"} to save"
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).background(if (ok) Look.Green else Look.Wrong, CircleShape))
        Text(text, color = if (ok) Look.Green else Look.Wrong, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun TemplateHint(noun: String, onUse: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("The grey text is the smallest $noun that works; typing replaces it — or ", color = Look.Muted, fontSize = 13.sp)
        Text(
            "use it as a start",
            Modifier.clip(RoundedCornerShape(4.dp)).clickable(role = Role.Button, onClick = onUse),
            color = Look.Green,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
        Text(".", color = Look.Muted, fontSize = 13.sp)
    }
}

@Composable
private fun Problems(problems: List<PartProblem>) {
    Column(
        Modifier.fillMaxWidth().background(Look.WrongBg, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (problem in problems.take(SHOWN_PROBLEMS)) {
            val where = when {
                !problem.inPart -> "Elsewhere in the course · ${problem.path}: "
                problem.line != null && problem.path.text.isEmpty() -> "Line ${problem.line} · "
                problem.line != null -> "Line ${problem.line} · ${problem.path}: "
                else -> if (problem.path.text.isEmpty()) "" else "${problem.path}: "
            }
            Text(where + problem.message, fontSize = 14.sp)
        }
        if (problems.size > SHOWN_PROBLEMS) Text("…and ${problems.size - SHOWN_PROBLEMS} more", color = Look.Muted, fontSize = 13.sp)
    }
}

@Composable
private fun PreviewCard(
    viewModel: EditorViewModel,
    edited: EditedPart?,
    preview: Preview,
    check: PartCheck?,
    course: com.polylingy.core.content.Course,
    images: ImageLoader,
    isUnit: Boolean,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().background(Look.Card, shape).border(1.dp, Look.Line, shape).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Kicker("Preview")
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    edited == null -> "appears as you type"
                    check?.ok == false -> "of the last valid text"
                    else -> "try it — nothing is saved to your progress"
                },
                color = Look.Muted,
                fontSize = 13.sp,
            )
        }
        if (edited == null) {
            Text("Nothing to show yet.", color = Look.Muted)
            return@Column
        }
        if (isUnit) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (edited.theory != null) Chip("Theory", on = preview.topic == null, onClick = viewModel::showTheory)
            edited.topics.forEachIndexed { i, topic -> Chip(topic.title, on = preview.topic == i) { viewModel.showTopic(i) } }
        }
        val topic = preview.topic?.let { edited.topics.getOrNull(it) }
        if (topic != null && topic.exercises.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            topic.exercises.forEachIndexed { i, exercise ->
                Chip("Exercise ${i + 1} · ${exercise.typeName()}", on = preview.exercise == i) { viewModel.showExercise(i) }
            }
        }
        val shownCourse = check?.course ?: course
        val links = remember(shownCourse) { CourseLinks(shownCourse) }
        ContentTheme(null, images, links.names) {
            val theory = edited.theory
            val exercise = preview.shown
            val response = preview.response
            if (preview.topic == null && theory != null) ContentView(theory)
            else if (exercise != null && response != null) {
                preview.checks?.let { checks ->
                    val (fg, bg) = when {
                        checks.any { it.result == ItemResult.WRONG } -> Look.Wrong to Look.WrongBg
                        checks.any { it.result == ItemResult.TYPO } -> Look.Warn to Look.WarnBg
                        else -> Look.Green to Color(0xFFE4F0E7)
                    }
                    Text(
                        checkLine(checks),
                        Modifier.fillMaxWidth().background(bg, RoundedCornerShape(10.dp)).padding(horizontal = 18.dp, vertical = 10.dp),
                        color = fg,
                        fontWeight = FontWeight.Bold,
                    )
                }
                ExerciseView(
                    exercise = exercise,
                    response = response,
                    checks = preview.checks,
                    onChange = viewModel::answer,
                    onSubmit = viewModel::checkAnswer,
                    seed = preview.exercise,
                )
                val colors = ButtonDefaults.buttonColors(containerColor = Look.Green)
                if (preview.checks == null) Button(onClick = viewModel::checkAnswer, enabled = exercise.isComplete(response), colors = colors, shape = RoundedCornerShape(8.dp)) { Text("Check") }
                else Button(onClick = viewModel::tryAgain, colors = colors, shape = RoundedCornerShape(8.dp)) { Text("Try again") }
            }
        }
    }
}

/** A tab of the preview: a theory, topic or exercise to show. */
@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text,
        Modifier
            .widthIn(max = 280.dp)
            .clip(shape)
            .background(if (on) Look.Ink else Look.Card, shape)
            .border(1.dp, if (on) Look.Ink else Look.Line, shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        color = if (on) Color.White else Look.Muted,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun Exercise.typeName() = when (this) {
    is Exercise.Gaps -> "gaps"
    is Exercise.Choice -> "choice"
    is Exercise.Pictures -> "pictures"
    is Exercise.Table -> "table"
    is Exercise.Match -> "match"
    is Exercise.Order -> "order"
    is Exercise.Sort -> "sort"
    is Exercise.Expand -> "expand"
    is Exercise.Fix -> "fix"
    is Exercise.Unknown -> "unknown"
}

private fun plural(n: Int, noun: String) = "$n $noun${if (n == 1) "" else "s"}"

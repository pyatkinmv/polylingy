package com.polylingy.app.courses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.polylingy.app.APP_VERSION_LABEL
import com.polylingy.app.CourseFiles
import com.polylingy.app.course.markColour
import com.polylingy.app.ui.AppTab
import com.polylingy.app.ui.AppTabs
import com.polylingy.app.ui.ConfirmDialog
import com.polylingy.app.ui.CourseDetailsDialog
import com.polylingy.app.ui.CourseMark
import com.polylingy.app.ui.Look
import com.polylingy.app.ui.MenuItem
import com.polylingy.app.ui.MoreMenu
import com.polylingy.app.ui.MessageDialog
import com.polylingy.app.ui.Page
import com.polylingy.app.ui.QuietButton
import com.polylingy.app.ui.CardProgress
import com.polylingy.app.ui.Tile
import com.polylingy.client.BrokenCourse
import com.polylingy.content.Problem
import com.polylingy.content.compose.annotated
import com.polylingy.core.content.Course
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

private sealed interface CourseDialog {
    data class Edit(val course: Course, val error: String? = null) : CourseDialog
    data class Remove(val id: Uuid, val name: String) : CourseDialog
    data class Update(val course: Course) : CourseDialog
    data class Message(val title: String, val text: String) : CourseDialog
}

/** At most this many problems are listed in a dialog; the full list is in the log. */
private const val SHOWN_PROBLEMS = 15

@Composable
fun CourseListScreen(viewModel: CourseListViewModel, files: CourseFiles, today: LocalDate, onOpen: (Uuid) -> Unit, onSettings: (Uuid) -> Unit, onStatistics: () -> Unit = {}) {
    val streak by viewModel.streakDays.collectAsState()
    val courses by viewModel.courses.collectAsState()
    val broken by viewModel.broken.collectAsState()
    var dialog by remember { mutableStateOf<CourseDialog?>(null) }
    val close = { dialog = null }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AppTabs(AppTab.COURSES, streak, onCourses = {}, onStatistics = onStatistics) {
                QuietButton("＋ Import") {
                    val file = files.open() ?: return@QuietButton
                    dialog = when (val outcome = viewModel.import(file)) {
                        is ImportOutcome.Failed -> CourseDialog.Message("Couldn't import ${outcome.fileName}", problemList(outcome.problems))
                        is ImportOutcome.Imported -> importedMessage(outcome)
                        is ImportOutcome.Refused -> CourseDialog.Message("Couldn't import ${outcome.fileName}", outcome.reason)
                    }
                }
            }
            Page(Modifier.verticalScroll(rememberScrollState())) {
                Text("Courses", style = MaterialTheme.typography.headlineMedium)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (row in courses) CourseTile(
                        row,
                        today,
                        onOpen = { onOpen(row.course.id) },
                        onEdit = { dialog = CourseDialog.Edit(row.course) },
                        onUpdate = { dialog = CourseDialog.Update(row.course) },
                        onSettings = { onSettings(row.course.id) },
                        onExport = {
                            viewModel.export(row.course.id)
                                .onSuccess { files.save(it.fileName, it.bytes) }
                                .onFailure { dialog = CourseDialog.Message("Couldn't export \"${row.course.name}\"", it.message ?: "Unknown error") }
                        },
                        onRemove = { dialog = CourseDialog.Remove(row.course.id, row.course.name) },
                    )
                    for (course in broken) BrokenTile(
                        course,
                        onShow = { dialog = CourseDialog.Message("Can't open \"${course.label}\"", problemList(course.problems)) },
                        onRemove = { dialog = CourseDialog.Remove(course.id, course.label) },
                    )
                }
            }
        }
        Text(APP_VERSION_LABEL, Modifier.align(Alignment.BottomStart).padding(horizontal = 32.dp, vertical = 12.dp), color = Look.Faint, fontSize = 11.sp)
    }

    when (val d = dialog) {
        null -> {}
        is CourseDialog.Edit -> CourseDetailsDialog(
            name = d.course.name,
            description = d.course.description?.text.orEmpty(),
            error = d.error,
            onConfirm = { name, description ->
                viewModel.editDetails(d.course.id, name, description)
                    .onSuccess { close() }
                    .onFailure { dialog = d.copy(error = it.message) }
            },
            onDismiss = close,
        )
        is CourseDialog.Remove -> ConfirmDialog(
            "Remove course",
            "Are you sure you want to remove \"${d.name}\"? This cannot be undone.",
            onConfirm = { viewModel.delete(d.id); close() },
            onDismiss = close,
        )
        is CourseDialog.Update -> ConfirmDialog(
            "Update course",
            "Pick a newer file of \"${d.course.name}\" (now version ${d.course.version}). Your progress stays on every topic and exercise " +
                "that keeps its id in the file; changes you made to the course inside the app are replaced by the file.",
            onConfirm = {
                val file = files.open()
                dialog = if (file == null) null else when (val outcome = viewModel.update(d.course.id, file)) {
                    is ImportOutcome.Failed -> CourseDialog.Message("Couldn't update \"${d.course.name}\" from ${outcome.fileName}", problemList(outcome.problems))
                    is ImportOutcome.Refused -> CourseDialog.Message("Couldn't update \"${d.course.name}\" from ${outcome.fileName}", outcome.reason)
                    is ImportOutcome.Imported -> CourseDialog.Message(
                        "Updated \"${outcome.course.name}\"",
                        "Now at version ${outcome.course.version}." + if (outcome.warnings.isEmpty()) "" else "\n\n${problemList(outcome.warnings)}",
                    )
                }
            },
            onDismiss = close,
        )
        is CourseDialog.Message -> MessageDialog(d.title, d.text, onDismiss = close)
    }
}

@Composable
private fun CourseTile(row: CourseRow, today: LocalDate, onOpen: () -> Unit, onEdit: () -> Unit, onUpdate: () -> Unit, onSettings: () -> Unit, onExport: () -> Unit, onRemove: () -> Unit) {
    val course = row.course
    Tile(onClick = onOpen) {
        CourseMark(course.name, course.markColour())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(course.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            course.description?.let {
                Text(it.annotated(), style = MaterialTheme.typography.bodyMedium, color = Look.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (course.studyOnly) Text("Made in a newer Polylingy · read-only", color = Look.Faint, fontSize = 12.5.sp)
        }
        CardProgress(row.counts)
        MoreMenu(
            listOf(
                MenuItem("Edit name and description…", enabled = !course.studyOnly, action = onEdit),
                MenuItem("Update from file…", action = onUpdate),
                MenuItem("Study settings…", enabled = !course.studyOnly, action = onSettings),
                MenuItem("Export", enabled = !course.studyOnly, action = onExport),
                MenuItem("Remove", danger = true, action = onRemove),
            ),
            note = if (course.studyOnly) "Update Polylingy to edit or export this course" else null,
        )
    }
}

private val BrokenCourse.label: String get() = name ?: "Unknown course"

/** A stored course this version can't read: what is wrong, and a way to remove it. */
@Composable
private fun BrokenTile(course: BrokenCourse, onShow: () -> Unit, onRemove: () -> Unit) {
    Tile(onClick = onShow) {
        Box(Modifier.size(42.dp).background(Look.WrongBg, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Text("!", color = Look.Wrong, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Text(course.label, style = MaterialTheme.typography.titleMedium)
            Text("Can't open this course", color = Look.Wrong, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            course.problems.firstOrNull()?.let { Text(it.toString(), color = Look.Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        MoreMenu(listOf(MenuItem("Show problems", action = onShow), MenuItem("Remove", danger = true, action = onRemove)))
    }
}

private fun importedMessage(outcome: ImportOutcome.Imported): CourseDialog.Message? {
    val course = outcome.course
    return when {
        course.studyOnly -> CourseDialog.Message(
            "\"${course.name}\" was made with a newer Polylingy",
            "It is imported and you can study it. Until you update Polylingy it can't be edited or exported, " +
                "and parts this version doesn't know are skipped:\n\n${problemList(outcome.warnings)}",
        )
        outcome.warnings.isNotEmpty() -> CourseDialog.Message("Imported \"${course.name}\" with warnings", problemList(outcome.warnings))
        else -> null
    }
}

private fun problemList(problems: List<Problem>): String {
    val shown = problems.take(SHOWN_PROBLEMS).joinToString("\n") { "• $it" }
    val more = problems.size - SHOWN_PROBLEMS
    return if (more > 0) "$shown\n…and $more more" else shown
}

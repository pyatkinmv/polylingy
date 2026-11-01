package com.polylingy.app.courses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import com.polylingy.app.APP_VERSION
import com.polylingy.app.CourseFiles
import com.polylingy.app.ui.ConfirmDialog
import com.polylingy.app.ui.CourseNameDialog
import com.polylingy.app.ui.MessageDialog
import com.polylingy.content.Problem
import com.polylingy.core.content.Course

private sealed interface CourseDialog {
    data class Rename(val course: Course) : CourseDialog
    data class Remove(val course: Course) : CourseDialog
    data class Message(val title: String, val text: String) : CourseDialog
}

/** At most this many problems are listed in a dialog; the full list is in the log. */
private const val SHOWN_PROBLEMS = 15

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseListScreen(viewModel: CourseListViewModel, files: CourseFiles) {
    val courses by viewModel.courses.collectAsState()
    var dialog by remember { mutableStateOf<CourseDialog?>(null) }
    val close = { dialog = null }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Polylingy") },
                actions = {
                    TextButton(onClick = {
                        val file = files.open() ?: return@TextButton
                        dialog = when (val outcome = viewModel.import(file)) {
                            is ImportOutcome.Failed -> CourseDialog.Message("Couldn't import ${outcome.fileName}", problemList(outcome.problems))
                            is ImportOutcome.Imported -> importedMessage(outcome)
                        }
                    }) { Text("Import") }
                    Spacer(Modifier.width(8.dp))
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        "Studying is being rebuilt. For now, courses can be imported, exported, renamed and removed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                }
                items(courses, key = { it.id.toString() }) { course ->
                    CourseCard(
                        course = course,
                        onRename = { dialog = CourseDialog.Rename(course) },
                        onExport = {
                            viewModel.export(course.id)
                                .onSuccess { files.save(it.fileName, it.bytes) }
                                .onFailure { dialog = CourseDialog.Message("Couldn't export \"${course.name}\"", it.message ?: "Unknown error") }
                        },
                        onRemove = { dialog = CourseDialog.Remove(course) },
                    )
                }
            }
            Text(
                "v$APP_VERSION",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            )
        }
    }

    when (val d = dialog) {
        null -> {}
        is CourseDialog.Rename -> CourseNameDialog(
            "Rename course",
            initial = d.course.name,
            onConfirm = { viewModel.rename(d.course.id, it); close() },
            onDismiss = close,
        )
        is CourseDialog.Remove -> ConfirmDialog(
            "Remove course",
            "Are you sure you want to remove \"${d.course.name}\"? This cannot be undone.",
            onConfirm = { viewModel.delete(d.course.id); close() },
            onDismiss = close,
        )
        is CourseDialog.Message -> MessageDialog(d.title, d.text, onDismiss = close)
    }
}

private fun importedMessage(outcome: ImportOutcome.Imported): CourseDialog.Message? {
    val course = outcome.course
    return when {
        course.studyOnly -> CourseDialog.Message(
            "Update Polylingy",
            "\"${course.name}\" was made with a newer version of Polylingy. Parts this version doesn't know are left out, " +
                "and the course can't be exported until the app is updated.\n\n${problemList(outcome.warnings)}",
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

@Composable
private fun CourseCard(course: Course, onRename: () -> Unit, onExport: () -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(course.name, style = MaterialTheme.typography.titleMedium)
                course.description?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it.plainText(), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(4.dp))
                Text(summary(course), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            OverflowMenu(
                "Rename course" to onRename,
                "Export course" to onExport,
                "Remove course" to onRemove,
            )
        }
    }
}

private fun summary(course: Course): String {
    val size = if (course.units.isNotEmpty()) count(course.units.size, "unit") else count(course.topics.size, "topic")
    return if (course.studyOnly) "$size · made with a newer version" else size
}

private fun count(n: Int, noun: String) = "$n $noun${if (n == 1) "" else "s"}"

/** A "⋮" button opening a menu of labelled actions. */
@Composable
fun OverflowMenu(vararg items: Pair<String, () -> Unit>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((label, action) in items) {
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; action() })
            }
        }
    }
}

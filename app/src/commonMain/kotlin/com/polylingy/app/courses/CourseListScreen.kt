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
import androidx.compose.material3.Button
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
import com.polylingy.app.CourseFiles
import com.polylingy.app.ui.AddTopicDialog
import com.polylingy.app.ui.ConfirmDialog
import com.polylingy.app.ui.CourseNameDialog
import com.polylingy.app.ui.MessageDialog
import com.polylingy.core.study.CourseStats
import kotlin.uuid.Uuid

private sealed interface CourseDialog {
    data object New : CourseDialog
    data class Rename(val stats: CourseStats) : CourseDialog
    data class Remove(val stats: CourseStats) : CourseDialog
    data class AddTopic(val courseId: Uuid) : CourseDialog
    data class Message(val title: String, val text: String) : CourseDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseListScreen(viewModel: CourseListViewModel, files: CourseFiles, onStart: (Uuid) -> Unit) {
    val courses by viewModel.courseStats.collectAsState()
    var dialog by remember { mutableStateOf<CourseDialog?>(null) }
    val close = { dialog = null }

    // Coming back from a study session changes the counters.
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Polylingy") },
                actions = {
                    TextButton(onClick = {
                        val json = files.open() ?: return@TextButton
                        viewModel.import(json)?.let { dialog = CourseDialog.Message("Couldn't import course", it) }
                    }) { Text("Import") }
                    TextButton(onClick = { dialog = CourseDialog.New }) { Text("+ New course") }
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
                items(courses, key = { it.course.id.toString() }) { stats ->
                    CourseCard(
                        stats = stats,
                        onStart = { onStart(stats.course.id) },
                        onAddTopic = { dialog = CourseDialog.AddTopic(stats.course.id) },
                        onRename = { dialog = CourseDialog.Rename(stats) },
                        onExport = {
                            viewModel.export(stats.course.id)?.let { files.save(it.fileName, it.json) }
                        },
                        onRemove = { dialog = CourseDialog.Remove(stats) },
                    )
                }
            }
        }
    }

    when (val d = dialog) {
        null -> {}
        CourseDialog.New -> CourseNameDialog("New course", onConfirm = { viewModel.create(it); close() }, onDismiss = close)
        is CourseDialog.Rename -> CourseNameDialog(
            "Rename course",
            initial = d.stats.course.name,
            onConfirm = { viewModel.rename(d.stats.course.id, it); close() },
            onDismiss = close,
        )
        is CourseDialog.Remove -> ConfirmDialog(
            "Remove course",
            "Are you sure you want to remove \"${d.stats.course.name}\"? This cannot be undone.",
            onConfirm = { viewModel.delete(d.stats.course.id); close() },
            onDismiss = close,
        )
        is CourseDialog.AddTopic -> AddTopicDialog(
            onSubmit = { json -> viewModel.addTopic(d.courseId, json).also { if (it == null) close() } },
            onDismiss = close,
        )
        is CourseDialog.Message -> MessageDialog(d.title, d.text, onDismiss = close)
    }
}

@Composable
private fun CourseCard(
    stats: CourseStats,
    onStart: () -> Unit,
    onAddTopic: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stats.course.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${stats.newCount} new · ${stats.toReview} to review · ${stats.doneToday} done today",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
            Spacer(Modifier.width(16.dp))
            Button(onClick = onStart, enabled = stats.eligible > 0) { Text("Start") }
            OverflowMenu(
                "Add topic" to onAddTopic,
                "Rename course" to onRename,
                "Export course" to onExport,
                "Remove course" to onRemove,
            )
        }
    }
}

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

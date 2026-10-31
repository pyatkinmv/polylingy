package com.polylingy.app.study

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.polylingy.app.courses.OverflowMenu
import com.polylingy.app.ui.ConfirmDialog
import com.polylingy.app.ui.htmlToAnnotatedString
import com.polylingy.app.ui.onEnter
import com.polylingy.core.content.Explanation
import com.polylingy.core.content.ExplanationFormat
import com.polylingy.core.journal.AutoCheck
import com.polylingy.core.rules.AnswerChecker

private val ContentWidth = 720.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(viewModel: StudyViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var confirmRemove by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.courseName) },
                navigationIcon = { IconButton(onClick = onBack) { Text("←", style = MaterialTheme.typography.titleLarge) } },
                actions = { if (state.topic != null) OverflowMenu("Remove topic" to { confirmRemove = true }) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is StudyState.Answering -> AnsweringView(s, viewModel::onInput, viewModel::submit)
                is StudyState.Reviewing -> ReviewingView(s, viewModel::rate)
                StudyState.Done -> DoneView(onBack)
            }
        }
    }

    val topic = state.topic
    if (confirmRemove && topic != null) {
        ConfirmDialog(
            "Remove topic",
            "Are you sure you want to remove \"${topic.subject}\"? This cannot be undone.",
            onConfirm = { viewModel.removeCurrentTopic(); confirmRemove = false },
            onDismiss = { confirmRemove = false },
        )
    }
}

/** Scrollable column centred and capped at [ContentWidth], like the Flutter layout. */
@Composable
private fun CenteredColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = ContentWidth).fillMaxWidth().padding(32.dp)) { content() }
    }
}

@Composable
private fun Subject(text: String) =
    Text(text, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

@Composable
private fun AnsweringView(state: StudyState.Answering, onInput: (String) -> Unit, onSubmit: () -> Unit) {
    val focus = remember(state.exercise) { FocusRequester() }
    LaunchedEffect(state.exercise) { focus.requestFocus() }
    val multiBlank = AnswerChecker.blankKeys(state.exercise).size > 1

    CenteredColumn {
        Subject(state.topic.subject)
        Spacer(Modifier.height(24.dp))
        Text(maskBlanks(state.exercise.task), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = state.input,
            onValueChange = onInput,
            label = { Text(if (multiBlank) "Your answers (comma-separated)" else "Your answer") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focus).onEnter(onSubmit),
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = onSubmit) { Text("Submit") }
        }
    }
}

private fun modeColor(result: AutoCheck) = when (result) {
    AutoCheck.CORRECT -> Color(0xFFA5D6A7)
    AutoCheck.PARTIAL -> Color(0xFFFFE082)
    AutoCheck.INCORRECT -> Color(0xFFEF9A9A)
}

private fun annotated(parts: List<TaskPart>): AnnotatedString = buildAnnotatedString {
    for (part in parts) when (part) {
        is TaskPart.Plain -> append(part.text)
        is TaskPart.Filled -> withStyle(
            SpanStyle(color = if (part.correct) Color(0xFF2E7D32) else Color(0xFFC62828), fontWeight = FontWeight.Bold),
        ) { append(part.text) }
    }
}

@Composable
private fun ReviewingView(state: StudyState.Reviewing, onRate: (Boolean) -> Unit) {
    val focus = remember(state) { FocusRequester() }
    LaunchedEffect(state) { focus.requestFocus() }

    Column(
        Modifier.fillMaxSize().focusRequester(focus).focusable().onKeyEvent {
            if (it.type != KeyEventType.KeyDown) return@onKeyEvent false
            when (it.key) {
                Key.One, Key.NumPad1 -> { onRate(false); true }
                Key.Two, Key.NumPad2 -> { onRate(true); true }
                else -> false
            }
        },
    ) {
        CenteredColumn(Modifier.weight(1f)) {
            Subject(state.topic.subject)
            Spacer(Modifier.height(24.dp))
            Text(
                annotated(annotateTask(state.exercise.task, state.check)),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                state.phrase,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
                    .background(modeColor(state.check.result), RoundedCornerShape(8.dp))
                    .padding(vertical = 10.dp, horizontal = 16.dp),
            )
            state.exercise.explanation?.let {
                Spacer(Modifier.height(16.dp))
                ExplanationBox("Specific explanation", Color(0xFFDEF3C8), AnnotatedString(it))
            }
            state.topic.explanation?.let {
                Spacer(Modifier.height(16.dp))
                ExplanationBox("Explanation", Color(0xFFE3F2FD), it.render())
            }
        }
        HorizontalDivider(thickness = 1.5.dp, color = Color(0xFFE0E0E0))
        Box(
            Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 12.dp, bottom = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onRate(false) }) { Text("Incorrect") }
                Button(onClick = { onRate(true) }) { Text("Correct") }
            }
        }
    }
}

private fun Explanation.render(): AnnotatedString = when (format) {
    ExplanationFormat.TEXT -> AnnotatedString(text)
    ExplanationFormat.HTML -> htmlToAnnotatedString(text)
}

@Composable
private fun ExplanationBox(label: String, color: Color, text: AnnotatedString) {
    Column(Modifier.fillMaxWidth().background(color, RoundedCornerShape(8.dp)).padding(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF616161))
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DoneView(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("All done for today!", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Come back tomorrow to continue.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onBack) { Text("Back to home") }
    }
}

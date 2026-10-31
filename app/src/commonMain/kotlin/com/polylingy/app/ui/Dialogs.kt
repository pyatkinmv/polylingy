package com.polylingy.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.polylingy.core.content.CourseService

/** Asks for a course name: non-blank, at most [CourseService.MAX_NAME_LENGTH] characters. */
@Composable
fun CourseNameDialog(title: String, initial: String = "", onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val valid = value.text.isNotBlank()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.limitTo(CourseService.MAX_NAME_LENGTH) },
                label = { Text("Course name") },
                singleLine = true,
                supportingText = { Text("${value.text.length}/${CourseService.MAX_NAME_LENGTH}") },
                modifier = Modifier.focusRequester(focus).onEnter { if (valid) onConfirm(value.text) },
            )
        },
        confirmButton = { Button(onClick = { onConfirm(value.text) }, enabled = valid) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { Button(onClick = onConfirm) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun MessageDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { Button(onClick = onDismiss) { Text("OK") } },
    )
}

/**
 * Lets the user paste a topic as JSON. [onSubmit] returns an error message to show
 * under the field, or null when the topic was added and the dialog can close.
 */
@Composable
fun AddTopicDialog(onSubmit: (String) -> String?, onDismiss: () -> Unit) {
    var json by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add topic") },
        text = {
            Column(Modifier.widthIn(min = 480.dp)) {
                OutlinedTextField(
                    value = json,
                    onValueChange = {
                        json = it
                        error = null
                    },
                    label = { Text("Topic JSON") },
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp).focusRequester(focus),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = { error = onSubmit(json) }, enabled = json.isNotBlank()) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Cuts text that is too long (e.g. pasted) instead of rejecting the whole edit. */
private fun TextFieldValue.limitTo(max: Int): TextFieldValue {
    if (text.length <= max) return this
    return TextFieldValue(text.take(max), TextRange(minOf(selection.start, max), minOf(selection.end, max)))
}

/** Runs [action] when Enter is pressed in this element. */
fun Modifier.onEnter(action: () -> Unit): Modifier = onPreviewKeyEvent {
    if (it.type == KeyEventType.KeyDown && (it.key == Key.Enter || it.key == Key.NumPadEnter)) {
        action()
        true
    } else {
        false
    }
}

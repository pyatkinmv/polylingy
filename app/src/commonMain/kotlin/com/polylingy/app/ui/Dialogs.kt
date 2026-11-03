package com.polylingy.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.polylingy.core.content.CourseService

/**
 * Edits a course's name (non-blank, at most [CourseService.MAX_NAME_LENGTH] characters) and description (optional,
 * the same markup as in course files). [error] is what saving said last time, e.g. about bad markup.
 */
@Composable
fun CourseDetailsDialog(name: String, description: String, error: String?, onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var nameValue by remember { mutableStateOf(TextFieldValue(name, TextRange(name.length))) }
    var descriptionValue by remember { mutableStateOf(description) }
    val valid = nameValue.text.isNotBlank()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name and description") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nameValue,
                    onValueChange = { nameValue = it.limitTo(CourseService.MAX_NAME_LENGTH) },
                    label = { Text("Name") },
                    singleLine = true,
                    supportingText = { Text("${nameValue.text.length}/${CourseService.MAX_NAME_LENGTH}, different from your other courses") },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                OutlinedTextField(
                    value = descriptionValue,
                    onValueChange = { descriptionValue = it },
                    label = { Text("Description") },
                    minLines = 3,
                    isError = error != null,
                    supportingText = { Text(error ?: "Optional. **bold** and *italic* work as in the course file") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(nameValue.text, descriptionValue) }, enabled = valid) { Text("Save") } },
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

/** Asks before something that can't be undone; the button says what happens ("Delete topic") and is red. */
@Composable
fun DangerDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = Look.Wrong)) { Text(confirm) } },
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

/** Cuts text that is too long (e.g. pasted) instead of rejecting the whole edit. */
private fun TextFieldValue.limitTo(max: Int): TextFieldValue {
    if (text.length <= max) return this
    return TextFieldValue(text.take(max), TextRange(minOf(selection.start, max), minOf(selection.end, max)))
}

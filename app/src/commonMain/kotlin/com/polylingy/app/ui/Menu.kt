package com.polylingy.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** An action in a [MoreMenu]; a [danger] one (remove, delete) is red. */
class MenuItem(val label: String, val enabled: Boolean = true, val danger: Boolean = false, val action: () -> Unit)

/** A "⋯" button opening a menu of actions; [note] explains disabled ones. */
@Composable
fun MoreMenu(items: List<MenuItem>, note: String? = null) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Text("⋯", color = Look.Faint, fontSize = 22.sp) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (item in items) DropdownMenuItem(
                text = { Text(item.label, color = if (item.danger && item.enabled) Look.Wrong else Color.Unspecified) },
                enabled = item.enabled,
                onClick = { open = false; item.action() },
            )
            note?.let { Text(it, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Look.Faint, fontSize = 12.sp) }
        }
    }
}

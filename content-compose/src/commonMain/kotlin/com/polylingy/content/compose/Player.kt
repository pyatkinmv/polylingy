package com.polylingy.content.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The player of a long recording, docked at the bottom of the window while one is loaded: pause or play, a bar to click or
 * drag to any place, the time, and a cross to close it. Buttons in the text only start the sound; this one controls it, so
 * pressing one never needs a player of its own inside the content. Sounds of a word or a phrase don't show it.
 */
@Composable
internal fun PlayerDock(modifier: Modifier = Modifier) {
    val player = LocalAudioPlayer.current
    val playback by player.playback.collectAsState()
    if (!playback.docked) return
    val title = LocalDockTitle.current.text
    val length = playback.durationSeconds
    var dragged by remember { mutableStateOf<Float?>(null) }
    val fraction = dragged ?: (playback.positionSeconds / length).coerceIn(0f, 1f)
    Row(
        modifier.fillMaxWidth().background(colors.card).border(1.dp, colors.line)
            .padding(horizontal = 16.dp, vertical = 8.dp).semantics { contentDescription = "Player" },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportButton(playing = playback.playing, size = 36.dp) {
            if (playback.playing) player.pause() else player.resume()
        }
        title?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(150.dp)) }
        Slider(
            value = fraction,
            onValueChange = { dragged = it },
            onValueChangeFinished = { dragged?.let { player.seekTo(it * length) }; dragged = null },
            modifier = Modifier.weight(1f).height(24.dp).semantics { contentDescription = "Position" },
            colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.line),
        )
        Text(
            "${clock(fraction * length)} / ${clock(length)}",
            style = MaterialTheme.typography.bodySmall, color = colors.muted,
            modifier = Modifier.width(88.dp),
        )
        Box(
            Modifier.size(28.dp).clip(CircleShape).clickable(role = Role.Button) { player.stop() }.semantics { contentDescription = "Close player" },
            contentAlignment = Alignment.Center,
        ) { Text("✕", color = colors.muted, fontSize = 13.sp) }
    }
}

/** A round play button that turns into a pause button while [playing]; the shapes are drawn, not typed, so no font lacks them. */
@Composable
internal fun TransportButton(playing: Boolean, size: Dp, ring: Dp = 2.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).border(ring, colors.accent, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (playing) "Pause" else "Play" },
        contentAlignment = Alignment.Center,
    ) {
        if (playing) Row(horizontalArrangement = Arrangement.spacedBy(size / 8)) {
            repeat(2) { Box(Modifier.width(size / 9).height(size / 3).background(colors.accent, RoundedCornerShape(1.dp))) }
        } else Text("▶", color = colors.accent, fontSize = (size.value * 0.36f).sp)
    }
}

/** Seconds as `m:ss`. */
internal fun clock(seconds: Float): String {
    val whole = seconds.toInt().coerceAtLeast(0)
    return "${whole / 60}:${(whole % 60).toString().padStart(2, '0')}"
}

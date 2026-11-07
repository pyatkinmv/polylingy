package com.polylingy.content.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Whether [url] is on YouTube; only its host is looked at. */
internal fun isYouTube(url: String): Boolean {
    val host = url.removePrefix("https://").substringBefore('/').substringBefore('?').substringBefore(':').lowercase()
    return host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")
}

/** The mark in front of a video card: YouTube's red button for YouTube addresses, a plain play sign for any other. */
@Composable
internal fun VideoIcon(url: String) {
    if (!isYouTube(url)) return Text("▶", color = colors.accent, fontSize = 16.sp)
    Canvas(Modifier.width(28.dp).height(20.dp)) {
        drawRoundRect(Color(0xFFFF0000), size = Size(size.width, size.height), cornerRadius = CornerRadius(size.height * 0.3f))
        val w = size.width
        val h = size.height
        val triangle = Path().apply {
            moveTo(w * 0.40f, h * 0.28f); lineTo(w * 0.66f, h * 0.5f); lineTo(w * 0.40f, h * 0.72f); close()
        }
        drawPath(triangle, Color.White)
    }
}

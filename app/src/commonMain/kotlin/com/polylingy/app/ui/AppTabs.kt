package com.polylingy.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The two top-level screens of the app. */
enum class AppTab(val title: String) { COURSES("Courses"), STATISTICS("Statistics") }

private val StreakBg = Color(0xFFF6E8DA)
private val StreakInk = Color(0xFF9A4B12)

/**
 * The top bar of the two top-level screens: the name, the tabs "Courses" and "Statistics", the streak of days in a row with
 * answers (a click on it opens the statistics) and the screen's own [actions] at the right.
 */
@Composable
fun AppTabs(selected: AppTab, streak: Int, onCourses: () -> Unit, onStatistics: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Column {
        Row(
            Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text("Polylingy", color = Look.Green, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            for (tab in AppTab.entries) Tab(tab.title, tab == selected, if (tab == AppTab.COURSES) onCourses else onStatistics)
            Box(Modifier.weight(1f))
            if (streak > 0) StreakChip(streak, onStatistics)
            actions()
        }
        HorizontalDivider(color = Look.Line)
    }
}

@Composable
private fun Tab(title: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(58.dp)
            .clickable(role = Role.Tab, onClick = onClick)
            .drawBehind { if (selected) drawRect(Look.Green, Offset(0f, size.height - 3.dp.toPx()), Size(size.width, 3.dp.toPx())) },
        contentAlignment = Alignment.Center,
    ) {
        Text(title, color = if (selected) Look.Ink else Look.Muted, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}

/** "12 days": how many days in a row there were answers. */
@Composable
fun StreakChip(days: Int, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(15.dp)
    Row(
        Modifier.height(30.dp)
            .background(StreakBg, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(Modifier.size(12.dp, 15.dp)) {
            val w = size.width
            val h = size.height
            val flame = Path().apply {
                moveTo(w * 0.5f, 0f)
                cubicTo(w * 1.05f, h * 0.42f, w * 1.0f, h, w * 0.5f, h)
                cubicTo(w * 0f, h, w * -0.05f, h * 0.5f, w * 0.5f, 0f)
                close()
            }
            drawPath(flame, StreakInk)
        }
        Text(if (days == 1) "1 day" else "$days days", color = StreakInk, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
    }
}

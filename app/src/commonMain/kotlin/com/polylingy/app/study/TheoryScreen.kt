package com.polylingy.app.study

import com.polylingy.app.ui.LocalCourseSheet
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.polylingy.app.Navigator
import com.polylingy.app.ui.Kicker
import com.polylingy.app.ui.Look
import com.polylingy.app.ui.Page
import com.polylingy.app.ui.TopBar
import com.polylingy.content.compose.ContentColors
import com.polylingy.content.compose.ContentTheme
import com.polylingy.content.compose.ContentView
import com.polylingy.content.compose.ImageLoader
import com.polylingy.content.compose.InlineText
import com.polylingy.core.content.Course
import kotlin.uuid.Uuid

/**
 * A unit's theory. Opened by a link it scrolls to [section] and lights it up; [onBack] then returns to the exercise.
 * When the unit is starting, [onStart] goes on to the exercises.
 */
@Composable
fun TheoryScreen(
    course: Course,
    unitId: Uuid,
    section: Uuid?,
    images: ImageLoader,
    onStart: (() -> Unit)?,
    onBack: (() -> Unit)?,
    go: Navigator,
) {
    val index = course.units.indexOfFirst { it.id == unitId }
    val unit = course.units.getOrNull(index) ?: return
    val theory = unit.theory ?: return
    val links = remember(course) { CourseLinks(course) }
    val accent = Look.Green

    Column(Modifier.fillMaxSize()) {
        TopBar(
            listOf(
                "Courses" to go::courses,
                course.name to { go.backToCourse(course.id) },
                "Unit ${index + 1}" to { go.backToUnit(course.id, unit.id) },
                "Theory" to null,
            ),
        ) {
            if (onBack != null) Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = accent), shape = RoundedCornerShape(8.dp)) {
                Text("← Back to the exercise")
            }
        }
        Page(Modifier.verticalScroll(rememberScrollState())) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Kicker("Theory")
                Text(unit.title, style = MaterialTheme.typography.headlineMedium)
                unit.subtitle?.let { InlineText(it, style = MaterialTheme.typography.bodyLarge.copy(color = Look.Muted)) }
            }
            ContentTheme(null, images, links.names) {
                ContentView(
                    theory,
                    Modifier.padding(top = 8.dp),
                    onLink = { link -> links.target(link)?.let { go.theory(course.id, it.unitId, it.section, back = onBack != null) } },
                    highlight = section,
                )
            }
            if (onStart != null) {
                HorizontalDivider(color = LocalCourseSheet.current?.line ?: Look.Line)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Button(onClick = onStart, colors = ButtonDefaults.buttonColors(containerColor = accent), shape = RoundedCornerShape(8.dp)) {
                        Text("Start exercises →")
                    }
                    Text("Opened by itself because the unit is new", color = Look.Faint, fontSize = 13.sp)
                }
            }
        }
    }
}

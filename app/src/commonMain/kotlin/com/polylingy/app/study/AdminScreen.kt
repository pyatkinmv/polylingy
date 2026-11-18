package com.polylingy.app.study

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.polylingy.app.Navigator
import com.polylingy.app.ui.Kicker
import com.polylingy.app.ui.Look
import com.polylingy.app.ui.Page
import com.polylingy.app.ui.TopBar
import com.polylingy.content.Block
import com.polylingy.content.Exercise
import com.polylingy.content.ItemCheck
import com.polylingy.content.Response
import com.polylingy.content.compose.ContentTheme
import com.polylingy.content.compose.ContentView
import com.polylingy.content.compose.ExerciseView
import com.polylingy.content.compose.ExplanationView
import com.polylingy.content.compose.ImageLoader
import com.polylingy.content.compose.InlineText
import com.polylingy.content.emptyResponse
import com.polylingy.content.isComplete
import com.polylingy.content.solvedResponse
import com.polylingy.core.content.Course
import com.polylingy.core.study.StudyService
import kotlin.uuid.Uuid

/**
 * A unit as the author sees it: the whole theory, then every exercise of every topic one under another, all in one column.
 * For reviewing a course, not for learning: exercises can be answered and checked here, but nothing is written to the
 * journal and no progress changes. [study] is used only to check an answer without recording it.
 *
 * "Show answers" fills every exercise with its key and shows it checked. The shuffled order of options is fixed by the
 * exercise id, so the page looks the same each time it is opened.
 */
@Composable
fun AdminScreen(course: Course, unitId: Uuid, study: StudyService, images: ImageLoader, go: Navigator) {
    val index = course.units.indexOfFirst { it.id == unitId }
    val unit = course.units.getOrNull(index) ?: return
    val links = remember(course) { CourseLinks(course) }
    var showKey by remember { mutableStateOf(false) }
    var highlight by remember { mutableStateOf<Uuid?>(null) }
    // What was entered and checked, per exercise; kept only while the page is open.
    val responses = remember(unitId) { mutableStateMapOf<Uuid, Response>() }
    val checked = remember(unitId) { mutableStateMapOf<Uuid, List<ItemCheck>>() }
    val onLink: (Block.Link) -> Unit = { link ->
        links.target(link)?.let { target ->
            if (target.unitId == unitId) highlight = target.section.also { if (it == highlight) highlight = null }
            else go.theory(course.id, target.unitId, target.section, back = true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            listOf(
                "Courses" to go::courses,
                course.name to { go.backToCourse(course.id) },
                "Unit ${index + 1}" to { go.backToUnit(course.id, unit.id) },
                "Admin" to null,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Show answers", fontSize = 14.sp, color = Look.Muted)
                Switch(checked = showKey, onCheckedChange = { showKey = it })
            }
        }
        Page(Modifier.verticalScroll(rememberScrollState())) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Kicker("Admin · Unit ${index + 1}")
                Text(unit.title, style = MaterialTheme.typography.headlineMedium)
                unit.subtitle?.let { InlineText(it, style = MaterialTheme.typography.bodyLarge.copy(color = Look.Muted)) }
                Text(
                    "Everything of the unit on one page. Answers are checked but not recorded: no progress changes.",
                    color = Look.Faint,
                    fontSize = 13.sp,
                )
            }
            ContentTheme(null, images, links.names, extraKeys = course.extraKeys) {
                unit.theory?.let { ContentView(it, Modifier.padding(top = 8.dp), onLink = onLink, highlight = highlight) }
                unit.topics.forEachIndexed { t, topic ->
                    HorizontalDivider(color = Look.Line)
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Kicker("Topic ${t + 1} · ${topic.exercises.size} ${if (topic.exercises.size == 1) "exercise" else "exercises"}")
                        Text(topic.title, style = MaterialTheme.typography.headlineSmall)
                    }
                    topic.exercises.forEachIndexed { e, exercise ->
                        if (exercise is Exercise.Unknown) {
                            Text("Exercise ${e + 1}: needs a newer version of Polylingy.", color = Look.Muted)
                            return@forEachIndexed
                        }
                        val key = remember(exercise.id) { exercise.solvedResponse() }
                        val response = if (showKey) key else responses[exercise.id] ?: exercise.emptyResponse()
                        val checks = if (showKey) study.preview(course.id, exercise, key) else checked[exercise.id]
                        Column(
                            Modifier.fillMaxWidth().background(Look.Card, RoundedCornerShape(12.dp)).border(1.dp, Look.Line, RoundedCornerShape(12.dp)).padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("Exercise ${e + 1} · ${exercise::class.simpleName?.lowercase()}", fontSize = 12.sp, color = Look.Faint)
                            ExerciseView(
                                exercise = exercise,
                                response = response,
                                checks = checks,
                                onChange = { if (!showKey && checked[exercise.id] == null) responses[exercise.id] = it },
                                onSubmit = {
                                    if (!showKey && exercise.isComplete(response)) checked[exercise.id] = study.preview(course.id, exercise, response)
                                },
                                onLink = onLink,
                                seed = exercise.id.hashCode(),
                            )
                            // The topic's explanation follows the exercise's own one, as in the lessons.
                            val explanation = topic.explanation
                            if (checks != null && explanation != null) ExplanationView(explanation, onLink, title = topic.title)
                            if (!showKey) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (checked[exercise.id] == null) {
                                    Button(
                                        onClick = { checked[exercise.id] = study.preview(course.id, exercise, response) },
                                        enabled = exercise.isComplete(response),
                                        colors = Look.buttonColors(),
                                        shape = RoundedCornerShape(8.dp),
                                    ) { Text("Check") }
                                } else {
                                    OutlinedButton(onClick = { checked.remove(exercise.id); responses.remove(exercise.id) }, shape = RoundedCornerShape(8.dp)) { Text("Again") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

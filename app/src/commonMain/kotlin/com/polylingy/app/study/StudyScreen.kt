package com.polylingy.app.study

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.polylingy.app.Navigator
import com.polylingy.app.course.TheoryButton
import com.polylingy.app.course.accent
import com.polylingy.app.ui.Look
import com.polylingy.app.ui.Page
import com.polylingy.app.ui.StubButton
import com.polylingy.app.ui.TopBar
import com.polylingy.app.ui.dayPhrase
import com.polylingy.content.Exercise
import com.polylingy.content.compose.ContentColors
import com.polylingy.content.compose.ContentTheme
import com.polylingy.content.compose.ExerciseView
import com.polylingy.content.compose.ImageLoader
import com.polylingy.core.content.Course
import kotlinx.datetime.LocalDate

@Composable
fun StudyScreen(viewModel: StudyViewModel, images: ImageLoader, today: LocalDate, go: Navigator) {
    val state by viewModel.state.collectAsState()
    val course = viewModel.course
    val links = remember(course) { course?.let(::CourseLinks) }

    Column(Modifier.fillMaxSize()) {
        val exercise = state as? StudyState.Exercise
        val unit = exercise?.next?.unit ?: course?.units?.firstOrNull { it.id == viewModel.unitId }
        val unitIndex = course?.units?.indexOfFirst { it.id == unit?.id } ?: -1
        val crumbs = buildList {
            add("Courses" to go::courses)
            if (course != null) add(course.name to { go.backToCourse(course.id) })
            if (course != null && unit != null) add("Unit ${unitIndex + 1}" to { go.backToUnit(course.id, unit.id) })
            exercise?.let { add(it.next.topic.title to null) }
        }
        TopBar(crumbs) {
            if (exercise?.review == true) Text(
                "Review",
                Modifier.background(Look.WarnBg, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 2.dp),
                color = Look.Warn,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (course != null && unit?.theory != null) TheoryButton(course.accent()) { go.theory(course.id, unit.id, back = true) }
        }
        when (val s = state) {
            is StudyState.Exercise -> ExerciseBody(s, course!!, links!!, images, today, viewModel, go)
            is StudyState.Done -> DoneBody(s, viewModel.unitId != null, today, go)
            StudyState.Missing -> Page {
                Text("Nothing to show here.", color = Look.Muted)
                Button(onClick = go::back) { Text("Back") }
            }
        }
    }
}

@Composable
private fun ExerciseBody(
    state: StudyState.Exercise,
    course: Course,
    links: CourseLinks,
    images: ImageLoader,
    today: LocalDate,
    viewModel: StudyViewModel,
    go: Navigator,
) {
    val accent = course.accent()
    Page(Modifier.verticalScroll(rememberScrollState())) {
        state.outcome?.let { outcome ->
            val checks = outcome.checks
            val (fg, bg) = when {
                checks.any { it.result == com.polylingy.content.ItemResult.WRONG } -> Look.Wrong to Look.WrongBg
                checks.any { it.result == com.polylingy.content.ItemResult.TYPO } -> Look.Warn to Look.WarnBg
                else -> Look.Green to Color(0xFFE4F0E7)
            }
            Column(Modifier.fillMaxWidth().background(bg, RoundedCornerShape(10.dp)).padding(horizontal = 18.dp, vertical = 12.dp)) {
                Text(checkLine(checks), color = fg, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                progressLine(outcome) { dayPhrase(it, today) }?.let { Text(it, color = fg) }
            }
        }
        ContentTheme(ContentColors.forAccent(course.theme?.accent), images, links.names) {
            ExerciseView(
                exercise = state.next.exercise,
                response = state.response,
                checks = state.checks,
                onChange = viewModel::change,
                onSubmit = viewModel::check,
                onLink = { link -> links.target(link)?.let { go.theory(course.id, it.unitId, it.section, back = true) } },
                seed = state.seed,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val colors = ButtonDefaults.buttonColors(containerColor = accent)
            if (state.outcome == null) {
                Button(onClick = viewModel::check, colors = colors, shape = RoundedCornerShape(8.dp)) { Text("Check") }
                if (state.next.exercise.hasBlanks()) Text("Tab — next blank · Enter — check", color = Look.Faint, fontSize = 13.sp)
            } else {
                Button(onClick = viewModel::next, colors = colors, shape = RoundedCornerShape(8.dp)) { Text("Next →") }
            }
        }
    }
}

private fun Exercise.hasBlanks() = this is Exercise.Gaps || this is Exercise.Pictures || this is Exercise.Table

@Composable
private fun DoneBody(state: StudyState.Done, inUnit: Boolean, today: LocalDate, go: Navigator) {
    val counts = state.counts
    Box(Modifier.fillMaxWidth().padding(top = 72.dp), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(72.dp).background(Color(0xFFE4F0E7), CircleShape), contentAlignment = Alignment.Center) {
                Text("✓", color = Look.Green, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            }
            Text("That’s all for today", style = MaterialTheme.typography.headlineMedium)
            val where = if (inUnit) "in this unit" else "in this course"
            Text(
                counts.nextReview?.let { "Nothing is due $where. The next review is ${dayPhrase(it, today)}." }
                    ?: "Everything $where is learned for good.",
                color = Look.Muted,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                "${counts.studied} of ${counts.total} studied · ${counts.learnedForGood} learned for good",
                Modifier.background(Look.Card, RoundedCornerShape(10.dp)).border(1.dp, Look.Line, RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                color = Look.Muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = go::back, shape = RoundedCornerShape(8.dp)) { Text(if (inUnit) "Back to the unit" else "Back to the course") }
                StubButton("Extra practice")
            }
        }
    }
}

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.polylingy.app.Navigator
import com.polylingy.app.ui.Look
import com.polylingy.app.ui.Page
import com.polylingy.app.ui.PracticeButton
import com.polylingy.app.ui.QuietButton
import com.polylingy.app.ui.TopBar
import com.polylingy.content.compose.ImageLoader
import com.polylingy.core.study.PracticeScope
import kotlinx.datetime.LocalDate

/**
 * Extra practice: the exercise screen of studying, without the plan's statuses, under a blue strip that says it changes nothing,
 * with "Finish"; then a summary. The plan isn't touched (docs/learning-model.md, "Extra practice").
 */
@Composable
fun PracticeScreen(viewModel: PracticeViewModel, images: ImageLoader, today: LocalDate, go: Navigator) {
    val ui by viewModel.state.collectAsState()
    val course = viewModel.course
    val links = remember(course) { course?.let(::CourseLinks) }
    val unit = (viewModel.scope as? PracticeScope.Unit)?.let { s -> course?.units?.firstOrNull { it.id == s.unitId } }
    val unitIndex = course?.units?.indexOfFirst { it.id == unit?.id } ?: -1

    Column(Modifier.fillMaxSize()) {
        val crumbs = buildList {
            add("Courses" to go::courses)
            if (course != null) add(course.name to { go.backToCourse(course.id) })
            if (course != null && unit != null) add("Unit ${unitIndex + 1}" to { go.backToUnit(course.id, unit.id) })
            add("Practice" to null)
        }
        TopBar(crumbs) { if (ui is PracticeUi.Exercise) QuietButton("Finish") { viewModel.finish() } }
        when (val s = ui) {
            is PracticeUi.Exercise -> {
                PracticeStrip(s.answered, s.right)
                ExerciseBody(s.state, course!!, links!!, images, today, viewModel::change, viewModel::check, viewModel::next, go)
            }
            is PracticeUi.Summary -> SummaryBody(s.summary, viewModel::more, go)
            PracticeUi.Nothing -> Page {
                Text("Nothing to practise here yet: extra practice goes over topics you have already started.", color = Look.Muted)
                Button(onClick = go::back) { Text("Back") }
            }
        }
    }
}

/** The blue strip under the top bar: this is practice, it changes nothing, and how it is going. */
@Composable
private fun PracticeStrip(answered: Int, right: Int) {
    Row(
        Modifier.fillMaxWidth().background(Look.PracticeBg).padding(horizontal = 32.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("EXTRA PRACTICE", color = Look.Practice, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, letterSpacing = 0.8.sp)
        Text("doesn’t change your plan", color = Look.Muted, fontSize = 13.sp)
        Box(Modifier.weight(1f))
        Text("$answered answered · $right right", color = Look.Muted, fontSize = 13.sp)
    }
}

@Composable
private fun SummaryBody(summary: PracticeSummary, onMore: () -> Unit, go: Navigator) {
    Box(Modifier.fillMaxWidth().padding(top = 56.dp), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.padding(horizontal = 32.dp).fillMaxWidth(0.8f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(68.dp).background(Look.PracticeBg, CircleShape), contentAlignment = Alignment.Center) {
                Text("✓", color = Look.Practice, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
            Text("Practice finished", style = MaterialTheme.typography.headlineMedium)
            Text(
                if (summary.answered == 0) "Nothing was answered." else "${summary.answered} answered · ${summary.right} right (${summary.percent}%)",
                color = Look.Muted, fontSize = 17.sp, textAlign = TextAlign.Center,
            )
            if (summary.byTopic.isNotEmpty()) {
                val shape = RoundedCornerShape(12.dp)
                Column(Modifier.fillMaxWidth().background(Look.Card, shape).border(1.dp, Look.Line, shape)) {
                    summary.byTopic.forEachIndexed { i, t ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(t.title, Modifier.weight(1f), fontSize = 15.sp)
                            Text(if (t.answered == 1) "1 answer" else "${t.answered} answers", color = Look.Muted, fontSize = 13.sp)
                            Text("${t.percent}%", fontWeight = FontWeight.SemiBold, color = if (t.percent >= 80) Look.LearnedForGood else Look.Wrong)
                        }
                        if (i < summary.byTopic.lastIndex) androidx.compose.material3.HorizontalDivider(color = Look.Line)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = go::back, shape = RoundedCornerShape(8.dp)) { Text("Back") }
                PracticeButton(onMore, "Practise more")
            }
            Text("Your plan and your reviews are exactly as they were.", color = Look.Faint, fontSize = 13.sp)
        }
    }
}

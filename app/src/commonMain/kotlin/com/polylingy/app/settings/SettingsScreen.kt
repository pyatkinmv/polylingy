package com.polylingy.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.polylingy.app.Navigator
import com.polylingy.app.ui.DangerDialog
import com.polylingy.app.ui.Look
import com.polylingy.app.ui.MessageDialog
import com.polylingy.app.ui.Page
import com.polylingy.app.ui.QuietButton
import com.polylingy.app.ui.TopBar
import com.polylingy.core.content.DisplaySpec
import com.polylingy.core.content.GradingSpec
import com.polylingy.core.content.MasterySpec
import com.polylingy.core.content.PickerSpec
import com.polylingy.core.content.Preset
import com.polylingy.core.content.RulesSpec
import com.polylingy.core.content.SchedulerSpec
import com.polylingy.core.study.ProgressCounts
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

/** "5 Nov". */
private fun dayMonth(date: LocalDate) = "${date.day} ${date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)}"

private val Example = Color(0xFFFBFAF6)
private val ExampleEdge = Color(0xFFF0EADC)
private val Chosen = Color(0xFFF3F7F1)
private val SetsBg = Color(0xFFF3EFE4)

/** How a ready set is called and explained on the screen; the file calls them quick, ladder and sm2. */
private fun Preset.title() = when (this) {
    Preset.QUICK -> "Quick pass"
    Preset.LADDER -> "Spaced review"
    Preset.SM2 -> "Lifelong review"
}

private fun Preset.about() = when (this) {
    Preset.QUICK -> "Right once and it’s done — no reviews. To go through a book fast."
    Preset.LADDER -> "3 right in a row, then reviews after 1, 3, 7, 14, 30 days."
    Preset.SM2 -> "3 right in a row, then reviews with gaps that grow for each topic."
}

/**
 * The study settings of a course (variant G): four questions are the settings, each with an example that follows the
 * answer; a ready set only fills in questions 1–3. Nothing is saved until Save.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, today: LocalDate, go: Navigator) {
    val course = viewModel.course ?: return
    val draft by viewModel.draft.collectAsState()
    val after by viewModel.after.collectAsState()
    var discard by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Keyed on the draft, so it is read here and the bar on top gets it as a value: Compose skips the bar unless that changes.
    val changed = remember(draft) { viewModel.changed }

    Column(Modifier.fillMaxSize()) {
        TopBar(listOf("Courses" to go::courses, course.name to { go.backToCourse(course.id) }, "Study settings" to null)) {
            QuietButton("Cancel") { if (changed) discard = true else go.back() }
            Button(
                onClick = { error = viewModel.save(); if (error == null) go.back() },
                enabled = changed,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Look.Green),
            ) { Text("Save") }
        }
        Page(Modifier.verticalScroll(rememberScrollState())) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Study settings", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Four questions decide how this course teaches you. Your answers below are the settings — change any of them. " +
                        "A ready set is only a shortcut that fills in questions 1–3 at once.",
                    color = Look.Muted,
                    fontSize = 15.sp,
                )
            }
            ReadySets(viewModel.matching, viewModel::usePreset)
            AnswerCounts(draft, viewModel)
            TopicDone(draft.mastery as MasterySpec.Consecutive, viewModel)
            // Fixed steps show the whole ladder and one doubled gap; growing gaps a few, as they soon run far ahead.
            ComesBack(draft.scheduler!!, viewModel.plan(if (draft.scheduler is SchedulerSpec.Sm2) 4 else 6), today, viewModel)
            HorizontalDivider(color = Look.Line)
            WhatComesNext(draft.picker!!, viewModel)
            HowItLooks(draft.display ?: DisplaySpec(), viewModel.typing, viewModel)
            Recount(viewModel.recounts, viewModel.before, after)
        }
    }
    if (discard) DangerDialog(
        title = "Discard changes?",
        text = "Your changes to the settings will be lost.",
        confirm = "Discard",
        onConfirm = { discard = false; go.back() },
        onDismiss = { discard = false },
    )
    error?.let { MessageDialog("Can’t save the settings", it) { error = null } }
}

@Composable
private fun ReadySets(matching: Preset?, onUse: (Preset) -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(SetsBg, RoundedCornerShape(14.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Fill in questions 1–3 with a ready set", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(
                matching?.let { "Your answers 1–3 match “${it.title()}”." } ?: "Your answers 1–3 are your own — no set matches. That’s fine.",
                color = Look.Muted,
                fontSize = 14.sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (preset in Preset.entries) {
                val on = preset == matching
                Column(
                    Modifier
                        .weight(1f)
                        .height(118.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Look.Card)
                        .border(if (on) 2.dp else 1.dp, if (on) Look.Green else Look.LineHover, RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button) { onUse(preset) }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(preset.title(), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        if (on) Text("✓ matches", color = Look.Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(preset.about(), color = Look.Muted, fontSize = 13.sp, lineHeight = 18.sp)
                    Text("fills in 1 · 2 · 3", color = Look.Faint, fontSize = 12.sp)
                }
            }
        }
    }
}

/** A question card: its number, the question, which policy it is, then its answer. */
@Composable
private fun Question(number: Int, question: String, policy: String, fill: Color = Look.Green, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().background(Look.Card, shape).border(1.dp, Look.Line, shape).padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(26.dp).background(fill, CircleShape), contentAlignment = Alignment.Center) {
                Text("$number", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Text(question, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(policy, color = Look.Faint, fontSize = 12.sp)
        }
        Column(Modifier.padding(start = 36.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** One option of a choice: a green edge when chosen. */
@Composable
private fun Choice(text: String, chosen: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        text,
        Modifier
            .clip(shape)
            .background(if (chosen) Chosen else Look.Card, shape)
            .border(if (chosen) 2.dp else 1.dp, if (chosen) Look.Green else Look.LineHover, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        fontSize = 14.sp,
        fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
    )
}

/** A larger option with an explanation under its name. */
@Composable
private fun ChoiceRow(title: String, about: String, chosen: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (chosen) Chosen else Look.Card, shape)
            .border(if (chosen) 2.dp else 1.dp, if (chosen) Look.Green else Look.LineHover, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(about, color = Look.Muted, fontSize = 13.sp)
    }
}

/** A number with − and + around it; [label] names it for the tests and screen readers. */
@Composable
private fun Stepper(label: String, value: String, onChange: (Int) -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(Modifier.border(1.dp, Look.LineHover, shape), verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", "$label, fewer") { onChange(-1) }
        Text(value, Modifier.width(44.dp).semantics { contentDescription = label }, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        StepButton("+", "$label, more") { onChange(+1) }
    }
}

@Composable
private fun StepButton(text: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Look.Muted, fontSize = 16.sp) }
}

@Composable
private fun Check(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Checkbox) { onChange(!checked) }.padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, onCheckedChange = null, Modifier.padding(6.dp), colors = CheckboxDefaults.colors(checkedColor = Look.Green))
        Text(text, fontSize = 15.sp)
    }
}

/** How exercises look: annotations over words, and whether to avoid typing; not one of the four questions. */
@Composable
private fun HowItLooks(display: DisplaySpec, typing: Pair<Int, Int>, viewModel: SettingsViewModel) {
    val shape = RoundedCornerShape(14.dp)
    val (typingOnly, topics) = typing
    Column(
        Modifier.fillMaxWidth().background(Look.Card, shape).border(1.dp, Look.Line, shape).padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("How exercises look", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Check("Show annotations over text", display.annotations, viewModel::annotations)
        Text("Small text a course puts over words — how to read them, say. Off, only the words show.", Modifier.padding(start = 42.dp), color = Look.Muted, fontSize = 13.sp)
        Check("Avoid typing when a topic has other exercises", display.avoidTyping, viewModel::avoidTyping)
        Text("Exercises with choices, tiles and pairs come first. A topic that has only typing exercises keeps them.", Modifier.padding(start = 42.dp), color = Look.Muted, fontSize = 13.sp)
        Example(
            if (typingOnly == 0) "Every topic of this course has exercises without typing."
            else "In this course $typingOnly of $topics topics have only typing exercises — there you still type.",
        )
    }
}

/** The example under a question: it follows the answer. */
@Composable
private fun Example(vararg lines: String) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier.fillMaxWidth().background(Example, shape).border(1.dp, ExampleEdge, shape).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (line in lines) Text(line, color = Color(0xFF4F4A42), fontSize = 14.sp)
    }
}

@Composable
private fun AnswerCounts(draft: RulesSpec, viewModel: SettingsViewModel) {
    val grading = draft.grading!!
    Question(1, "When does an answer count?", "grading") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("All items right", grading is GradingSpec.AllCorrect, viewModel::allCorrect)
            Choice("Most items right", grading is GradingSpec.Threshold, viewModel::mostCorrect)
            if (grading is GradingSpec.Threshold) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("at least", fontSize = 14.sp)
                Stepper("Share of items", "${(grading.min * 100).roundToInt()}%") { viewModel.changeMin(it * 5) }
            }
        }
        Check("Forgive one typo per item", grading.typos, viewModel::typos)
        val counts = { percent: Int -> if (grading is GradingSpec.Threshold && percent >= (grading.min * 100).roundToInt()) "counts" else "doesn’t count" }
        Example(
            if (grading is GradingSpec.Threshold) "Example: 5 of 6 right (83%) → ${counts(83)}; 1 of 2 right (50%) → ${counts(50)}."
            else "Example: 5 of 6 blanks right → doesn’t count.",
            if (grading.typos) "“is wroking” for “is working” → counts; the right spelling is shown." else "“is wroking” for “is working” → wrong.",
        )
    }
}

@Composable
private fun TopicDone(mastery: MasterySpec.Consecutive, viewModel: SettingsViewModel) {
    Question(2, "When is a topic done for now?", "mastery") {
        Text("After this many counted answers in a row. A wrong one starts the count again.", color = Look.Muted, fontSize = 14.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InARow("First time", "learning a new topic", mastery.learning) { viewModel.changeLearning(it) }
            InARow("Review", "when it comes back", mastery.review) { viewModel.changeReview(it) }
        }
        val ticks = { n: Int -> List(n) { "✓" }.joinToString(" ") }
        Example(
            "First time: ✗ ${ticks(mastery.learning)} → done on answer ${mastery.learning + 1}; a ✗ starts the count again.",
            "Review: ${ticks(mastery.review)} → done again${if (mastery.review == 1) " — one right answer is enough." else "."}",
        )
    }
}

@Composable
private fun InARow(title: String, about: String, value: Int, onChange: (Int) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.border(1.dp, Look.Line, shape).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(about, color = Look.Faint, fontSize = 12.sp)
        }
        Stepper("$title in a row", "$value", onChange)
    }
}

@Composable
private fun ComesBack(scheduler: SchedulerSpec, plan: ReviewPlan, today: LocalDate, viewModel: SettingsViewModel) {
    Question(3, "When does a done topic come back?", "schedule") {
        ChoiceRow("It doesn’t", "Once done, it’s learned for good.", scheduler is SchedulerSpec.Once) { viewModel.scheduler(SchedulerSpec.Once) }
        val ladder = scheduler as? SchedulerSpec.Ladder ?: SchedulerSpec.Ladder()
        ChoiceRow(
            "After fixed steps: ${ladder.days.joinToString(" · ")} days",
            "A clean review moves one step further; a review with a mistake goes back to ${ladder.days.first()} day${if (ladder.days.first() == 1) "" else "s"}.",
            scheduler is SchedulerSpec.Ladder,
        ) { viewModel.scheduler(ladder) }
        if (scheduler is SchedulerSpec.Ladder) Box(Modifier.padding(start = 8.dp)) {
            Check("After the last step — learned for good (otherwise the gap doubles each time)", scheduler.retire, viewModel::retire)
        }
        ChoiceRow(
            "After growing gaps (SM-2 formula)",
            "Each topic gets its own gap: longer after clean reviews, shorter after mistakes. Never stops.",
            scheduler is SchedulerSpec.Sm2,
        ) { viewModel.scheduler(SchedulerSpec.Sm2) }
        Example(
            when {
                plan.dates.isEmpty() -> "Done today → never offered by itself again; you can still open it and practise."
                else -> "Done today, ${dayMonth(today)} → " + plan.dates.joinToString(" → ", transform = ::dayMonth) +
                    if (plan.learned) " → learned for good." else " → …"
            },
            when (scheduler) {
                SchedulerSpec.Once -> "That’s the whole schedule."
                is SchedulerSpec.Ladder -> "A review with a mistake → back to ${scheduler.days.first()} day${if (scheduler.days.first() == 1) "" else "s"}, and up the steps again."
                SchedulerSpec.Sm2 -> "Gaps of " + (listOf(today) + plan.dates).zipWithNext { a, b -> (b.toEpochDays() - a.toEpochDays()).toString() }.joinToString(", ") +
                    " days… Mistakes make them shorter."
            },
        )
    }
}

@Composable
private fun WhatComesNext(picker: PickerSpec, viewModel: SettingsViewModel) {
    Question(4, "What comes next in a session?", "order", fill = Look.Muted) {
        Text("Not part of the sets: it changes only the order you see topics in, never what’s learned.", color = Look.Muted, fontSize = 14.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Conveyor", picker is PickerSpec.Conveyor) { viewModel.picker(PickerKind.CONVEYOR) }
            Choice("Shuffle", picker is PickerSpec.Shuffle) { viewModel.picker(PickerKind.SHUFFLE) }
            Choice("Alternate", picker is PickerSpec.Alternate) { viewModel.picker(PickerKind.ALTERNATE) }
        }
        when (picker) {
            is PickerSpec.Conveyor -> Example("Example: 1.1 1.1 1.1 → 1.2 1.2 → 1.3 …", "Topics in course order; stays on a topic until it’s done. Unfinished and due ones first.")
            is PickerSpec.Shuffle -> Example("Example: 2.4 2.4 → 1.1 1.1 1.1 → 1.3 …", "Same as Conveyor, but topics in random order.")
            is PickerSpec.Alternate -> Example("Example: 1.1 → 1.2 → 1.1 → 1.3 → 1.2 …", "Another topic after every answer, like Anki cards; one you got wrong comes back a little later.")
        }
        HorizontalDivider(color = ExampleEdge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("New topics a day", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text("Reviews are never limited.", color = Look.Muted, fontSize = 13.sp)
            }
            Stepper("New topics a day", picker.newPerDay?.toString() ?: "—", viewModel::changeLimit)
            Check("No limit", picker.newPerDay == null, viewModel::noLimit)
        }
    }
}

/** What saving does to progress: questions 1–3 recount it from all answers; numbers before and after. */
@Composable
private fun Recount(recounts: Boolean, before: ProgressCounts?, after: ProgressCounts?) {
    val shape = RoundedCornerShape(12.dp)
    val text = if (recounts && before != null && after != null) {
        "Questions 1–3 changed: on Save your progress is recounted from all your answers — " +
            "learned ${before.learned} → ${after.learned} · to review ${before.toReview} → ${after.toReview}. Your answers themselves stay."
    } else {
        "Nothing that affects progress has changed. Question 4 and the daily limit never recount progress."
    }
    Text(
        text,
        Modifier.fillMaxWidth().background(if (recounts) Look.WarnBg else Color(0xFFEFEBE1), shape).padding(horizontal = 18.dp, vertical = 14.dp),
        color = if (recounts) Color(0xFF4A3500) else Look.Muted,
        fontSize = 14.sp,
    )
}

package com.polylingy.app.settings

import com.polylingy.content.compose.Palettes
import com.polylingy.content.compose.Palette
import com.polylingy.content.compose.InlineText
import com.polylingy.content.compose.ContentView
import com.polylingy.content.compose.ContentTheme
import com.polylingy.content.compose.ContentColors
import com.polylingy.content.Inline
import com.polylingy.content.CourseStyle
import com.polylingy.content.Content
import com.polylingy.content.Cell
import com.polylingy.content.Block
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.polylingy.core.content.ExerciseSpec
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
            // Fixed steps show the whole ladder and two doubled gaps after it; growing gaps a few, as they soon run far ahead.
            ComesBack(draft.scheduler!!, viewModel.plan(when (val sc = draft.scheduler) { is SchedulerSpec.Ladder -> sc.days.size + LadderSteps.DOUBLED_SHOWN; SchedulerSpec.Sm2 -> 4; else -> 6 }), today, viewModel)
            HorizontalDivider(color = Look.Line)
            WhatComesNext(draft.picker!!, viewModel)
            WhichExercise(draft.picker!!.exercise, viewModel)
            CourseLookCard(draft.display ?: DisplaySpec(), viewModel)
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

/**
 * How the course looks: its palette and its style, and under them a preview that follows the choice. This changes only how the course
 * is drawn, never what is learned, so it is not one of the four questions and never recounts progress.
 */
@Composable
private fun CourseLookCard(display: DisplaySpec, viewModel: SettingsViewModel) {
    val shape = RoundedCornerShape(14.dp)
    val palette = Palettes.byId(display.palette)
    Column(
        Modifier.fillMaxWidth().background(Look.Card, shape).border(1.dp, Look.Line, shape).padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("How the course looks", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("look", color = Look.Faint, fontSize = 12.sp)
        }
        Text("Colours and style of the theory and the exercises. This changes only how the course is drawn, never what you learn.", color = Look.Muted, fontSize = 13.sp)

        Text("Palette", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (option in Palettes.all) PaletteOption(option, option.id == palette.id) { viewModel.palette(option.id) }
        }

        Text("Style", Modifier.padding(top = 4.dp), fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StyleOption("Soft", "Marks sit on a coloured background.", CourseStyle.SOFT, display.style, palette.colors, Modifier.weight(1f)) { viewModel.style(CourseStyle.SOFT) }
            StyleOption("Strict", "Marks are underlined, with no background.", CourseStyle.STRICT, display.style, palette.colors, Modifier.weight(1f)) { viewModel.style(CourseStyle.STRICT) }
        }
        Text("More styles — sharper or rounder corners — can be added here later.", color = Look.Faint, fontSize = 12.sp)

        Text("Preview", Modifier.padding(top = 4.dp), fontWeight = FontWeight.SemiBold)
        ContentTheme(palette.colors, style = display.style) {
            Row(Modifier.semantics { contentDescription = "Preview of the course look" }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniScreen("Theory", palette.colors, Modifier.weight(1f), previewTheory())
                MiniScreen("Remarks", palette.colors, Modifier.weight(1f), previewRemarks())
                MiniScreen("Dialogue and table", palette.colors, Modifier.weight(1f), previewDialogue())
            }
        }
    }
}

/** A palette to choose: its name and five of its colours. */
@Composable
private fun PaletteOption(palette: Palette, chosen: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val colors = palette.colors
    Column(
        Modifier
            .width(170.dp)
            .clip(shape)
            .background(if (chosen) Chosen else Look.Card, shape)
            .border(if (chosen) 2.dp else 1.dp, if (chosen) Look.Green else Look.LineHover, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "Palette ${palette.name}" }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(palette.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (chosen) Text("✓", color = Look.Green, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (swatch in listOf(colors.background, colors.accent, colors.accentSoft, colors.marks[0].fill, colors.marks[1].fill)) {
                Box(Modifier.size(20.dp).background(swatch, RoundedCornerShape(5.dp)).border(1.dp, Color(0x24000000), RoundedCornerShape(5.dp)))
            }
        }
    }
}

/** A style to choose: its name, what it does and a line of text drawn in it with the palette's colours. */
@Composable
private fun StyleOption(title: String, about: String, style: CourseStyle, chosen: CourseStyle, colors: ContentColors, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val on = style == chosen
    Column(
        modifier
            .clip(shape)
            .background(if (on) Chosen else Look.Card, shape)
            .border(if (on) 2.dp else 1.dp, if (on) Look.Green else Look.LineHover, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "Style $title" }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            if (on) Text("✓", color = Look.Green, fontWeight = FontWeight.Bold)
        }
        Text(about, color = Look.Muted, fontSize = 13.sp)
        ContentTheme(colors, style = style) { InlineText(Inline("корень Москва и окончание ==һаа=="), style = TextStyle(fontSize = 16.sp)) }
    }
}

/** A small screen of the course drawn with the real blocks, shrunk to fit: [blocks] on the palette's background. */
@Composable
private fun MiniScreen(title: String, colors: ContentColors, modifier: Modifier, blocks: List<Block>) {
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.height(280.dp).clip(shape).background(colors.background, shape).border(1.dp, colors.line, shape)) {
        Text(title, Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), color = colors.muted, fontSize = 10.sp)
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
            val design = 420.dp
            val scale = maxWidth / design
            Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).width(design).graphicsLayer(scaleX = scale, scaleY = scale, transformOrigin = TransformOrigin(0f, 0f)).padding(14.dp)) {
                ContentView(Content.Blocks(blocks))
            }
        }
    }
}

private fun text(markup: String, role: Block.Text.Role? = null) = Block.Text(Inline(markup), role)
private fun cell(markup: String) = Cell(Inline(markup))

private fun previewTheory(): List<Block> = listOf(
    Block.Text(Inline("Родительный падеж отвечает на вопрос ==хэнэй?== (чей?)"), Block.Text.Role.LEAD),
    Block.Tip(listOf(text("После краткой гласной: ==-ын==, ==-иин==. Дарима — Дарим=={2}ын==.")), Block.Tip.Kind.RULE),
    Block.Table(
        head = listOf(Inline("Слово"), Inline("Форма")),
        rows = listOf(listOf(cell("багша"), cell("багш==ын==")), listOf(cell("Дарима"), cell("Дарим==ын==")), listOf(cell("малгай"), cell("малгай==н=="))),
    ),
)

private fun previewRemarks(): List<Block> = listOf(
    Block.Tip(listOf(text("=={4}хэн?== — только о людях, =={4}юун?== — обо всём остальном.")), Block.Tip.Kind.WARNING),
    Block.Tip(listOf(text("Слово **далай** значит и «море», и «большое озеро».")), Block.Tip.Kind.FACT),
    Block.Tip(listOf(text("Проходя мимо кучи камней — **обо** — оставляют подношение.")), Block.Tip.Kind.CULTURE, title = Inline("Обо")),
)

private fun previewDialogue(): List<Block> = listOf(
    Block.Dialogue(
        lines = listOf(
            Block.Line(Inline("Дарима"), Inline("Аюр, ши ==хаанаһаа== ерээбши?")),
            Block.Line(Inline("Аюр"), Inline("=={2}Шэтэһээ==.")),
        ),
        look = Block.Dialogue.Style.BUBBLES,
    ),
    Block.Table(
        head = listOf(Inline("Слово"), Inline("Окончание"), Inline("Форма")),
        rows = listOf(listOf(cell("Москва"), cell("һаа"), cell("Москва==һаа==")), listOf(cell("Шэтэ"), cell("һээ"), cell("Шэтэ==һээ=="))),
        appearance = Block.Table.Appearance(headerRow = Block.Table.Appearance.HeaderRow.HIGHLIGHTED, highlightedColumns = listOf(0), stripedRows = true),
    ),
)

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
            "After fixed steps",
            "A clean review moves one step further; a review with a mistake goes back to the first step.",
            scheduler is SchedulerSpec.Ladder,
        ) { viewModel.scheduler(ladder) }
        if (scheduler is SchedulerSpec.Ladder) LadderChips(scheduler, plan, today, viewModel)
        ChoiceRow(
            "After growing gaps (SM-2 formula)",
            "Each topic gets its own gap: longer after clean reviews, shorter after mistakes. Never stops.",
            scheduler is SchedulerSpec.Sm2,
        ) { viewModel.scheduler(SchedulerSpec.Sm2) }
        // The fixed steps explain themselves with their dates; the other two answers get an example.
        when (scheduler) {
            SchedulerSpec.Once -> Example("Done today → never offered by itself again; you can still open it and practise.", "That’s the whole schedule.")
            is SchedulerSpec.Ladder -> Unit
            SchedulerSpec.Sm2 -> Example(
                "Done today, ${dayMonth(today)} → " + plan.dates.joinToString(" → ", transform = ::dayMonth) + if (plan.learned) " → learned for good." else " → …",
                "Gaps of " + (listOf(today) + plan.dates).zipWithNext { a, b -> (b.toEpochDays() - a.toEpochDays()).toString() }.joinToString(", ") +
                    " days… Mistakes make them shorter.",
            )
        }
    }
}

private fun dayLabel(date: LocalDate, today: LocalDate) = dayMonth(date) + if (date.year != today.year) " ${date.year}" else ""

private fun days(n: Int) = "$n day${if (n == 1) "" else "s"}"

/**
 * The steps of a ladder, drawn: each is the days after the previous review, with its date under it (for finishing today).
 * After the last one either "learned for good" or, dashed, the gaps that keep doubling. The steps themselves aren't edited:
 * a course only chooses where the ladder ends.
 */
@Composable
private fun LadderChips(ladder: SchedulerSpec.Ladder, plan: ReviewPlan, today: LocalDate, viewModel: SettingsViewModel) {
    val steps = ladder.days
    Column(Modifier.padding(start = 10.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Days after the previous review; the dates are for finishing today, ${dayMonth(today)}.", color = Look.Faint, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.forEachIndexed { i, d ->
                StepChip("Step ${i + 1}, ${days(d)}", d, plan.dates.getOrNull(i)?.let { dayLabel(it, today) }, ghost = false)
            }
            if (ladder.retire) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("✓ learned", Modifier.background(Look.Green, RoundedCornerShape(9.dp)).padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("for good", color = Look.LearnedForGood, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                var gap = steps.last()
                for (k in 0 until LadderSteps.DOUBLED_SHOWN) {
                    gap *= 2
                    StepChip(null, gap, plan.dates.getOrNull(steps.size + k)?.let { dayLabel(it, today) }, ghost = true)
                }
                Text("…", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = Look.Faint)
            }
        }
        Check("After the last step — learned for good (otherwise the gap doubles each time)", ladder.retire, viewModel::retire)
        if (ladder.retire) {
            Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Last step:", color = Look.Muted, fontSize = 14.sp)
                for (last in LadderSteps.LAST_STEPS) {
                    Choice(LadderSteps.title(last), steps == LadderSteps.upTo(last)) { viewModel.lastStep(last) }
                }
            }
        }
        Text("A review with a mistake → back to ${days(steps.first())}, and up the steps again.", color = Look.Faint, fontSize = 13.sp)
    }
}

/** A step of the ladder: the days, with the date of that review under it; [label] names it for the tests and screen readers. */
@Composable
private fun StepChip(label: String?, days: Int, date: String?, ghost: Boolean) {
    val shape = RoundedCornerShape(9.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        var chip = Modifier.clip(shape).border(1.dp, Look.LineHover, shape)
        if (label != null) chip = chip.semantics { contentDescription = label }
        Row(chip.padding(horizontal = 11.dp, vertical = 5.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$days", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (ghost) Look.Faint else Color.Unspecified)
            Text(if (days == 1) "day" else "days", color = if (ghost) Look.Faint else Look.Muted, fontSize = 12.5.sp, modifier = Modifier.padding(bottom = 1.dp))
        }
        if (date != null) Text(date, color = Look.Faint, fontSize = 12.sp)
    }
}

@Composable
private fun WhatComesNext(picker: PickerSpec, viewModel: SettingsViewModel) {
    Question(4, "What comes next in a session?", "order", fill = Look.Muted) {
        Text("Not part of the sets: it changes only the order you see topics in, never what’s learned. Which exercise of a topic comes next is question 5.", color = Look.Muted, fontSize = 14.sp)
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

/** How an exercise looks in a drawn example: [mark] — what happened to it; [back] — it comes back now because it was missed earlier. */
private data class Ex(val letter: String, val mark: Boolean? = null, val back: Boolean = false)

@Composable
private fun ExerciseChip(ex: Ex) {
    val shape = RoundedCornerShape(7.dp)
    val bg = when (ex.mark) { true -> Color(0xFFE4F0E7); false -> Look.WrongBg; null -> Look.Card }
    val ink = when (ex.mark) { true -> Color(0xFF1F5A39); false -> Look.Wrong; null -> Look.Ink }
    Row(
        Modifier.clip(shape).background(bg, shape).border(if (ex.back) 2.dp else 1.dp, if (ex.back) Look.Green else Look.LineHover, shape).padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(ex.letter, color = ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        when (ex.mark) { true -> Text("✓", color = ink, fontSize = 12.sp); false -> Text("✗", color = ink, fontSize = 12.sp); null -> {} }
    }
}

/** A row of exercises with a caption: what was answered before, or what comes now. */
@Composable
private fun ExerciseRow(caption: String, exercises: List<Ex>, arrows: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(caption, color = Look.Muted, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            exercises.forEachIndexed { i, ex ->
                if (arrows && i > 0) Text("→", color = Look.Faint, modifier = Modifier.padding(top = 4.dp))
                ExerciseChip(ex)
            }
        }
    }
}

@Composable
private fun WhichExercise(exercise: ExerciseSpec, viewModel: SettingsViewModel) {
    Question(5, "Which exercise of a topic?", "exercise", fill = Look.Muted) {
        Text("Not part of the sets: it changes only which exercise you get, never what’s learned. Shown here for a topic of five exercises, A to E.", color = Look.Muted, fontSize = 14.sp)
        ChoiceRow("Random, never the last", "Any exercise of the topic, but not the one you just had.", exercise == ExerciseSpec.RANDOM_NOT_LAST) { viewModel.exercise(ExerciseSpec.RANDOM_NOT_LAST) }
        ChoiceRow("In course order", "One after another in the order of the course, round and round.", exercise == ExerciseSpec.IN_ORDER) { viewModel.exercise(ExerciseSpec.IN_ORDER) }
        ChoiceRow(
            "Missed ones first",
            "What you missed and haven’t got right since comes back first, the one missed longest ago first, but never in the round you missed it in. Otherwise in course order.",
            exercise == ExerciseSpec.MISSED_FIRST,
        ) { viewModel.exercise(ExerciseSpec.MISSED_FIRST) }
        when (exercise) {
            ExerciseSpec.RANDOM_NOT_LAST -> Example("Example: C → A → D → B → E → A → D …", "The same exercise can come twice before another one is seen.")
            ExerciseSpec.IN_ORDER -> Example("Example: A → B → C → D → E → A → B …", "Every exercise once before any comes again.")
            ExerciseSpec.MISSED_FIRST -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExerciseRow(
                    "Last round (it ended when the topic was learned):",
                    listOf(Ex("A", true), Ex("B", false), Ex("C", true), Ex("D", false), Ex("E", true), Ex("A", true), Ex("B", true)),
                    arrows = true,
                )
                ExerciseRow("This round: D comes first (missed, never right since); B was got right later, so it is not brought back.", listOf(Ex("D", back = true), Ex("E"), Ex("A"), Ex("B"), Ex("C")), arrows = true)
                ExerciseRow("Missing D again now: it waits for the next round, and this round goes on in order:", listOf(Ex("D", false), Ex("E"), Ex("A"), Ex("B")), arrows = true)
                Text("If every exercise was missed in the round, they are all brought back in course order, so there is always something to show.", color = Look.Muted, fontSize = 13.sp)
            }
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
        "Nothing that affects progress has changed. Questions 4 and 5 and the daily limit never recount progress."
    }
    Text(
        text,
        Modifier.fillMaxWidth().background(if (recounts) Look.WarnBg else Color(0xFFEFEBE1), shape).padding(horizontal = 18.dp, vertical = 14.dp),
        color = if (recounts) Color(0xFF4A3500) else Look.Muted,
        fontSize = 14.sp,
    )
}

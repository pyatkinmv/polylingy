package com.polylingy.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.polylingy.content.Exercise
import com.polylingy.content.Response
import com.polylingy.content.compose.ContentColors
import com.polylingy.content.compose.ContentTheme
import com.polylingy.content.compose.ExerciseView
import com.polylingy.content.emptyResponse
import com.polylingy.core.content.CourseJson
import com.polylingy.core.rules.Checking
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.time.Instant

// The multiple-choice exercises of the Arabic course, which mix Russian prompts with Arabic phrases and options:
// the prompt, the phrase and the options each keep their own line and read in their own direction, before and after Check.
class ArabicGreetingsUiTest {
    private val course = ZipFile(File("../examples/arabic-sample.zip")).use { zip ->
        CourseJson.read(zip.getInputStream(zip.getEntry("course.json")).readBytes().decodeToString(), Instant.parse("2026-11-01T10:00:00Z")).course
    }

    private fun show(name: String, exercise: Exercise.Choice, wrong: Boolean) = runDesktopComposeUiTest(width = 832, height = 820) {
        // Every place answered: the right option, or the last one listed (a wrong one) to see the right answer shown.
        val response = Response.Choice(exercise.places.map { if (wrong) it.options.last() else it.answer })
        val checks = Checking.check(exercise, response)
        setContent {
            MaterialTheme {
                ContentTheme(ContentColors.forAccent(null)) {
                    Column(Modifier.fillMaxSize().background(Color(0xFFFAF6EC)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        ExerciseView(exercise, response, checks, {}, onSubmit = {}, seed = 3)
                    }
                }
            }
        }
        screenshot(name)
    }

    @Test fun greetingExercisesKeepTheirLines() {
        val topic = course.units.single().topics.first { it.title == "Приветствия" }
        val choices = topic.exercises.filterIsInstance<Exercise.Choice>()
        for ((i, exercise) in choices.withIndex()) {
            show("arabic-greet-$i", exercise, wrong = false)
            show("arabic-greet-$i-wrong", exercise, wrong = true)
        }
    }

    @Test fun theOrderOfGreetingsShowsEachPlaceNextToItsRightPhrase() {
        val topic = course.units.single().topics.first { it.title == "Приветствия" }
        val exercise = topic.exercises.filterIsInstance<Exercise.Order>().first { it.items.single().parts.size == 6 }
        runDesktopComposeUiTest(width = 832, height = 760) {
            // The right order turned around, with the first two places swapped back: some places right, some wrong.
            val parts = exercise.items.single().parts
            val response = Response.Order(listOf(parts.reversed().toMutableList().also { it[0] = parts[0]; it[5] = parts[5] }))
            val checks = Checking.check(exercise, response)
            setContent {
                MaterialTheme {
                    ContentTheme(ContentColors.forAccent(null)) {
                        Column(Modifier.fillMaxSize().background(Color(0xFFFAF6EC)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            ExerciseView(exercise, response, checks, {}, onSubmit = {}, seed = 3)
                        }
                    }
                }
            }
            screenshot("arabic-greet-order-wrong")
        }
    }
}

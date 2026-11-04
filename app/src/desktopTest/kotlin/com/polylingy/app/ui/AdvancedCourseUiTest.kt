package com.polylingy.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import java.io.File
import kotlin.test.Test

// The example course examples/advanced-grammar.json as a learner sees it: each unit's theory and first exercise.
class AdvancedCourseUiTest {
    private val json = File("../examples/advanced-grammar.json").readText()

    @Test fun eachUnitShowsItsTheoryAndThenItsExercises() = appTest({ it.seedCourse(json) }, height = 1400) {
        click("Advanced Grammar: Units 1–3")
        text("Units").assertIsDisplayed()
        screenshot("advanced-course")
        for ((n, unit) in listOf("State verbs and performatives", "Using present continuous and present simple", "Past simple and present perfect").withIndex()) {
            click(unit)
            click("Continue →")
            text("Start exercises →").assertIsDisplayed()
            screenshot("advanced-unit${n + 1}-theory")
            click("Start exercises →")
            screenshot("advanced-unit${n + 1}-exercise")
            click("Advanced Grammar: Units 1–3")
        }
    }
}

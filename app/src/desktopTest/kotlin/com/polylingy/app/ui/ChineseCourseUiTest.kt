package com.polylingy.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import java.io.File
import kotlin.test.Test

// The example course examples/chinese-1.zip as a learner sees it: annotations, dialogues with sound, listening.
class ChineseCourseUiTest {
    /** The theory is long: "Start exercises →" is at its end. */
    private fun AppHarness.start() {
        test.onNodeWithText("Start exercises →").performScrollTo().performClick()
        test.waitForIdle()
    }

    private val zip = File("../examples/chinese-1.zip").readBytes()

    @Test fun lessonOneShowsItsTheoryAndExercises() = appTest({ it.library.import("chinese-1.zip", zip) }, height = 1400) {
        click("Китайский с нуля")
        text("Units").assertIsDisplayed()
        screenshot("chinese-course")
        click("你好")
        screenshot("chinese-unit1")
        click("Continue →")
        text("Start exercises →").assertExists()
        screenshot("chinese-unit1-theory")
        test.onNodeWithText("Грамматика · 2. Общий вопрос с частицей 吗").performScrollTo()
        screenshot("chinese-unit1-grammar")
        test.onNodeWithText("Фонетика урока").performScrollTo()
        screenshot("chinese-unit1-dialogues")
        start()
        screenshot("chinese-unit1-exercise")
        click("Китайский с нуля")
        click("你好")
        test.onNodeWithText("Иероглифы: левая и правая части").performScrollTo().performClick()
        test.waitForIdle()
        if (test.onAllNodesWithText("Start exercises →").fetchSemanticsNodes().isNotEmpty()) start()
        screenshot("chinese-characters")
        click("Китайский с нуля")
        click("你好")
        test.onNodeWithText("Диалоги урока").performScrollTo().performClick()
        test.waitForIdle()
        if (test.onAllNodesWithText("Start exercises →").fetchSemanticsNodes().isNotEmpty()) start()
        screenshot("chinese-dialogue-topic")
        click("Китайский с нуля")
        click("Фонетика 1")
        click("Continue →")
        screenshot("chinese-phonetics-theory")
        start()
        screenshot("chinese-phonetics-exercise")
    }
}

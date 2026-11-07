package com.polylingy.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import java.io.File
import kotlin.test.Test

// The example course examples/arabic-sample.zip as a learner sees it: right-to-left text, tracks, a video link.
class ArabicCourseUiTest {
    private val zip = File("../examples/arabic-sample.zip").readBytes()
    private val course = "Арабский: первые шаги"
    private val unit = "Первые шаги"

    private fun AppHarness.start() {
        test.onNodeWithText("Start exercises →").performScrollTo().performClick()
        test.waitForIdle()
    }

    @Test fun unitOneTheoryAndEveryTopic() = appTest({ it.library.import("arabic-sample.zip", zip) }, height = 1300) {
        click(course)
        screenshot("arabic-course")
        click(unit)
        screenshot("arabic-unit")
        click("Continue →")
        screenshot("arabic-theory-top")
        for ((i, section) in listOf("Буквы", "Огласовки", "Приветствия").withIndex()) {
            test.onNodeWithText(section).performScrollTo()
            screenshot("arabic-theory-${i + 1}")
        }
        for ((i, topic) in listOf("Буквы", "Огласовки", "Приветствия").withIndex()) {
            click(course)
            click(unit)
            test.onNodeWithText(topic).performScrollTo().performClick()
            test.waitForIdle()
            if (test.onAllNodesWithText("Start exercises →").fetchSemanticsNodes().isNotEmpty()) start()
            screenshot("arabic-topic-${i + 1}")
        }
    }
}

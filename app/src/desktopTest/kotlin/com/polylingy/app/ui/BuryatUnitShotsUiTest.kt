package com.polylingy.app.ui

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import java.io.File
import kotlin.test.Test

// Screenshots of one unit of the real Buryat course (build-notes/buryat/course2/buryat-course.zip) for a look at what a learner sees.
// Not part of the regular suite: it runs only with -Dburyat.unit="<unit title>".
class BuryatUnitShotsUiTest {
    private val unit = System.getProperty("burat.unit") ?: System.getenv("BURYAT_UNIT")
    private val sections = (System.getenv("BURYAT_SECTIONS") ?: "").split("|").filter { it.isNotBlank() }
    private val topics = (System.getenv("BURYAT_TOPICS") ?: "").split("|").filter { it.isNotBlank() }
    private val prefix = System.getenv("BURYAT_PREFIX") ?: "buryat"
    private val courseName = System.getenv("BURYAT_COURSE") ?: "Бурятский язык"

    private fun AppHarness.start() {
        test.onNodeWithText("Start exercises →").performScrollTo().performClick()
        test.waitForIdle()
    }

    @Test fun shots() {
        if (unit == null) return
        val zip = File(System.getenv("BURYAT_ZIP") ?: "../build-notes/buryat/course2/buryat-course.zip").readBytes()
        appTest({ it.library.import("buryat-course.zip", zip) }, height = 1500) {
            click(courseName)
            test.onNodeWithText(unit).performScrollTo().performClick()
            test.waitForIdle()
            screenshot("$prefix-unit")
            click("Continue →")
            screenshot("$prefix-theory-0")
            sections.forEachIndexed { i, title ->
                test.onAllNodesWithText(title)[0].performScrollTo()
                screenshot("$prefix-theory-${i + 1}")
            }
            start()
            screenshot("$prefix-topic-0")
            topics.forEachIndexed { i, title ->
                click(courseName)
                test.onNodeWithText(unit).performScrollTo().performClick()
                test.waitForIdle()
                test.onNodeWithText(title).performScrollTo().performClick()
                test.waitForIdle()
                if (test.onAllNodesWithText("Start exercises →").fetchSemanticsNodes().isNotEmpty()) start()
                screenshot("$prefix-topic-${i + 1}")
            }
        }
    }
}

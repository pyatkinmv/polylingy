package com.polylingy.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import kotlin.test.Test
import kotlin.test.assertTrue

// Long titles take the crumbs of the top bar, never the width of the button at its right.
class LongTitlesUiTest {
    private val course = """{ "formatVersion": 6, "name": "A course with a rather long title that goes on",
      "units": [ { "title": "A unit whose title is also long, long enough to need two lines", "theory": { "format": "blocks", "blocks": [ { "type": "text", "text": "Theory." } ] },
        "topics": [ { "title": "A topic named with a great many words so that the crumbs have no room left", "exercises": [
          { "type": "gaps", "instruction": "Fill in.", "items": [ { "text": "I {{am}} here." } ] } ] } ] } ] }"""

    @Test fun theTheoryButtonKeepsItsWidthNextToLongCrumbs() = appTest({ it.seedCourse(course) }, width = 900) {
        click("A course with a rather long title that goes on")
        click("A unit whose title is also long, long enough to need two lines")
        click("Continue →")
        click("Start exercises →")
        text("Theory").assertIsDisplayed()
        screenshot("long-titles-study")
        val width = text("Theory").getBoundsInRoot().let { it.right - it.left }
        assertTrue(width.value >= 40f, "the button's text got $width")
    }
}

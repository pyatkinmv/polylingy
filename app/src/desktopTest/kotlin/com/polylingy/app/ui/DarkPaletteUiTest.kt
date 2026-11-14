package com.polylingy.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import com.polylingy.app.AppContainer
import kotlin.test.Test

// A course in the dark test palette: the sheet of the course is dark, the app around it keeps its own colours; look at the screenshots.
class DarkPaletteUiTest {
    private val course = """{ "formatVersion": 9, "name": "Dark course",
        "rules": { "mastery": { "type": "consecutive", "learning": 1, "review": 1 }, "display": { "palette": "midnight" } },
        "units": [
          { "title": "Present continuous", "subtitle": "I am doing",
            "theory": { "format": "blocks", "blocks": [
              { "type": "section", "label": "A", "title": "Happening now", "blocks": [
                { "type": "text", "text": "He**’s cycling** to work, and ==root== with =={2}ending== and =={3}three==.", "role": "lead" },
                { "type": "situation", "frame": { "type": "newspaper", "headline": "City paper: it is raining", "source": "Tuesday" }, "blocks": [ { "type": "text", "text": "Dark paper, light letters." } ] },
                { "type": "situation", "frame": { "type": "email", "from": "Dari", "to": "Ayur", "subject": "Hello" }, "blocks": [ { "type": "text", "text": "I am writing to you." } ] },
                { "type": "tip", "kind": "rule", "blocks": [ { "type": "text", "text": "am / is / are + **-ing**." } ] },
                { "type": "tip", "kind": "fact", "title": "A fact", "blocks": [ { "type": "text", "text": "Some say it is the oldest form." } ] },
                { "type": "tip", "kind": "warning", "blocks": [ { "type": "text", "text": "Not with state verbs." } ] },
                { "type": "table", "head": ["Subject", "Form"], "rows": [ ["I", "am doing"], ["he", "is doing"], ["they", "are doing"] ],
                  "appearance": { "highlightedColumns": [0], "stripedRows": true, "headerRow": "highlighted" } } ] } ] },
            "topics": [ { "title": "Things happening now", "exercises": [ { "type": "gaps", "instruction": "Complete the sentences.",
              "items": [ { "text": "Listen! Somebody {{is singing}}.", "hint": "sing" }, { "text": "I {{am not reading}} it now.", "hint": "not / read" } ] } ] } ] } ] }"""

    @Test fun aCourseInTheDarkPalette() = appTest({ it.seedCourse(course) }, height = 1700) {
        click("Dark course")
        screenshot("dark-course")
        click("Present continuous")
        click("Continue →")
        text("Start exercises →").assertIsDisplayed()
        screenshot("dark-theory")
        click("Start exercises →")
        text("Complete the sentences.").assertIsDisplayed()
        fill(1, "is singing")
        fill(2, "do not read")
        click("Check")
        screenshot("dark-exercise-checked")
    }
}

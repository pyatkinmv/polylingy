package com.polylingy.app.ui

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test

/**
 * Screenshots of exact places of a course file, for before/after comparisons. Not part of the regular suite: runs only with
 * SHOTS_SPEC=<spec.json>: {"zip", "course", "unit", "height", "theory": [{"name","section","end"?,"start"?}], "topics": [{"name","topic"}]}.
 * A theory shot is the whole scrolled theory page in a tall window, plus <name>.json with the top and the bottom (px) of the place
 * (from the section's title to the title of "end", or from the text "start"); the caller crops. A topic shot is the screen of its first exercise.
 */
class ShotsUiTest {
    private val spec = System.getenv("SHOTS_SPEC")?.let { Json.parseToJsonElement(File(it).readText()).jsonObject }

    @Test fun shots() {
        val spec = spec ?: return
        val zip = File(spec["zip"]!!.jsonPrimitive.content).readBytes()
        val course = spec["course"]!!.jsonPrimitive.content
        val unit = spec["unit"]!!.jsonPrimitive.content
        val height = spec["height"]?.jsonPrimitive?.content?.toInt() ?: 1600
        val theory = spec["theory"]?.jsonArray ?: JsonArray(emptyList())
        val topics = spec["topics"]?.jsonArray ?: JsonArray(emptyList())
        appTest({ it.library.import("shots.zip", zip) }, width = spec["width"]?.jsonPrimitive?.content?.toInt() ?: 832, height = height) {
            if (theory.isNotEmpty()) {
                click(course)
                test.onNodeWithText(unit).performScrollTo().performClick()
                test.waitForIdle()
                click("Continue →")
                test.waitForIdle()
                for (entry in theory) {
                    val e = entry.jsonObject
                    val name = e["name"]!!.jsonPrimitive.content
                    fun top(title: String, part: Boolean = false) = test.onAllNodesWithText(title, substring = part)[0].fetchSemanticsNode().boundsInRoot.top
                    val y0 = e["start"]?.jsonPrimitive?.content?.let { top(it, true) } ?: top(e["section"]!!.jsonPrimitive.content)
                    val y1 = e["end"]?.jsonPrimitive?.content?.let { t -> test.onAllNodesWithText(t).let { n -> n[n.fetchSemanticsNodes().size - 1] }.fetchSemanticsNode().boundsInRoot.top } ?: -1f
                    screenshot(name)
                    File(System.getProperty("polylingy.screenshots") ?: ".", "$name.json").writeText("""{"y0": $y0, "y1": $y1}""")
                }
            }
            for (entry in spec["unitpages"]?.jsonArray ?: JsonArray(emptyList())) {
                val name = entry.jsonObject["name"]!!.jsonPrimitive.content
                if (test.onAllNodesWithText("Courses").fetchSemanticsNodes().isNotEmpty()) click("Courses")
                click(course)
                test.onNodeWithText(unit).performScrollTo().performClick()
                test.waitForIdle()
                screenshot(name)
            }
            for (entry in topics) {
                val e = entry.jsonObject
                val name = e["name"]!!.jsonPrimitive.content
                if (test.onAllNodesWithText("Courses").fetchSemanticsNodes().isNotEmpty()) click("Courses")
                click(course)
                test.onNodeWithText(unit).performScrollTo().performClick()
                test.waitForIdle()
                test.onNodeWithText(e["topic"]!!.jsonPrimitive.content).performScrollTo().performClick()
                test.waitForIdle()
                if (test.onAllNodesWithText("Start exercises →").fetchSemanticsNodes().isNotEmpty()) click("Start exercises →")
                screenshot(name)
            }
        }
    }
}

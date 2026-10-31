package com.polylingy.app.ui

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.polylingy.app.App
import com.polylingy.app.AppContainer
import com.polylingy.app.CourseFiles
import com.polylingy.app.MovableClock
import com.polylingy.client.CourseFile
import com.polylingy.client.DesktopStorage
import com.polylingy.client.seedSampleCourse
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.random.Random

/** File dialogs replaced by a fixed answer; remembers what the app tried to save. */
class FakeFiles(var toOpen: String? = null) : CourseFiles {
    var saved: Pair<String, String>? = null
    override fun open() = toOpen
    override fun save(suggestedName: String, content: String): Boolean {
        saved = suggestedName to content
        return true
    }
}

fun AppContainer.seedSample() = seedSampleCourse(storage, clock, DesktopStorage.sampleCourseJson())

fun AppContainer.seedCourse(json: String) =
    courseService.import(CourseFile.parseCourse(json, clock.now().instant, clock.now().zone))

class AppHarness(val test: ComposeUiTest, val container: AppContainer, val clock: MovableClock, val files: FakeFiles)

/**
 * Runs the real [App] headless in a window-sized scene, on an in-memory database with a
 * movable clock. [seed] prepares data before the first frame.
 */
fun appTest(seed: (AppContainer) -> Unit = {}, block: AppHarness.() -> Unit) =
    runDesktopComposeUiTest(width = 832, height = 720) {
        val clock = MovableClock()
        val files = FakeFiles()
        DesktopStorage.inMemory().use { storage ->
            val container = AppContainer(storage, files, clock, Random(7))
            seed(container)
            setContent { App(container) }
            AppHarness(this, container, clock, files).block()
        }
    }

fun AppHarness.text(text: String): SemanticsNodeInteraction = test.onNodeWithText(text)

fun AppHarness.click(text: String) {
    test.onNodeWithText(text).performClick()
    test.waitForIdle()
}

/** Opens the n-th "⋮" overflow menu on screen and picks [item]. */
fun AppHarness.menu(item: String, index: Int = 0) {
    test.onAllNodesWithText("⋮")[index].performClick()
    click(item)
}

/**
 * Saves the whole scene — dialogs and menus included — as a PNG under app/build/screenshots,
 * so a person (or Claude) can look at the layout.
 */
fun AppHarness.screenshot(name: String) {
    val dir = System.getProperty("polylingy.screenshots") ?: return
    test.waitForIdle()
    val roots = test.onAllNodes(isRoot()).fetchSemanticsNodes().indices.map { test.onAllNodes(isRoot())[it] }
    val images = roots.map { it.captureToImage().toAwtImage() }
    val base = images.first()
    val composed = BufferedImage(base.width, base.height, BufferedImage.TYPE_INT_ARGB)
    composed.createGraphics().apply {
        images.forEachIndexed { i, image ->
            val bounds = test.onAllNodes(isRoot())[i].fetchSemanticsNode().boundsInWindow
            drawImage(image, bounds.left.toInt(), bounds.top.toInt(), null)
        }
        dispose()
    }
    ImageIO.write(composed, "png", File(dir, "$name.png").apply { parentFile.mkdirs() })
}

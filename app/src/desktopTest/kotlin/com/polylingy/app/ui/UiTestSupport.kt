package com.polylingy.app.ui

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.polylingy.app.App
import com.polylingy.app.AppContainer
import com.polylingy.app.CourseFiles
import com.polylingy.app.MovableClock
import com.polylingy.app.PickedFile
import com.polylingy.app.testContainer
import com.polylingy.client.CourseSource
import com.polylingy.client.DesktopStorage
import com.polylingy.client.LocalStorage
import com.polylingy.client.sampleCourse
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** File dialogs replaced by a fixed answer; remembers what the app tried to save. */
class FakeFiles(var toOpen: PickedFile? = null) : CourseFiles {
    var saved: Pair<String, ByteArray>? = null
    override fun open() = toOpen
    override fun save(suggestedName: String, bytes: ByteArray): Boolean {
        saved = suggestedName to bytes
        return true
    }

    fun open(name: String, json: String) {
        toOpen = PickedFile(name, json.toByteArray())
    }
}

fun AppContainer.seedSample() = library.seedSample(sampleCourse())

fun AppContainer.seedCourse(json: String) = library.import(CourseSource("seed.json", json)).course

class AppHarness(val test: ComposeUiTest, val container: AppContainer, val clock: MovableClock, val files: FakeFiles)

/**
 * Runs the real [App] headless in a window-sized scene, on an in-memory database with a
 * movable clock. [seed] prepares data before the first frame.
 */
fun appTest(
    seed: (AppContainer) -> Unit = {},
    storage: () -> LocalStorage = DesktopStorage::inMemory,
    width: Int = 832,
    height: Int = 720,
    block: AppHarness.() -> Unit,
) =
    runDesktopComposeUiTest(width = width, height = height) {
        val clock = MovableClock()
        val files = FakeFiles()
        storage().use { storage ->
            val container = testContainer(clock, files, storage)
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

/** Opens the n-th "⋯" menu on screen and picks [item]. */
fun AppHarness.menu(item: String, index: Int = 0) {
    openMenu(index)
    click(item)
}

fun AppHarness.openMenu(index: Int = 0) {
    test.onAllNodesWithText("⋯")[index].performClick()
    test.waitForIdle()
}

/** Types into the n-th blank of the exercise on screen. */
fun AppHarness.fill(blank: Int, text: String) {
    test.onNode(hasContentDescription("Blank $blank")).performTextInput(text)
    test.waitForIdle()
}

/**
 * Saves the whole scene — dialogs and menus included — as a PNG under app/build/screenshots,
 * so a person (or Claude) can look at the layout.
 */
fun AppHarness.screenshot(name: String) = test.screenshot(name)

/** Same for any Compose UI test. */
fun ComposeUiTest.screenshot(name: String) {
    val dir = System.getProperty("polylingy.screenshots") ?: return
    val test = this
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

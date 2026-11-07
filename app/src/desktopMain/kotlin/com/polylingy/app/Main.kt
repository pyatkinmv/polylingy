package com.polylingy.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.polylingy.client.CourseArchive
import com.polylingy.client.DesktopStorage
import com.polylingy.client.FileImageStore
import com.polylingy.client.HttpImageFetcher
import com.polylingy.client.sampleCourse
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.skia.Image
import java.awt.Dimension

private val AppIcon = BitmapPainter(
    Image.makeFromEncoded(requireNotNull(Thread.currentThread().contextClassLoader.getResource("polylingy.png")).readBytes())
        .toComposeImageBitmap(),
)

/** Decodes PNG, JPEG, GIF, WebP and the rest that Skia knows. */
val SkiaImageDecoder = ImageDecoder { Image.makeFromEncoded(it).toComposeImageBitmap() }

/** The smallest window, in the same units as dp at 100 % scale. */
private const val MIN_WIDTH = 900
private const val MIN_HEIGHT = 640

fun main() {
    val dataDir = DesktopStorage.defaultDataDir()
    // Read by logback.xml; must be set before the first logger is created.
    System.setProperty("polylingy.logs", dataDir.resolve("logs").toString())
    val log = KotlinLogging.logger {}
    log.info { "Polylingy $APP_VERSION starting, data in $dataDir" }
    Thread.setDefaultUncaughtExceptionHandler { thread, e -> log.error(e) { "Uncaught exception in thread ${thread.name}" } }

    val storage = DesktopStorage.open(dataDir)
    val images = FileImageStore(dataDir.resolve("images"))
    val fetcher = HttpImageFetcher()

    application {
        Window(
            onCloseRequest = {
                log.info { "Polylingy closing" }
                storage.close()
                exitApplication()
            },
            title = "Polylingy",
            icon = AppIcon,
            state = rememberWindowState(width = 1000.dp, height = 720.dp),
        ) {
            // Below this the cards and their progress no longer fit side by side. AWT applies the minimum in physical
            // pixels, ignoring the display scale (at 175 % a 900-wide minimum let the window shrink to 514), so scale it.
            LaunchedEffect(Unit) {
                val scale = window.graphicsConfiguration.defaultTransform.scaleX
                window.minimumSize = Dimension((MIN_WIDTH * scale).toInt(), (MIN_HEIGHT * scale).toInt())
                log.info { "Window minimum size $MIN_WIDTH×$MIN_HEIGHT at scale $scale" }
            }
            val container = remember {
                DesktopSound.warmUp()
                AppContainer(storage, DesktopCourseFiles(window), images, fetcher, CourseArchive, SkiaImageDecoder, sound = DesktopSound).also {
                    it.library.seedSample(sampleCourse())
                }
            }
            App(container, DesktopText)
        }
    }
}

package com.polylingy.app

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
            state = rememberWindowState(width = 832.dp, height = 720.dp),
        ) {
            window.minimumSize = Dimension(480, 400)
            val container = remember {
                AppContainer(storage, DesktopCourseFiles(window), images, fetcher, CourseArchive).also {
                    it.library.seedSample(sampleCourse())
                }
            }
            App(container)
        }
    }
}

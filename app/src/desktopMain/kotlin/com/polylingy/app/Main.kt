package com.polylingy.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.polylingy.client.BundledCourse
import com.polylingy.client.CourseArchive
import com.polylingy.client.DbBackup
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
    log.info { "Polylingy $APP_VERSION_LABEL starting, data in $dataDir" }
    Thread.setDefaultUncaughtExceptionHandler { thread, e ->
        log.error(e) { "Uncaught exception in thread ${thread.name}" }
        ErrorReport.report(e) // the window tells the user; the details are in the log
    }

    DbBackup.run(dataDir, mirror = DesktopStorage.backupMirror()) // before the database is opened and migrated
    // Without a database there is nothing to show a dialog in: say so in a plain system dialog and point at the backups.
    val storage = try {
        DesktopStorage.open(dataDir)
    } catch (e: Throwable) {
        log.error(e) { "The database in $dataDir couldn't be opened" }
        javax.swing.JOptionPane.showMessageDialog(
            null,
            "Polylingy can't open its data in\n$dataDir\n\n${e.message?.lineSequence()?.firstOrNull()?.take(200).orEmpty()}\n\n" +
                "Nothing was changed. Copies of the database are in the \"backups\" folder there: close this, copy the newest one over polylingy.db and start again.\n" +
                "The details are in logs\\polylingy.log.",
            "Polylingy",
            javax.swing.JOptionPane.ERROR_MESSAGE,
        )
        kotlin.system.exitProcess(1)
    }
    val images = FileImageStore(dataDir.resolve("images"))
    val fetcher = HttpImageFetcher()

    val saved = storage.setting(WINDOW_SETTING)?.let(Placement::parse)

    application {
        val windowState = rememberWindowState(
            placement = if (saved?.maximized == true) WindowPlacement.Maximized else WindowPlacement.Floating,
            position = saved?.let { WindowPosition(it.x.dp, it.y.dp) } ?: WindowPosition.PlatformDefault,
            size = saved?.let { DpSize(it.width.dp, it.height.dp) } ?: DpSize(1000.dp, 720.dp),
        )
        Window(
            onCloseRequest = {
                log.info { "Polylingy closing" }
                Placement.of(windowState)?.let { storage.saveSetting(WINDOW_SETTING, it.toString()) }
                storage.close()
                exitApplication()
            },
            title = "Polylingy",
            icon = AppIcon,
            state = windowState,
        ) {
            // Below this the cards and their progress no longer fit side by side. AWT applies the minimum in physical
            // pixels, ignoring the display scale (at 175 % a 900-wide minimum let the window shrink to 514), so scale it.
            // A window saved on a screen that is gone (another monitor unplugged) would open where nobody can see it.
            LaunchedEffect(Unit) {
                if (!onSomeScreen(window.bounds)) {
                    log.info { "The saved window position ${window.bounds} is on no screen; centring the window" }
                    window.setLocationRelativeTo(null)
                }
            }
            LaunchedEffect(Unit) {
                val scale = window.graphicsConfiguration.defaultTransform.scaleX
                window.minimumSize = Dimension((MIN_WIDTH * scale).toInt(), (MIN_HEIGHT * scale).toInt())
                log.info { "Window minimum size $MIN_WIDTH×$MIN_HEIGHT at scale $scale" }
            }
            val container = remember {
                DesktopSound.warmUp()
                AppContainer(storage, DesktopCourseFiles(window), images, fetcher, CourseArchive, SkiaImageDecoder, sound = DesktopSound).also {
                    // The installed app starts with its own course; a run from the sources with the sample one.
                    val bundled = if (DesktopStorage.isInstalled() || System.getProperty("polylingy.bundled") != null) BundledCourse.find() else null
                    if (bundled != null) it.library.seedBundled(bundled.id, bundled.version, bundled::source)
                    else it.library.seedSample(sampleCourse())
                }
            }
            App(container, DesktopText, onOpenLogs = { openFolder(dataDir.resolve("logs")) })
        }
    }
}

private const val WINDOW_SETTING = "window"

/** Where the window was when the app was closed, in dp: `x,y,width,height,maximized`. */
private data class Placement(val x: Int, val y: Int, val width: Int, val height: Int, val maximized: Boolean) {
    override fun toString() = "$x,$y,$width,$height,${if (maximized) 1 else 0}"

    companion object {
        fun parse(text: String): Placement? = runCatching {
            val p = text.split(',')
            Placement(p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toInt(), p[4] == "1").takeIf { it.width >= MIN_WIDTH && it.height >= MIN_HEIGHT }
        }.getOrNull()

        fun of(state: WindowState): Placement? {
            if (state.isMinimized) return null
            val position = state.position
            val x = if (position.isSpecified) position.x.value.toInt() else return null
            return Placement(x, position.y.value.toInt(), state.size.width.value.toInt(), state.size.height.value.toInt(), state.placement == WindowPlacement.Maximized)
        }
    }
}

/** True when at least a corner of the window is on one of the screens, enough to be found and dragged. */
private fun onSomeScreen(bounds: java.awt.Rectangle): Boolean {
    val grab = java.awt.Rectangle(bounds.x, bounds.y, minOf(bounds.width, 200), minOf(bounds.height, 60))
    return java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.any { it.defaultConfiguration.bounds.intersects(grab) }
}

private fun openFolder(folder: java.nio.file.Path) {
    runCatching { java.awt.Desktop.getDesktop().open(folder.toFile()) }
        .onFailure { KotlinLogging.logger {}.warn(it) { "Couldn't open $folder" } }
}

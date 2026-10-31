package com.polylingy.app

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.polylingy.client.DesktopStorage
import com.polylingy.client.seedSampleCourse
import java.awt.Dimension

fun main() {
    val storage = DesktopStorage.open()
    seedSampleCourse(storage, SystemClock, DesktopStorage.sampleCourseJson())

    application {
        Window(
            onCloseRequest = {
                storage.close()
                exitApplication()
            },
            title = "Polylingy",
            state = rememberWindowState(width = 832.dp, height = 720.dp),
        ) {
            window.minimumSize = Dimension(480, 400)
            val container = remember { AppContainer(storage, DesktopCourseFiles(window)) }
            App(container)
        }
    }
}

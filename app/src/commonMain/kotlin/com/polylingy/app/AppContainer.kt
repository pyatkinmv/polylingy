package com.polylingy.app

import com.polylingy.client.CourseArchives
import com.polylingy.client.CourseLibrary
import com.polylingy.client.ImageFetcher
import com.polylingy.client.ImageStore
import com.polylingy.client.LocalStorage
import com.polylingy.core.content.CourseService
import com.polylingy.core.journal.ZonedInstant
import com.polylingy.core.ports.Clock
import kotlinx.datetime.TimeZone
import kotlin.random.Random

/** A file the user picked in a file dialog. */
class PickedFile(val name: String, val bytes: ByteArray)

/** Opening and saving course files; each platform shows its own file dialogs. */
interface CourseFiles {
    /** Lets the user pick a course file (`.json` or `.zip`), or returns null if cancelled. */
    fun open(): PickedFile?

    /** Lets the user choose where to save [bytes]; returns false if cancelled. */
    fun save(suggestedName: String, bytes: ByteArray): Boolean
}

val SystemClock = Clock { ZonedInstant(kotlin.time.Clock.System.now(), TimeZone.currentSystemDefault()) }

/** Builds every long-lived object once; screens get what they need from here. */
class AppContainer(
    val storage: LocalStorage,
    val files: CourseFiles,
    images: ImageStore,
    fetcher: ImageFetcher,
    archives: CourseArchives,
    val clock: Clock = SystemClock,
    /** Picks topics and exercises; fixed in tests for repeatable runs. */
    val random: Random = Random.Default,
) {
    val courseService = CourseService(storage.courses, clock)
    val library = CourseLibrary(storage.courses, courseService, images, fetcher, archives, clock)
}

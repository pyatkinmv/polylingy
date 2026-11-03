package com.polylingy.app

import androidx.compose.ui.graphics.ImageBitmap
import com.polylingy.client.CourseArchives
import com.polylingy.client.CourseLibrary
import com.polylingy.client.ImageFetcher
import com.polylingy.client.ImageStore
import com.polylingy.client.LocalStorage
import com.polylingy.content.compose.ImageLoader
import com.polylingy.core.content.CourseService
import com.polylingy.core.journal.ZonedInstant
import com.polylingy.core.ports.Clock
import com.polylingy.core.study.StudyService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.TimeZone
import kotlin.random.Random
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

/** A file the user picked in a file dialog. */
class PickedFile(val name: String, val bytes: ByteArray)

/** Opening and saving course files; each platform shows its own file dialogs. */
interface CourseFiles {
    /** Lets the user pick a course file (`.json` or `.zip`), or returns null if cancelled. */
    fun open(): PickedFile?

    /** Lets the user choose where to save [bytes]; returns false if cancelled. */
    fun save(suggestedName: String, bytes: ByteArray): Boolean
}

/** Turns stored picture bytes into an image; each platform decodes its own way. Null when the bytes aren't a picture. */
fun interface ImageDecoder {
    fun decode(bytes: ByteArray): ImageBitmap?
}

val SystemClock = Clock { ZonedInstant(kotlin.time.Clock.System.now(), TimeZone.currentSystemDefault()) }

/** Builds every long-lived object once; screens get what they need from here. */
class AppContainer(
    val storage: LocalStorage,
    val files: CourseFiles,
    images: ImageStore,
    fetcher: ImageFetcher,
    archives: CourseArchives,
    private val decoder: ImageDecoder,
    val clock: Clock = SystemClock,
    /** Picks topics and exercises; fixed in tests for repeatable runs. */
    val random: Random = Random.Default,
) {
    val courseService = CourseService(storage.courses, storage.courseSettings, clock)
    val library = CourseLibrary(storage.courses, courseService, images, fetcher, archives, clock)
    val study = StudyService(storage.courses, storage.courseSettings, storage.journal, clock, storage.deviceId, random)

    private val decoded = mutableMapOf<Pair<Uuid, String>, ImageBitmap?>()

    /** Pictures of one course for the content views; each is decoded once and kept. */
    fun images(courseId: Uuid) = ImageLoader { src ->
        decoded.getOrPut(courseId to src) {
            library.image(courseId, src)?.let { bytes ->
                runCatching { decoder.decode(bytes) }.onFailure { log.warn(it) { "Picture $src of course $courseId can't be decoded" } }.getOrNull()
            }
        }
    }
}

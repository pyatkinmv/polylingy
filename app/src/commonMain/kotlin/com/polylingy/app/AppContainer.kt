package com.polylingy.app

import com.polylingy.core.content.DisplaySpec
import com.polylingy.content.compose.Palettes
import com.polylingy.content.compose.ContentColors
import com.polylingy.content.CourseStyle
import androidx.compose.ui.graphics.ImageBitmap
import com.polylingy.client.CourseArchives
import com.polylingy.client.CourseLibrary
import com.polylingy.client.ImageFetcher
import com.polylingy.client.ImageStore
import com.polylingy.client.LocalStorage
import com.polylingy.content.compose.AudioPlayer
import com.polylingy.content.compose.DockTitle
import com.polylingy.content.compose.Playback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

/** What a [SoundPlayer] is doing: the [key] of the sound it was given, whether it plays, and where it is, in seconds. */
data class SoundState(val key: String? = null, val playing: Boolean = false, val position: Float = 0f, val duration: Float = 0f) {
    companion object {
        val Silent: StateFlow<SoundState> = MutableStateFlow(SoundState())
    }
}

/**
 * Plays a stored sound (.mp3 or .wav bytes); each platform plays its own way. A new sound stops the one playing.
 * Only [play] is needed to hear sounds; [state] and the controls drive the docked player of long recordings.
 */
fun interface SoundPlayer {
    fun play(bytes: ByteArray)

    /** Plays [bytes], which [state] then reports under [key]. */
    fun play(key: String, bytes: ByteArray) = play(bytes)

    val state: StateFlow<SoundState> get() = SoundState.Silent
    fun pause() {}
    fun resume() {}
    fun seekTo(seconds: Float) {}
    fun stop() {}
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
    private val sound: SoundPlayer = SoundPlayer {},
) {
    val courseService = CourseService(storage.courses, storage.courseSettings, clock)
    val library = CourseLibrary(storage.courses, courseService, images, fetcher, archives, clock)
    val study = StudyService(storage.courses, storage.courseSettings, storage.journal, clock, storage.deviceId, random)

    private val players = mutableMapOf<Uuid, AudioPlayer>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /** Sounds of one course for the content views; a missing one is logged and stays silent. One player per course. */
    fun audio(courseId: Uuid): AudioPlayer = players.getOrPut(courseId) {
        val prefix = "$courseId|"
        // What the content sees is only the sounds of its own course: a recording left playing in another one is not its.
        val flow = sound.state.map { s ->
            val src = s.key?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)
            if (src == null) Playback.Idle else Playback(src, s.playing, s.position, s.duration)
        }.stateIn(scope, SharingStarted.Eagerly, Playback.Idle)
        object : AudioPlayer {
            override fun play(src: String) {
                val bytes = library.image(courseId, src)
                if (bytes == null) log.warn { "Sound $src of course $courseId is missing" }
                else runCatching { sound.play(prefix + src, bytes) }.onFailure { log.warn(it) { "Sound $src of course $courseId can't be played" } }
            }

            override val playback get() = flow
            override fun pause() = sound.pause()
            override fun resume() = sound.resume()
            override fun seekTo(seconds: Float) = sound.seekTo(seconds)
            override fun stop() = sound.stop()
        }
    }

    /** The title of the sound the docked player shows; the buttons that start a sound set it. One for the whole app. */
    val dockTitle = DockTitle()

    /**
     * The sound that is playing now, whichever course started it, for the docked player that stays on every screen:
     * a recording goes on when the learner leaves the page that started it, and its player must go on too.
     */
    val nowPlaying: AudioPlayer = object : AudioPlayer {
        private val flow = sound.state.map { s -> if (s.key == null) Playback.Idle else Playback(s.key, s.playing, s.position, s.duration) }
            .stateIn(scope, SharingStarted.Eagerly, Playback.Idle)

        override fun play(src: String) {}
        override val playback get() = flow
        override fun pause() = sound.pause()
        override fun resume() = sound.resume()
        override fun seekTo(seconds: Float) = sound.seekTo(seconds)
        override fun stop() = sound.stop()
    }

    /** The palette and the style a course is drawn in: its "How the course looks" settings (the default palette when none is chosen). */
    fun look(courseId: Uuid): CourseLook {
        val display = study.settings(courseId)?.resolved()?.display ?: DisplaySpec()
        return CourseLook(Palettes.byId(display.palette).colors, display.style)
    }

    /** Whether the course's annotations over words show: its "Show annotations over text" setting. */
    fun annotations(courseId: Uuid): Boolean = study.settings(courseId)?.resolved()?.display?.annotations ?: true

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

/** The colours and the style content is drawn in, for one course. */
data class CourseLook(val colors: ContentColors, val style: CourseStyle)

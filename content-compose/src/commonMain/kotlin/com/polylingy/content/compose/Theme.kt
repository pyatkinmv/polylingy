package com.polylingy.content.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.polylingy.content.Block
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * How content looks. Courses may set only [accent]; everything else belongs to the app, so a course can't
 * break the layout or the meaning of the check colours.
 */
@Immutable
data class ContentColors(
    val accent: Color,
    val onAccent: Color = Color.White,
    val accentSoft: Color = accent.copy(alpha = 0.12f),
    val ink: Color = Color(0xFF1F1D1A),
    val muted: Color = Color(0xFF6B6458),
    val line: Color = Color(0xFFE6DFD0),
    val card: Color = Color.White,
    val panel: Color = Color(0xFFF3EEE2),
    val correct: Color = Color(0xFF2F7A4F),
    val correctBg: Color = Color(0xFFE4F0E7),
    val typo: Color = Color(0xFF7A5200),
    val typoBg: Color = Color(0xFFF6E9C8),
    val wrong: Color = Color(0xFFB3261E),
    val wrongBg: Color = Color(0xFFF7E1DD),
) {
    companion object {
        /** The soft green of the app ("Notebook" look); a course without its own accent uses it. */
        val DefaultAccent = Color(0xFF2F7A4F)

        /** The theme for a course: its accent (`#2F6B5E`) if it sets a valid one, the app's otherwise. */
        fun forAccent(hex: String?): ContentColors = ContentColors(parseHex(hex) ?: DefaultAccent)

        private fun parseHex(hex: String?): Color? {
            if (hex == null || !Regex("#[0-9A-Fa-f]{6}").matches(hex)) return null
            return Color(0xFF000000 or hex.drop(1).toLong(16))
        }
    }
}

/** Gives the content views a picture for a `src`; the app decides where pictures come from. Null shows the picture's description. */
fun interface ImageLoader {
    fun load(src: String): ImageBitmap?
}

/**
 * Gives a `seeAlso` link the words it shows, e.g. "B · Started, not finished"; only the app knows the course the link points into.
 * Null falls back to a generic "Theory".
 */
fun interface LinkNames {
    fun name(link: Block.Link): String?
}

/** What the player is doing now: which sound ([src], null when none), whether it is playing, where it is and how long it is. */
data class Playback(
    val src: String? = null,
    val playing: Boolean = false,
    val positionSeconds: Float = 0f,
    val durationSeconds: Float = 0f,
) {
    /** Whether the docked player shows this sound: a long one, which is worth pausing and rewinding. */
    val docked: Boolean get() = src != null && durationSeconds >= DockMinSeconds

    companion object {
        /** A sound shorter than this is a word or a phrase: no dock, a press of its button just plays it again. */
        const val DockMinSeconds = 6f
        val Idle = Playback()
    }
}

/**
 * Plays a course's sound by its `src`; the app decides where sounds come from and how they play. A new sound stops the one
 * playing. Only [play] is needed for sounds to be heard; the rest — [playback] and the controls — drive the docked player
 * of long recordings and do nothing by default.
 */
fun interface AudioPlayer {
    fun play(src: String)

    val playback: StateFlow<Playback> get() = IdlePlayback
    fun pause() {}
    fun resume() {}
    fun seekTo(seconds: Float) {}
    fun stop() {}
}

private val IdlePlayback: StateFlow<Playback> = MutableStateFlow(Playback.Idle)

/** The title the docked player shows: set by the button that started the sound. One per app, so it outlives the screen. */
class DockTitle {
    var text: String? by mutableStateOf(null)
}

internal val LocalContentColors = staticCompositionLocalOf { ContentColors(ContentColors.DefaultAccent) }
internal val LocalImageLoader = staticCompositionLocalOf { ImageLoader { null } }
internal val LocalLinkNames = staticCompositionLocalOf { LinkNames { null } }
internal val LocalAudioPlayer = staticCompositionLocalOf { AudioPlayer {} }
internal val LocalShowAnnotations = staticCompositionLocalOf { true }
internal val LocalDockTitle = staticCompositionLocalOf { DockTitle() }

/**
 * Provides colours, pictures, sounds and link names to the content views inside, and whether annotations over words
 * show ([annotations], a course setting).
 */
@Composable
fun ContentTheme(
    colors: ContentColors,
    images: ImageLoader = ImageLoader { null },
    links: LinkNames = LinkNames { null },
    audio: AudioPlayer = LocalAudioPlayer.current,
    annotations: Boolean = LocalShowAnnotations.current,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalContentColors provides colors,
        LocalImageLoader provides images,
        LocalLinkNames provides links,
        LocalAudioPlayer provides audio,
        LocalShowAnnotations provides annotations,
        content = content,
    )
}

/**
 * Sounds and annotations for the content inside, for one course; [ContentTheme] inside keeps them unless it is given its own.
 * The docked player of a long recording is not here: it belongs to the app (see [PlayerDock]), so it stays when the screen changes.
 */
@Composable
fun CourseContent(audio: AudioPlayer, annotations: Boolean, dockTitle: DockTitle = remember { DockTitle() }, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAudioPlayer provides audio, LocalShowAnnotations provides annotations, LocalDockTitle provides dockTitle) {
        content()
    }
}

internal val colors: ContentColors @Composable get() = LocalContentColors.current

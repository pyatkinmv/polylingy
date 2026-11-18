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
import com.polylingy.content.CourseStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The two paints of a mark (`==text==`): [fill] under the text of the soft style, [strong] for the text and its line in the strict style. */
@Immutable
data class MarkColor(val fill: Color, val strong: Color)

/**
 * How content looks: the colours of one [Palette], each for its role. Courses never carry colours, they name a palette, so a course can't
 * break the layout or the meaning of the check colours (those do not change with the palette).
 * [background] is the page, [card] a card on it, [line] the edges and thin lines, [separator] the stronger line between parts
 * (under a header, beside a highlighted column); the fills: [accentSoft] of a remark, [head] of a highlighted header, [column] of a
 * highlighted column, [stripe] of a striped row; [marks] are the five marks of a text highlight.
 */
@Immutable
data class ContentColors(
    val accent: Color,
    val accentSoft: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val card: Color,
    val panel: Color,
    val background: Color,
    val head: Color,
    val column: Color,
    val stripe: Color,
    val separator: Color,
    val typo: Color,
    val typoBg: Color,
    val info: Color,
    val infoBg: Color,
    val culture: Color,
    val cultureBg: Color,
    val marks: List<MarkColor>,
    val onAccent: Color = Color.White,
    val correct: Color = Color(0xFF2F7A4F),
    val correctBg: Color = Color(0xFFE4F0E7),
    val wrong: Color = Color(0xFFB3261E),
    val wrongBg: Color = Color(0xFFF7E1DD),
) {
    companion object {
        /** The colours of the default palette. */
        val Default: ContentColors get() = Palettes.default.colors

        /** The default palette with the accent of a course's old `theme.accent` (`#2F6B5E`), if it is a valid one. */
        fun forAccent(hex: String?): ContentColors {
            val accent = parseHex(hex) ?: return Default
            return Default.copy(accent = accent, accentSoft = accent.copy(alpha = 0.12f))
        }

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

internal val LocalContentColors = staticCompositionLocalOf { ContentColors.Default }
internal val LocalCourseStyle = staticCompositionLocalOf { CourseStyle.STRICT }
internal val LocalImageLoader = staticCompositionLocalOf { ImageLoader { null } }
internal val LocalLinkNames = staticCompositionLocalOf { LinkNames { null } }
internal val LocalAudioPlayer = staticCompositionLocalOf { AudioPlayer {} }
internal val LocalShowAnnotations = staticCompositionLocalOf { true }
internal val LocalDockTitle = staticCompositionLocalOf { DockTitle() }
internal val LocalExtraKeys = staticCompositionLocalOf { emptyList<String>() }

/**
 * Provides colours, pictures, sounds and link names to the content views inside, and whether annotations over words
 * show ([annotations], a course setting). [extraKeys] are the keys the course adds to a text field because the keyboard has none
 * for them (the course's `extraKeys`); the field shows them while it has focus.
 */
@Composable
fun ContentTheme(
    colors: ContentColors? = null,
    images: ImageLoader = ImageLoader { null },
    links: LinkNames = LinkNames { null },
    audio: AudioPlayer = LocalAudioPlayer.current,
    annotations: Boolean = LocalShowAnnotations.current,
    extraKeys: List<String> = LocalExtraKeys.current,
    style: CourseStyle? = null,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalExtraKeys provides extraKeys,
        LocalContentColors provides (colors ?: LocalContentColors.current),
        LocalCourseStyle provides (style ?: LocalCourseStyle.current),
        LocalImageLoader provides images,
        LocalLinkNames provides links,
        LocalAudioPlayer provides audio,
        LocalShowAnnotations provides annotations,
        content = content,
    )
}

/**
 * Sounds, annotations, palette ([colors]) and [style] for the content inside, for one course; [ContentTheme] inside keeps them unless it is given its own.
 * The docked player of a long recording is not here: it belongs to the app (see [PlayerDock]), so it stays when the screen changes.
 */
@Composable
fun CourseContent(
    audio: AudioPlayer,
    annotations: Boolean,
    dockTitle: DockTitle = remember { DockTitle() },
    colors: ContentColors = ContentColors.Default,
    style: CourseStyle = CourseStyle.STRICT,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAudioPlayer provides audio, LocalShowAnnotations provides annotations, LocalDockTitle provides dockTitle,
        LocalContentColors provides colors, LocalCourseStyle provides style,
    ) {
        content()
    }
}

internal val colors: ContentColors @Composable get() = LocalContentColors.current

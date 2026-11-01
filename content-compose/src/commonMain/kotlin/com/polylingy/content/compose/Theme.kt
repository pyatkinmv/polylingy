package com.polylingy.content.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.polylingy.content.Block

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

internal val LocalContentColors = staticCompositionLocalOf { ContentColors(ContentColors.DefaultAccent) }
internal val LocalImageLoader = staticCompositionLocalOf { ImageLoader { null } }
internal val LocalLinkNames = staticCompositionLocalOf { LinkNames { null } }

/** Provides colours, pictures and link names to the content views inside. */
@Composable
fun ContentTheme(
    colors: ContentColors,
    images: ImageLoader = ImageLoader { null },
    links: LinkNames = LinkNames { null },
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalContentColors provides colors, LocalImageLoader provides images, LocalLinkNames provides links, content = content)
}

internal val colors: ContentColors @Composable get() = LocalContentColors.current

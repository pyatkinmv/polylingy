package com.polylingy.content.compose

import androidx.compose.ui.graphics.Color

/** A named set of colours a course can be drawn in; [id] is what the course's `rules.display.palette` and the settings store. */
data class Palette(val id: String, val name: String, val colors: ContentColors)

/**
 * The palettes of the app. A course names one by id; an unknown id (a course of a newer version) is drawn in the [default].
 * Every palette has the same roles (see [ContentColors]); a test keeps their text readable on every fill.
 */
object Palettes {
    val softGreen = Palette(
        id = "softGreen", name = "Soft green",
        colors = ContentColors(
            accent = Color(0xFF2F6B4F), accentSoft = Color(0xFFE9F2EB), ink = Color(0xFF1F2A22), muted = Color(0xFF62695F), line = Color(0xFFE1E7DC), card = Color(0xFFFFFEFB),
            panel = Color(0xFFEEF5F0), background = Color(0xFFFAF7F0), head = Color(0xFFDCEBE0), column = Color(0xFFEEF5F0), stripe = Color(0xFFF7F9F4), separator = Color(0xFFC6DACD),
            typo = Color(0xFF7A5A1E), typoBg = Color(0xFFF7EFD9), info = Color(0xFF2F5F8A), infoBg = Color(0xFFE4EEF7), culture = Color(0xFF8A4F2A), cultureBg = Color(0xFFF5E8DE),
            marks = listOf(
            MarkColor(Color(0xFFF4D9C2), Color(0xFFA4561F)),
            MarkColor(Color(0xFFCFE6D6), Color(0xFF2F6B4F)),
            MarkColor(Color(0xFFD2E3F2), Color(0xFF2F5F8A)),
            MarkColor(Color(0xFFF3D6D2), Color(0xFFA33A30)),
            MarkColor(Color(0xFFF1E4BE), Color(0xFF7A5A1E)),
            ),
        ),
    )

    val paperAndInk = Palette(
        id = "paperAndInk", name = "Paper and ink",
        colors = ContentColors(
            accent = Color(0xFF2F6B4F), accentSoft = Color(0xFFF0EEE8), ink = Color(0xFF22201C), muted = Color(0xFF6A665C), line = Color(0xFFE4E0D6), card = Color(0xFFFFFFFF),
            panel = Color(0xFFF4F2EC), background = Color(0xFFF7F5F0), head = Color(0xFFECEAE2), column = Color(0xFFF4F2EC), stripe = Color(0xFFFAF9F6), separator = Color(0xFFD6D2C6),
            typo = Color(0xFF7A5A1E), typoBg = Color(0xFFF6F0E0), info = Color(0xFF2F5F8A), infoBg = Color(0xFFEBF0F5), culture = Color(0xFF8A4F2A), cultureBg = Color(0xFFF4ECE6),
            marks = listOf(
            MarkColor(Color(0xFFCFE3F5), Color(0xFF2F5F8A)),
            MarkColor(Color(0xFFCBEBD6), Color(0xFF2F6B4F)),
            MarkColor(Color(0xFFF8DDBB), Color(0xFF8A4F2A)),
            MarkColor(Color(0xFFF6D3D0), Color(0xFFA33A30)),
            MarkColor(Color(0xFFFBEEB5), Color(0xFF7A5A1E)),
            ),
        ),
    )

    val blue = Palette(
        id = "blue", name = "Blue",
        colors = ContentColors(
            accent = Color(0xFF2F5FA3), accentSoft = Color(0xFFE7EEF8), ink = Color(0xFF1C2330), muted = Color(0xFF5B6577), line = Color(0xFFDDE3EC), card = Color(0xFFFFFFFF),
            panel = Color(0xFFEDF2FA), background = Color(0xFFF7F8FA), head = Color(0xFFD9E5F5), column = Color(0xFFEDF2FA), stripe = Color(0xFFF5F8FC), separator = Color(0xFFC3D3EA),
            typo = Color(0xFF8A5A00), typoBg = Color(0xFFFBF0D5), info = Color(0xFF2F5FA3), infoBg = Color(0xFFE7EEF8), culture = Color(0xFFA1502A), cultureBg = Color(0xFFF7E4DA),
            marks = listOf(
            MarkColor(Color(0xFFF7DCC4), Color(0xFFA1502A)),
            MarkColor(Color(0xFFCFDFF5), Color(0xFF2F5FA3)),
            MarkColor(Color(0xFFCDEBDD), Color(0xFF256B4A)),
            MarkColor(Color(0xFFF6D6D2), Color(0xFFA33A30)),
            MarkColor(Color(0xFFF7E3B8), Color(0xFF8A5A00)),
            ),
        ),
    )

    val terracotta = Palette(
        id = "terracotta", name = "Terracotta",
        colors = ContentColors(
            accent = Color(0xFF9A4617), accentSoft = Color(0xFFF8E9DC), ink = Color(0xFF2A211A), muted = Color(0xFF7A6A5A), line = Color(0xFFEADFCF), card = Color(0xFFFFFDF9),
            panel = Color(0xFFFAEFE5), background = Color(0xFFFBF6EE), head = Color(0xFFF3DDCB), column = Color(0xFFFAEFE5), stripe = Color(0xFFFCF8F2), separator = Color(0xFFE5CBB5),
            typo = Color(0xFF7A5200), typoBg = Color(0xFFF6E9C8), info = Color(0xFF2F5F8A), infoBg = Color(0xFFE4EEF7), culture = Color(0xFF9A4617), cultureBg = Color(0xFFF8E9DC),
            marks = listOf(
            MarkColor(Color(0xFFD3E7D8), Color(0xFF2F6B4F)),
            MarkColor(Color(0xFFF2D2BB), Color(0xFF9A4617)),
            MarkColor(Color(0xFFD2E3F2), Color(0xFF2F5F8A)),
            MarkColor(Color(0xFFF3D3CF), Color(0xFFA33A30)),
            MarkColor(Color(0xFFF6E1AF), Color(0xFF7A5200)),
            ),
        ),
    )

    val pine = Palette(
        id = "pine", name = "Pine",
        colors = ContentColors(
            accent = Color(0xFF1F5E4B), accentSoft = Color(0xFFE3EEE8), ink = Color(0xFF1B2620), muted = Color(0xFF59655C), line = Color(0xFFDCE3DA), card = Color(0xFFFFFFFF),
            panel = Color(0xFFEAF3EE), background = Color(0xFFF4F6F2), head = Color(0xFFD3E5DB), column = Color(0xFFEAF3EE), stripe = Color(0xFFF6F9F7), separator = Color(0xFFBCD5C8),
            typo = Color(0xFF755500), typoBg = Color(0xFFF4EBCF), info = Color(0xFF2A5F86), infoBg = Color(0xFFE1ECF5), culture = Color(0xFF8A4A2A), cultureBg = Color(0xFFF3E5DB),
            marks = listOf(
            MarkColor(Color(0xFFEBD6C8), Color(0xFF8A4A2A)),
            MarkColor(Color(0xFFC8E3D5), Color(0xFF1F5E4B)),
            MarkColor(Color(0xFFCBDFEF), Color(0xFF2A5F86)),
            MarkColor(Color(0xFFF1D3CF), Color(0xFF9C3A30)),
            MarkColor(Color(0xFFEFE3B8), Color(0xFF755500)),
            ),
        ),
    )

    val plum = Palette(
        id = "plum", name = "Plum",
        colors = ContentColors(
            accent = Color(0xFF6B4A8E), accentSoft = Color(0xFFEFE9F5), ink = Color(0xFF241F2B), muted = Color(0xFF675F72), line = Color(0xFFE5DFEA), card = Color(0xFFFFFFFF),
            panel = Color(0xFFF3EEF8), background = Color(0xFFF9F7FA), head = Color(0xFFE3D9EE), column = Color(0xFFF3EEF8), stripe = Color(0xFFFAF8FC), separator = Color(0xFFD2C5E1),
            typo = Color(0xFF7A5A1E), typoBg = Color(0xFFF7EFD9), info = Color(0xFF2F5F8A), infoBg = Color(0xFFE4EEF7), culture = Color(0xFF9A4A12), cultureBg = Color(0xFFF6E5D8),
            marks = listOf(
            MarkColor(Color(0xFFD0E8DA), Color(0xFF2F6B4F)),
            MarkColor(Color(0xFFE4D6F0), Color(0xFF6B4A8E)),
            MarkColor(Color(0xFFD3E2F3), Color(0xFF2F5F8A)),
            MarkColor(Color(0xFFF4D5DC), Color(0xFFA33A5A)),
            MarkColor(Color(0xFFF6E4B8), Color(0xFF7A5A1E)),
            ),
        ),
    )


    /** A dark palette: how a course looks on a dark page. */
    val midnight = Palette(
        id = "midnight", name = "Midnight",
        colors = ContentColors(
            accent = Color(0xFF7CC9A3), accentSoft = Color(0xFF243A33), ink = Color(0xFFE8ECEF), muted = Color(0xFFA3AEB6), line = Color(0xFF333C44), card = Color(0xFF232930),
            panel = Color(0xFF2A323A), background = Color(0xFF1B1F24), head = Color(0xFF2D4A3F), column = Color(0xFF28322F), stripe = Color(0xFF20262C), separator = Color(0xFF3E4C55),
            typo = Color(0xFFE6C27A), typoBg = Color(0xFF3A3020), info = Color(0xFF8FC1EB), infoBg = Color(0xFF1F3345), culture = Color(0xFFE3A07A), cultureBg = Color(0xFF3B2A20),
            marks = listOf(
                MarkColor(Color(0xFF4A3626), Color(0xFFF0B27F)),
                MarkColor(Color(0xFF264A3B), Color(0xFF86D3AB)),
                MarkColor(Color(0xFF26405A), Color(0xFF8FC1EB)),
                MarkColor(Color(0xFF52302D), Color(0xFFF09A92)),
                MarkColor(Color(0xFF4A4025), Color(0xFFE6CB7A)),
            ),
            onAccent = Color(0xFF10231B),
            correct = Color(0xFF86D3AB), correctBg = Color(0xFF1F3A2D), wrong = Color(0xFFF09A92), wrongBg = Color(0xFF44262A),
        ),
    )

    val all: List<Palette> = listOf(softGreen, paperAndInk, blue, terracotta, pine, plum, midnight)

    /** The palette a course without its own choice is drawn in. */
    val default: Palette = softGreen

    fun byId(id: String?): Palette = all.firstOrNull { it.id == id } ?: default
}

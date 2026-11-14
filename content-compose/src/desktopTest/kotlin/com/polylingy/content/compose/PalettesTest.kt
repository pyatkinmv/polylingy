package com.polylingy.content.compose

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The catalogue of palettes: complete, and readable on every fill it puts text on. */
class PalettesTest {
    private fun luminance(c: Color): Double {
        fun channel(v: Float): Double = v.toDouble().let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test fun idsAreUniqueAndAnUnknownIdGivesTheDefault() {
        assertEquals(Palettes.all.size, Palettes.all.map { it.id }.toSet().size)
        assertEquals(Palettes.default, Palettes.byId(null))
        assertEquals(Palettes.default, Palettes.byId("from-a-newer-version"))
        for (palette in Palettes.all) assertEquals(palette, Palettes.byId(palette.id))
    }

    @Test fun everyPaletteHasFiveMarks() {
        for (palette in Palettes.all) assertEquals(5, palette.colors.marks.size, palette.id)
    }

    @Test fun textIsReadableOnEveryFillOfEveryPalette() {
        val problems = mutableListOf<String>()
        fun check(palette: String, what: String, text: Color, on: Color, min: Double) {
            val ratio = contrast(text, on)
            if (ratio < min) problems += "$palette: $what is %.2f, needs $min".format(ratio)
        }
        for (palette in Palettes.all) {
            val c = palette.colors
            val id = palette.id
            for ((name, fill) in listOf("background" to c.background, "card" to c.card, "accentSoft" to c.accentSoft, "head" to c.head, "column" to c.column, "stripe" to c.stripe, "panel" to c.panel)) {
                check(id, "ink on $name", c.ink, fill, 7.0)
            }
            check(id, "muted on background", c.muted, c.background, 4.5)
            check(id, "muted on card", c.muted, c.card, 4.5)
            for ((name, fill) in listOf("background" to c.background, "card" to c.card, "accentSoft" to c.accentSoft, "head" to c.head)) check(id, "accent on $name", c.accent, fill, 4.5)
            check(id, "white on accent", c.onAccent, c.accent, 4.5)
            check(id, "warning on its fill", c.typo, c.typoBg, 4.5)
            check(id, "fact on its fill", c.info, c.infoBg, 4.5)
            check(id, "culture on its fill", c.culture, c.cultureBg, 4.5)
            c.marks.forEachIndexed { i, mark ->
                check(id, "ink on mark ${i + 1}", c.ink, mark.fill, 7.0)
                check(id, "ink on mark ${i + 1} over a remark", c.ink, mark.fill, 7.0)
                check(id, "strong mark ${i + 1} on card", mark.strong, c.card, 4.5)
                check(id, "strong mark ${i + 1} on the remark fill", mark.strong, c.accentSoft, 4.5)
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }
}

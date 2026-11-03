package com.polylingy.core.rules

import com.polylingy.content.ItemResult

/** How a typed answer compares with the accepted variants; [variant] is the one it matched or came closest to. */
data class TextMatch(val result: ItemResult, val variant: String)

/**
 * The one place where typed text is compared (docs/learning-model.md, section 3).
 *
 * Both sides are normalized first: case doesn't matter, spaces at the ends are dropped and runs of spaces count as one,
 * a final period doesn't matter, and every kind of apostrophe (' ’ ‘ ʼ `) is the same; the apostrophe itself matters.
 *
 * A typo is one substituted letter or two swapped neighbouring letters, in a word of at least [MIN_TYPO_WORD] letters,
 * at most once per item. A missing or extra letter is not a typo: that's how mistakes in endings (-s, -ed, -ing) look.
 * A typo is always reported; whether it counts as right is up to the course's grading.
 */
object TextMatcher {
    const val MIN_TYPO_WORD = 4

    private val apostrophes = Regex("[’‘ʼ`]")
    private val spaces = Regex("\\s+")

    fun match(input: String, variants: List<String>): TextMatch {
        require(variants.isNotEmpty()) { "an item needs at least one right answer" }
        val given = normalize(input)
        variants.firstOrNull { normalize(it) == given }?.let { return TextMatch(ItemResult.CORRECT, it) }
        if (given.isNotEmpty()) {
            variants.firstOrNull { isTypo(given, normalize(it)) }?.let { return TextMatch(ItemResult.TYPO, it) }
        }
        return TextMatch(ItemResult.WRONG, variants.first())
    }

    fun normalize(text: String): String =
        text.lowercase().replace(apostrophes, "'").trim().replace(spaces, " ").removeSuffix(".").trimEnd()

    /** [given] and [right] are normalized and differ. */
    private fun isTypo(given: String, right: String): Boolean {
        val a = given.split(' ')
        val b = right.split(' ')
        if (a.size != b.size) return false
        val differing = a.indices.filter { a[it] != b[it] }
        if (differing.size != 1) return false
        val i = differing.single()
        return isWordTypo(a[i], b[i])
    }

    private fun isWordTypo(given: String, right: String): Boolean {
        if (given.length != right.length || right.count { it.isLetter() } < MIN_TYPO_WORD) return false
        val diff = given.indices.filter { given[it] != right[it] }
        return when (diff.size) {
            1 -> true
            2 -> diff[1] == diff[0] + 1 && given[diff[0]] == right[diff[1]] && given[diff[1]] == right[diff[0]]
            else -> false
        }
    }
}

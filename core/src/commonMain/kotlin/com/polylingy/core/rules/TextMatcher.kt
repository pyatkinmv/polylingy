package com.polylingy.core.rules

import com.polylingy.content.ItemResult

/** How a typed answer compares with the accepted variants; [variant] is the one it matched or came closest to. */
data class TextMatch(val result: ItemResult, val variant: String)

/** How a typed answer holds the key words; [reason] says what is wrong when it is [ItemResult.WRONG]. */
data class KeysMatch(val result: ItemResult, val reason: String? = null)

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

    /**
     * Whether [input] has the key words of one of [orders] in that order, with any words between them. A key may be a
     * phrase ("my charger"): its words must stand together. Words are compared normalized, without punctuation; one
     * typo in one key word is allowed and makes the result [ItemResult.TYPO].
     *
     * When no order fits, the reason names, by the first order: the keys not found anywhere ("Not found: borrowing"),
     * or else the first two in the wrong order ("Wrong order: continually before interrupting"), or else that more than
     * one key has a typo.
     */
    fun keys(input: String, orders: List<List<String>>): KeysMatch {
        require(orders.isNotEmpty() && orders.none { it.isEmpty() }) { "an item needs at least one order of key words" }
        val words = words(input)
        var typo = false
        for (order in orders) {
            when (findInOrder(words, order)) {
                Found.EXACT -> return KeysMatch(ItemResult.CORRECT)
                Found.TYPO -> typo = true
                null -> {}
            }
        }
        if (typo) return KeysMatch(ItemResult.TYPO)
        val first = orders.first()
        val positions = first.map { key -> find(words, words(key), 0, typoAllowed = true)?.first }
        val missing = first.filterIndexed { i, _ -> positions[i] == null }
        if (missing.isNotEmpty()) return KeysMatch(ItemResult.WRONG, "Not found: ${missing.joinToString(", ")}")
        val i = (0 until first.lastIndex).firstOrNull { positions[it]!! > positions[it + 1]!! }
        return KeysMatch(ItemResult.WRONG, if (i == null) "More than one typo" else "Wrong order: ${first[i]} before ${first[i + 1]}")
    }

    private enum class Found { EXACT, TYPO }

    /** Finds the keys one after another; at most one key may be found with a typo. */
    private fun findInOrder(words: List<String>, order: List<String>): Found? {
        var from = 0
        var typo = false
        for (key in order) {
            val (at, withTypo) = find(words, words(key), from, typoAllowed = !typo) ?: return null
            typo = typo || withTypo
            from = at + words(key).size
        }
        return if (typo) Found.TYPO else Found.EXACT
    }

    /** The first place at or after [from] where [phrase] stands, exactly or (if [typoAllowed]) with one typo; with whether it was a typo. */
    private fun find(words: List<String>, phrase: List<String>, from: Int, typoAllowed: Boolean): Pair<Int, Boolean>? {
        if (phrase.isEmpty()) return null
        val starts = from..words.size - phrase.size
        starts.firstOrNull { i -> phrase.indices.all { words[i + it] == phrase[it] } }?.let { return it to false }
        if (!typoAllowed) return null
        return starts.firstOrNull { i ->
            val differing = phrase.indices.filter { words[i + it] != phrase[it] }
            differing.size == 1 && isWordTypo(words[i + differing.single()], phrase[differing.single()])
        }?.let { it to true }
    }

    private val notWord = Regex("[^\\p{L}\\p{N}']+")

    private fun words(text: String): List<String> =
        text.lowercase().replace(apostrophes, "'").split(notWord).map { it.trim('\'') }.filter { it.isNotEmpty() }

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

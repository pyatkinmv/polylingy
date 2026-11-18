package com.polylingy.content

import kotlin.jvm.JvmInline

/** Where something is in a course file, e.g. `units[0].topics[2].exercises[4].items[1].text`. */
@JvmInline
value class Path(val text: String) {
    operator fun div(key: String): Path = Path(if (text.isEmpty()) key else "$text.$key")
    operator fun get(index: Int): Path = Path("$text[$index]")
    override fun toString(): String = text.ifEmpty { "$" }

    companion object {
        val ROOT = Path("")
    }
}

/** One thing wrong with a course file. */
data class Problem(val path: Path, val message: String, val kind: Kind = Kind.ERROR) {
    enum class Kind {
        /** The file is wrong. */
        ERROR,

        /** A block, exercise or content format this app doesn't know — fine in a file from a newer app version. */
        UNKNOWN_TYPE,
    }

    override fun toString(): String = "$path: $message"
}

/** How many blanks `{{…}}` a text may or must have. */
internal enum class BlankRule { NONE, ANY, AT_LEAST_ONE, EXACTLY_ONE }

/** The longest answer a blank accepts: the field stops taking characters here, so every variant must fit. */
const val MAX_BLANK_LENGTH = 60

/** The longest answer typed as a whole sentence (expand). */
const val MAX_ANSWER_LENGTH = 150

/** Collects problems while a validate() walks a tree. */
internal class Checks(private val out: MutableList<Problem>) {
    fun error(at: Path, message: String) {
        out += Problem(at, message)
    }

    fun unknownType(at: Path, what: String, type: String) {
        out += Problem(at, "unknown $what \"$type\"", Problem.Kind.UNKNOWN_TYPE)
    }

    fun require(condition: Boolean, at: Path, message: String) {
        if (!condition) error(at, message)
    }

    fun notBlank(value: String?, at: Path) = require(value == null || value.isNotBlank(), at, "must not be empty")

    /**
     * Checks markup and blanks; [mayBeEmpty] is for table cells, which are often left empty. Every variant of a blank must
     * fit [maxBlank]; null — the caller checks lengths itself. [hints]: whether `{{…}}[[hint]]` is allowed (typed blanks of `gaps`).
     */
    fun text(inline: Inline?, at: Path, blanks: BlankRule = BlankRule.NONE, mayBeEmpty: Boolean = false, maxBlank: Int? = MAX_BLANK_LENGTH, hints: Boolean = false) {
        if (inline == null) return
        require(mayBeEmpty || inline.text.isNotBlank(), at, "must not be empty")
        inline.problems.forEach { error(at, it) }
        val count = inline.blanks.size
        when (blanks) {
            BlankRule.NONE -> require(count == 0, at, "blanks {{…}} are only allowed in exercises")
            BlankRule.ANY -> {}
            BlankRule.AT_LEAST_ONE -> require(count >= 1, at, "needs at least one blank {{…}}")
            BlankRule.EXACTLY_ONE -> require(count == 1, at, "needs exactly one blank {{…}}, found $count")
        }
        if (!hints) require(inline.blanks.none { it.hint != null }, at, "a hint [[…]] after a blank is only for the sentences of a gaps exercise")
        if (maxBlank != null) for (blank in inline.blanks) for (variant in blank.variants) {
            require(variant.length <= maxBlank, at, "answer \"$variant\" is longer than $maxBlank characters")
        }
    }

    fun nested(problems: List<Problem>) {
        out += problems
    }
}

internal fun checks(body: Checks.() -> Unit): List<Problem> = buildList { Checks(this).body() }

/** Checks a text outside an exercise: not empty, well-formed markup, no blanks. */
fun Inline.validate(at: Path): List<Problem> = checks { text(this@validate, at) }

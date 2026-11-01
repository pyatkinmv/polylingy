package com.polylingy.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.uuid.Uuid

/**
 * One exercise, shown and checked as a whole. Its type decides its fields, how it looks and how it is checked;
 * an exercise is made of items, and each blank, choice, pair or sentence is one checked item ([itemCount]).
 *
 * Adding a type touches exactly: a class here with validate(), its view, its checker and a sample file in tests.
 */
@Serializable
sealed interface Exercise {
    val id: Uuid
    val instruction: Inline?

    /** Shown under the exercise after it is checked. */
    val explanation: Content?
    val itemCount: Int

    fun validate(at: Path): List<Problem>

    /** Sentences with blanks to type into. */
    @Serializable
    @SerialName("gaps")
    data class Gaps(
        override val id: Uuid,
        override val instruction: Inline? = null,
        override val explanation: Content? = null,
        /** A solved sentence shown first, as "0" in a textbook. */
        val example: Inline? = null,
        val wordBank: List<String> = emptyList(),
        val items: List<GapItem>,
    ) : Exercise {
        override val itemCount get() = items.sumOf { it.text.blanks.size }

        override fun validate(at: Path) = checks {
            common(this@Gaps, at)
            text(example, at / "example")
            wordBank(wordBank, at / "wordBank")
            require(items.isNotEmpty(), at / "items", "needs at least one item")
            items.forEachIndexed { i, item ->
                val itemAt = (at / "items")[i]
                text(item.text, itemAt / "text", BlankRule.AT_LEAST_ONE)
                notBlank(item.hint, itemAt / "hint")
                text(item.note, itemAt / "note")
            }
        }
    }

    /** [hint] is shown in brackets after the sentence, [note] under it when the answer is wrong. */
    @Serializable
    data class GapItem(val text: Inline, val hint: String? = null, val note: Inline? = null)

    /** Sentences where one of [ChoiceItem.options] is picked; the blank holds the right one. */
    @Serializable
    @SerialName("choice")
    data class Choice(
        override val id: Uuid,
        override val instruction: Inline? = null,
        override val explanation: Content? = null,
        val items: List<ChoiceItem>,
    ) : Exercise {
        override val itemCount get() = items.size

        override fun validate(at: Path) = checks {
            common(this@Choice, at)
            require(items.isNotEmpty(), at / "items", "needs at least one item")
            items.forEachIndexed { i, item ->
                val itemAt = (at / "items")[i]
                text(item.text, itemAt / "text", BlankRule.EXACTLY_ONE)
                text(item.note, itemAt / "note")
                val options = itemAt / "options"
                require(item.options.size >= 2, options, "needs at least 2 options")
                require(item.options.toSet().size == item.options.size, options, "has the same option twice")
                item.options.forEachIndexed { o, option -> notBlank(option, options[o]) }
                item.text.blanks.singleOrNull()?.let { blank ->
                    require(blank.variants.size == 1, itemAt / "text", "a choice has one right answer, not variants")
                    require(blank.answer in item.options, options, "must include the right answer \"${blank.answer}\"")
                }
            }
        }
    }

    @Serializable
    data class ChoiceItem(val text: Inline, val options: List<String>, val note: Inline? = null)

    /** Picture cards, each with a sentence with blanks. */
    @Serializable
    @SerialName("pictures")
    data class Pictures(
        override val id: Uuid,
        override val instruction: Inline? = null,
        override val explanation: Content? = null,
        val wordBank: List<String> = emptyList(),
        val items: List<PictureItem>,
    ) : Exercise {
        override val itemCount get() = items.sumOf { it.text.blanks.size }

        override fun validate(at: Path) = checks {
            common(this@Pictures, at)
            wordBank(wordBank, at / "wordBank")
            require(items.isNotEmpty(), at / "items", "needs at least one item")
            items.forEachIndexed { i, item ->
                val itemAt = (at / "items")[i]
                nested(item.image.validate(itemAt / "image"))
                text(item.text, itemAt / "text", BlankRule.AT_LEAST_ONE)
                text(item.note, itemAt / "note")
            }
        }
    }

    @Serializable
    data class PictureItem(val image: Image, val text: Inline, val note: Inline? = null)

    /** A table with blanks in its cells. */
    @Serializable
    @SerialName("table")
    data class Table(
        override val id: Uuid,
        override val instruction: Inline? = null,
        override val explanation: Content? = null,
        val head: List<Inline> = emptyList(),
        val rows: List<List<Inline>>,
    ) : Exercise {
        override val itemCount get() = rows.sumOf { row -> row.sumOf { it.blanks.size } }

        override fun validate(at: Path) = checks {
            common(this@Table, at)
            head.forEachIndexed { i, cell -> text(cell, (at / "head")[i], mayBeEmpty = true) }
            require(rows.isNotEmpty(), at / "rows", "needs at least one row")
            val width = head.size.takeIf { it > 0 } ?: rows.firstOrNull()?.size
            rows.forEachIndexed { r, row ->
                require(row.size == width, (at / "rows")[r], "has ${row.size} cells, expected $width")
                row.forEachIndexed { c, cell -> text(cell, (at / "rows")[r][c], BlankRule.ANY, mayBeEmpty = true) }
            }
            require(rows.isEmpty() || itemCount > 0, at / "rows", "needs at least one blank {{…}}")
        }
    }

    /** Pairs to match; the right sides and [extra] (sides that fit nothing) are shown shuffled. */
    @Serializable
    @SerialName("match")
    data class Match(
        override val id: Uuid,
        override val instruction: Inline? = null,
        override val explanation: Content? = null,
        /** Each pair is `[left, right]`. */
        val pairs: List<List<Side>>,
        val extra: List<Side> = emptyList(),
    ) : Exercise {
        override val itemCount get() = pairs.size

        /** All right-hand sides in a fixed order: the pairs' right sides, then [extra]. Answers refer to this order. */
        val rightSides: List<Side> get() = pairs.mapNotNull { it.getOrNull(1) } + extra

        override fun validate(at: Path) = checks {
            common(this@Match, at)
            require(pairs.size >= 2, at / "pairs", "needs at least 2 pairs")
            pairs.forEachIndexed { i, pair ->
                val pairAt = (at / "pairs")[i]
                require(pair.size == 2, pairAt, "a pair is [left, right], found ${pair.size} sides")
                pair.forEachIndexed { s, side -> side(side, pairAt[s]) }
            }
            extra.forEachIndexed { i, side -> side(side, (at / "extra")[i]) }
            val lefts = pairs.mapNotNull { it.getOrNull(0) }
            require(lefts.toSet().size == lefts.size, at / "pairs", "has the same left side twice")
            require(rightSides.toSet().size == rightSides.size, at / "pairs", "has the same right side twice (pairs and extra)")
        }
    }

    /** Sentences to build from their shuffled words. */
    @Serializable
    @SerialName("order")
    data class Order(
        override val id: Uuid,
        override val instruction: Inline? = null,
        override val explanation: Content? = null,
        val items: List<OrderItem>,
    ) : Exercise {
        override val itemCount get() = items.size

        override fun validate(at: Path) = checks {
            common(this@Order, at)
            require(items.isNotEmpty(), at / "items", "needs at least one item")
            items.forEachIndexed { i, item ->
                val itemAt = (at / "items")[i]
                require(item.words.size >= 2, itemAt / "answer", "needs at least 2 words")
                item.accept.forEachIndexed { a, other ->
                    val same = OrderItem(other)
                    require(same.words.sorted() == item.words.sorted() && same.ending == item.ending,
                        (itemAt / "accept")[a], "must use the same words and ending as the answer")
                }
                text(item.note, itemAt / "note")
            }
        }
    }

    /**
     * [accept] lists other right word orders. The sentence's closing punctuation is not a tile: it stays fixed
     * at the end, so "Is it raining now?" and "Is it now raining?" are built from the same tiles.
     */
    @Serializable
    data class OrderItem(val answer: String, val accept: List<String> = emptyList(), val note: Inline? = null) {
        /** The words shown as tiles: `["Is", "it", "raining", "now"]`. */
        val words: List<String> get() = words(answer.trimEnd().dropLastWhile { it in ENDING })

        /** The fixed end of the sentence: `"?"`. */
        val ending: String get() = answer.trimEnd().takeLastWhile { it in ENDING }
    }

    /** An exercise type from a newer format; it is not shown. */
    @Serializable(with = UnknownExerciseSerializer::class)
    data class Unknown(val raw: JsonObject) : Exercise {
        val type: String get() = raw.typeName()
        override val id: Uuid
            get() = (raw["id"] as? JsonPrimitive)?.content?.let { runCatching { Uuid.parse(it) }.getOrNull() } ?: Uuid.NIL
        override val instruction: Inline? get() = null
        override val explanation: Content? get() = null
        override val itemCount get() = 0
        override fun validate(at: Path) = checks { unknownType(at, "exercise type", type) }
    }
}

private const val ENDING = ".?!…"

internal fun words(sentence: String): List<String> = sentence.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

private fun Checks.common(exercise: Exercise, at: Path) {
    text(exercise.instruction, at / "instruction")
    exercise.explanation?.let { nested(it.validate(at / "explanation")) }
}

private fun Checks.wordBank(words: List<String>, at: Path) = words.forEachIndexed { i, word -> notBlank(word, at[i]) }

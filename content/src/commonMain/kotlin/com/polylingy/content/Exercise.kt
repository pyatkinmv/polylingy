package com.polylingy.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.uuid.Uuid

/**
 * One exercise, shown and checked as a whole. An exercise is made of items, and each blank, choice, pair, sentence
 * or marked word is one checked item ([itemCount]).
 *
 * Every exercise is drawn as the same layers, top to bottom (docs/content-format.md, "Слои упражнения"):
 *
 * 1. [instruction] — the task;
 * 2. [wordBank] — words to use, a read-only reference; only gaps, pictures and expand have one;
 * 3. [audio] — a sound to listen to, its play button above the frame;
 * 4. [video] — a video to watch, its card above the frame;
 * 5. [passage] — a page of blocks to read first (a dialogue, a story, a letter), drawn exactly as in a unit's theory, above the frame;
 * 6. [look] — a frame with a header (an email, an article, a newspaper clipping) around 7 and 8; no look, no frame;
 * 7. [aside] — one picture next to the body: inside the frame when there is a look;
 * 8. the body — the type's own items: the only layer a type draws;
 * 9. [explanation] — after the exercise is checked.
 *
 * Layers 1–7 and 9 are fields of every type and know nothing of each other or of the body, so any type combines with
 * any sound, look and aside. A type's own fields only shape its body.
 *
 * Adding a type touches exactly: a class here with validate() and [needsKeyboard], its body view, its checker, a sample
 * in tests and its definition in docs/course.schema.json.
 */
@Serializable
sealed interface Exercise {
    val id: Uuid
    val instruction: Inline?

    /** Shown under the exercise after it is checked. */
    val explanation: Content?

    /**
     * A page of blocks the exercise is about, shown above the frame in the same way as in a unit's theory (a dialogue with portraits, a story,
     * a letter): the learner reads it and answers from it, so the exercise stands on its own. Only the original text: a translation would give the answers away.
     * Null — none.
     */
    val passage: Content?

    /** The frame around the body; null — none. */
    val look: Look?

    /** A picture next to the body; null — none. */
    val aside: Aside?

    /** Words to use, shown above the frame; empty for types that have no word bank. */
    val wordBank: List<String>

    /** A sound to listen to before answering (an `https://` URL or a path in the archive); null — none. */
    val audio: String?

    /** A video to watch before answering, a card above the frame that opens it in the browser; null — none. */
    val video: Block.Video?

    /** Whether the exercise was generated rather than written by the course's author; false — written. Shown nowhere, only in the file. */
    val generated: Boolean

    /**
     * Whether answering means typing on a keyboard. Every type says it itself, so a new type can't be left out: the
     * course setting "avoid typing" prefers the exercises of a topic where this is false.
     */
    val needsKeyboard: Boolean
    val itemCount: Int

    fun validate(at: Path): List<Problem>

    /** Sentences with blanks to type into. */
    @Serializable
    @SerialName("gaps")
    data class Gaps(
        override val id: Uuid,
        override val instruction: Inline? = null,
        override val explanation: Content? = null,
        override val passage: Content? = null,
        override val look: Look? = null,
        override val aside: Aside? = null,
        override val audio: String? = null,
        override val video: Block.Video? = null,
        override val generated: Boolean = false,
        /** A solved sentence shown first, as "0" in a textbook. */
        val example: Inline? = null,
        override val wordBank: List<String> = emptyList(),
        val items: List<GapItem>,
    ) : Exercise {
        override val needsKeyboard get() = true
        override val itemCount get() = items.sumOf { it.text.blanks.size }

        override fun validate(at: Path) = checks {
            common(this@Gaps, at)
            text(example, at / "example")
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

    /**
     * Sentences where an option is picked in each place. A place is written in one of two ways:
     * - the old one: one blank holding the right answer, and the item's [ChoiceItem.options];
     * - in the text: any number of blanks, each listing its options, the right one first: `{{admit|’m admitting}}`.
     * Each place is one checked item; [places] lists them in reading order.
     */
    @Serializable
    @SerialName("choice")
    data class Choice(
        override val id: Uuid,
        override val instruction: Inline? = null,
        override val explanation: Content? = null,
        override val passage: Content? = null,
        override val look: Look? = null,
        override val aside: Aside? = null,
        override val audio: String? = null,
        override val video: Block.Video? = null,
        override val generated: Boolean = false,
        val items: List<ChoiceItem>,
    ) : Exercise {
        override val needsKeyboard get() = false
        override val wordBank: List<String> get() = emptyList()
        override val itemCount get() = places.size

        /** Every place in reading order; [Response.Choice.picked] follows it. */
        val places: List<ChoicePlace> get() = items.flatMap { it.places }

        override fun validate(at: Path) = checks {
            common(this@Choice, at)
            require(items.isNotEmpty(), at / "items", "needs at least one item")
            items.forEachIndexed { i, item ->
                val itemAt = (at / "items")[i]
                text(item.note, itemAt / "note")
                notBlank(item.mark, itemAt / "mark")
                audio(item.audio, itemAt / "audio")
                if (item.options.isNotEmpty()) {
                    text(item.text, itemAt / "text", BlankRule.EXACTLY_ONE)
                    val options = itemAt / "options"
                    require(item.options.size >= 2, options, "needs at least 2 options")
                    require(item.options.toSet().size == item.options.size, options, "has the same option twice")
                    item.options.forEachIndexed { o, option -> notBlank(option, options[o]) }
                    item.text.blanks.singleOrNull()?.let { blank ->
                        require(blank.variants.size == 1, itemAt / "text", "with \"options\" the blank holds only the right answer")
                        require(blank.answer in item.options, options, "must include the right answer \"${blank.answer}\"")
                    }
                } else {
                    text(item.text, itemAt / "text", BlankRule.AT_LEAST_ONE)
                    for (blank in item.text.blanks) {
                        require(blank.variants.size >= 2, itemAt / "text",
                            "each place needs its options {{right|other}}, or the item needs \"options\": \"{{${blank.answer}}}\" has one")
                        require(blank.variants.toSet().size == blank.variants.size, itemAt / "text", "\"{{${blank.variants.joinToString("|")}}}\" has the same option twice")
                    }
                }
            }
        }
    }

    /**
     * [options] are given for an item with one place; empty when each place lists its options in the text. [mark] is a piece
     * of text the item is about ("ب" in "how many times does ب appear"): once the exercise is checked, choosing the item shows
     * every place of it in the text of the exercise's frame (a headline, a letter), so the learner sees what was meant.
     * [audio] is a sound of this item alone (a word to recognise by ear); its play button stands before the item.
     */
    @Serializable
    data class ChoiceItem(
        val text: Inline,
        val options: List<String> = emptyList(),
        val note: Inline? = null,
        val mark: String? = null,
        val audio: String? = null,
    ) {
        val places: List<ChoicePlace>
            get() = if (options.isNotEmpty()) text.blanks.map { ChoicePlace(options, it.answer) }
            else text.blanks.map { ChoicePlace(it.variants, it.answer) }
    }

    /** One place to pick in: its options in the written order and the right one. */
    data class ChoicePlace(val options: List<String>, val answer: String)

    /** Picture cards, each with a sentence with blanks. */
    @Serializable
    @SerialName("pictures")
    data class Pictures(
        override val id: Uuid,
        override val instruction: Inline? = null,
        override val explanation: Content? = null,
        override val passage: Content? = null,
        override val look: Look? = null,
        override val aside: Aside? = null,
        override val audio: String? = null,
        override val video: Block.Video? = null,
        override val generated: Boolean = false,
        override val wordBank: List<String> = emptyList(),
        val items: List<PictureItem>,
    ) : Exercise {
        override val needsKeyboard get() = true
        override val itemCount get() = items.sumOf { it.text.blanks.size }

        override fun validate(at: Path) = checks {
            common(this@Pictures, at)
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
        override val passage: Content? = null,
        override val look: Look? = null,
        override val aside: Aside? = null,
        override val audio: String? = null,
        override val video: Block.Video? = null,
        override val generated: Boolean = false,
        val head: List<Inline> = emptyList(),
        val rows: List<List<Inline>>,
    ) : Exercise {
        override val needsKeyboard get() = true
        override val wordBank: List<String> get() = emptyList()
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
        override val passage: Content? = null,
        override val look: Look? = null,
        override val aside: Aside? = null,
        override val audio: String? = null,
        override val video: Block.Video? = null,
        override val generated: Boolean = false,
        /** Each pair is `[left, right]`. */
        val pairs: List<List<Side>>,
        val extra: List<Side> = emptyList(),
    ) : Exercise {
        override val needsKeyboard get() = false
        override val wordBank: List<String> get() = emptyList()
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
        override val passage: Content? = null,
        override val look: Look? = null,
        override val aside: Aside? = null,
        override val audio: String? = null,
        override val video: Block.Video? = null,
        override val generated: Boolean = false,
        val items: List<OrderItem>,
    ) : Exercise {
        override val needsKeyboard get() = false
        override val wordBank: List<String> get() = emptyList()
        override val itemCount get() = items.size

        override fun validate(at: Path) = checks {
            common(this@Order, at)
            require(items.isNotEmpty(), at / "items", "needs at least one item")
            items.forEachIndexed { i, item ->
                val itemAt = (at / "items")[i]
                if (item.parts.isNotEmpty()) {
                    require(item.answer.isEmpty(), itemAt, "has both \"answer\" and \"parts\"; give one of them")
                    require(item.parts.size >= 2, itemAt / "parts", "needs at least 2 parts")
                    item.parts.forEachIndexed { k, part -> notBlank(part, (itemAt / "parts")[k]) }
                    require(item.accept.isEmpty(), itemAt / "accept", "works only with \"answer\"")
                } else {
                    require(item.words.size >= 2, itemAt / "answer", "needs at least 2 words")
                }
                if (item.parts.isEmpty()) item.accept.forEachIndexed { a, other ->
                    val same = OrderItem(other)
                    require(same.words.sorted() == item.words.sorted() && same.ending == item.ending,
                        (itemAt / "accept")[a], "must use the same words and ending as the answer")
                }
                text(item.note, itemAt / "note")
            }
        }
    }

    /**
     * A sentence to build from its words ([answer]), or anything to put in order from its [parts] — whole phrases, the
     * lines of a dialogue — given in the right order. [accept] lists other right word orders of an [answer]. The
     * sentence's closing punctuation is not a tile: it stays fixed at the end, so "Is it raining now?" and "Is it now
     * raining?" are built from the same tiles; parts have no fixed end.
     */
    @Serializable
    data class OrderItem(
        val answer: String = "",
        val parts: List<String> = emptyList(),
        val accept: List<String> = emptyList(),
        val note: Inline? = null,
    ) {
        /** The tiles: the words of the answer, `["Is", "it", "raining", "now"]`, or the parts. */
        val words: List<String> get() = parts.ifEmpty { words(answer.trimEnd().dropLastWhile { it in ENDING }) }

        /** The fixed end of the sentence: `"?"`; none for parts. */
        val ending: String get() = if (parts.isNotEmpty()) "" else answer.trimEnd().takeLastWhile { it in ENDING }

        /** The right order as shown after a mistake: the answer, or the parts one under another. */
        val shown: String get() = if (parts.isNotEmpty()) parts.joinToString("\n") else answer
    }

    /**
     * Words to sort into groups: each group has a [SortGroup.title] and the words that belong to it. The words are shown
     * shuffled in one pile; the learner puts each into a group. Each word is one checked item ([entries]).
     */
    @Serializable
    @SerialName("sort")
    data class Sort(
        override val id: Uuid,
        override val instruction: Inline? = null,
        override val explanation: Content? = null,
        override val passage: Content? = null,
        override val look: Look? = null,
        override val aside: Aside? = null,
        override val audio: String? = null,
        override val video: Block.Video? = null,
        override val generated: Boolean = false,
        val groups: List<SortGroup>,
    ) : Exercise {
        override val needsKeyboard get() = false
        override val wordBank: List<String> get() = emptyList()
        override val itemCount get() = groups.sumOf { it.items.size }

        /** Every word with the index of its right group, group by group; [Response.Sort.placed] follows it. */
        val entries: List<SortEntry> get() = groups.flatMapIndexed { g, group -> group.items.map { SortEntry(it, g) } }

        override fun validate(at: Path) = checks {
            common(this@Sort, at)
            require(groups.size in 2..MAX_SORT_GROUPS, at / "groups", "needs 2 to $MAX_SORT_GROUPS groups")
            val seen = mutableSetOf<String>()
            groups.forEachIndexed { g, group ->
                val groupAt = (at / "groups")[g]
                text(group.title, groupAt / "title")
                require(group.items.isNotEmpty(), groupAt / "items", "needs at least one word")
                group.items.forEachIndexed { i, word ->
                    notBlank(word, (groupAt / "items")[i])
                    require(seen.add(word.trim().lowercase()), (groupAt / "items")[i], "\"$word\" is in two groups or twice in one")
                }
            }
            require(itemCount in 3..MAX_SORT_ITEMS, at / "groups", "needs 3 to $MAX_SORT_ITEMS words in all")
        }
    }

    @Serializable
    data class SortGroup(val title: Inline, val items: List<String>)

    /** One word of a [Sort] and the index of the group it belongs to. */
    data class SortEntry(val text: String, val group: Int)

    /**
     * Replies to write from notes. The item's one blank holds the model answer (variants allowed); the learner types the
     * whole answer, which is right when it has the key words of one of [ExpandItem.keys] in that order, with any words
     * between them. The notes to use are the [wordBank].
     */
    @Serializable
    @SerialName("expand")
    data class Expand(
        override val id: Uuid,
        override val instruction: Inline? = null,
        override val explanation: Content? = null,
        override val passage: Content? = null,
        override val look: Look? = null,
        override val aside: Aside? = null,
        override val audio: String? = null,
        override val video: Block.Video? = null,
        override val generated: Boolean = false,
        override val wordBank: List<String> = emptyList(),
        /** A solved item shown first, as "0" in a textbook. */
        val example: Inline? = null,
        val items: List<ExpandItem>,
    ) : Exercise {
        override val needsKeyboard get() = true
        override val itemCount get() = items.size

        override fun validate(at: Path) = checks {
            common(this@Expand, at)
            text(example, at / "example")
            require(items.isNotEmpty(), at / "items", "needs at least one item")
            items.forEachIndexed { i, item ->
                val itemAt = (at / "items")[i]
                text(item.text, itemAt / "text", BlankRule.EXACTLY_ONE, maxBlank = MAX_ANSWER_LENGTH)
                text(item.note, itemAt / "note")
                val keysAt = itemAt / "keys"
                require(item.keys.isNotEmpty(), keysAt, "needs at least one order of key words")
                item.keys.forEachIndexed { k, order ->
                    require(order.isNotEmpty(), keysAt[k], "needs at least one key word")
                    order.forEachIndexed { w, key -> notBlank(key, keysAt[k][w]) }
                    require(order.sorted() == item.keys.first().sorted(), keysAt[k], "must have the same key words as the first order, in another order")
                }
            }
        }
    }

    /** [keys] are the allowed orders of the key words, each a list of words or phrases in the forms the answer needs. */
    @Serializable
    data class ExpandItem(val text: Inline, val keys: List<List<String>>, val note: Inline? = null)

    /**
     * Texts with marked words, each right or wrong as it stands: `{{as it is}}` marks a right one,
     * `{{wrong => fix|other fix}}` a wrong one with its accepted fixes. Each mark starts as kept (✓); the learner keeps
     * it or types a fix. Each mark is one checked item; [marks] lists them in reading order.
     */
    @Serializable
    @SerialName("fix")
    data class Fix(
        override val id: Uuid,
        override val instruction: Inline? = null,
        override val explanation: Content? = null,
        override val passage: Content? = null,
        override val look: Look? = null,
        override val aside: Aside? = null,
        override val audio: String? = null,
        override val video: Block.Video? = null,
        override val generated: Boolean = false,
        val items: List<FixItem>,
    ) : Exercise {
        override val needsKeyboard get() = true
        override val wordBank: List<String> get() = emptyList()
        override val itemCount get() = marks.size

        /** Every mark in reading order; [Response.Blanks.values] follow it, an empty value meaning kept. */
        val marks: List<FixMark> get() = items.flatMap { it.marks }

        override fun validate(at: Path) = checks {
            common(this@Fix, at)
            require(items.isNotEmpty(), at / "items", "needs at least one item")
            items.forEachIndexed { i, item ->
                val itemAt = (at / "items")[i]
                text(item.text, itemAt / "text", BlankRule.AT_LEAST_ONE, maxBlank = null)
                text(item.note, itemAt / "note")
                for (blank in item.text.blanks) {
                    val written = blank.variants.joinToString("|")
                    val mark = FixMark.of(blank)
                    if (!mark.wrong) {
                        require(blank.variants.size == 1, itemAt / "text", "\"{{$written}}\": a wrong word is written {{wrong => fix|other fix}}")
                    } else {
                        require(mark.shown.isNotBlank(), itemAt / "text", "\"{{$written}}\" has nothing before =>")
                        require(mark.fixes.none { it.isBlank() }, itemAt / "text", "\"{{$written}}\" has an empty fix")
                        require(mark.fixes.none { it.equals(mark.shown, ignoreCase = true) }, itemAt / "text", "\"{{$written}}\": a fix must differ from the wrong word")
                    }
                    for (part in mark.fixes + mark.shown) {
                        require(part.length <= MAX_BLANK_LENGTH, itemAt / "text", "\"$part\" is longer than $MAX_BLANK_LENGTH characters")
                    }
                }
            }
        }
    }

    @Serializable
    data class FixItem(val text: Inline, val note: Inline? = null) {
        val marks: List<FixMark> get() = text.blanks.map(FixMark::of)
    }

    /** A marked word: [shown] in the text, and the accepted [fixes] if it is wrong (empty if it is right). */
    data class FixMark(val shown: String, val fixes: List<String>) {
        val wrong: Boolean get() = fixes.isNotEmpty()

        companion object {
            fun of(blank: InlineNode.Blank): FixMark {
                val first = blank.variants.first()
                val arrow = first.indexOf("=>")
                if (arrow < 0) return FixMark(first, emptyList())
                return FixMark(first.substring(0, arrow).trim(), listOf(first.substring(arrow + 2).trim()) + blank.variants.drop(1))
            }
        }
    }

    /** An exercise type from a newer format; it is not shown. */
    @Serializable(with = UnknownExerciseSerializer::class)
    data class Unknown(val raw: JsonObject) : Exercise {
        override val generated: Boolean get() = false
        val type: String get() = raw.typeName()
        override val look: Look? get() = null
        override val aside: Aside? get() = null
        override val wordBank: List<String> get() = emptyList()
        override val audio: String? get() = null
        override val video: Block.Video? get() = null
        override val needsKeyboard get() = false
        override val id: Uuid
            get() = (raw["id"] as? JsonPrimitive)?.content?.let { runCatching { Uuid.parse(it) }.getOrNull() } ?: Uuid.NIL
        override val instruction: Inline? get() = null
        override val explanation: Content? get() = null
        override val passage: Content? get() = null
        override val itemCount get() = 0
        override fun validate(at: Path) = checks { unknownType(at, "exercise type", type) }
    }
}

private const val ENDING = ".?!…。？！"

const val MAX_SORT_GROUPS = 6
const val MAX_SORT_ITEMS = 24

internal fun words(sentence: String): List<String> = sentence.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

/** The layers every type has: instruction, word bank, passage, look, aside and explanation. */
private fun Checks.common(exercise: Exercise, at: Path) {
    text(exercise.instruction, at / "instruction")
    exercise.wordBank.forEachIndexed { i, word -> notBlank(word, (at / "wordBank")[i]) }
    exercise.passage?.let { nested(it.validate(at / "passage")) }
    exercise.look?.let { nested(it.validate(at / "look")) }
    exercise.aside?.let { nested(it.validate(at / "aside")) }
    audio(exercise.audio, at / "audio")
    exercise.video?.let { nested(it.validate(at / "video")) }
    exercise.explanation?.let { nested(it.validate(at / "explanation")) }
}

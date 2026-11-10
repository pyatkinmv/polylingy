package com.polylingy.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.Uuid

/**
 * A page of free content: unit theory or an explanation after an exercise is checked.
 * Only [Blocks] exists for now. TODO: markdown and html formats, when a course needs them.
 */
@Serializable
@JsonClassDiscriminator("format")
sealed interface Content {
    fun validate(at: Path): List<Problem>

    @Serializable
    @SerialName("blocks")
    data class Blocks(val blocks: List<Block>) : Content {
        override fun validate(at: Path) = checks {
            require(blocks.isNotEmpty(), at / "blocks", "needs at least one block")
            blocks(blocks, at / "blocks")
        }
    }

    @Serializable(with = UnknownContentSerializer::class)
    data class Unknown(val raw: JsonObject) : Content {
        val format: String get() = raw.typeName("format")
        override fun validate(at: Path) = checks { unknownType(at, "content format", format) }
    }
}

/**
 * A building block of a content page. Blocks describe what the content *is*; the app decides how it is laid out,
 * so there are no sizes here.
 */
@Serializable
sealed interface Block {
    fun validate(at: Path): List<Problem>

    /**
     * A part of a page. With a [label] (a letter, "A") it is lettered: the letter in the margin, the content beside it,
     * as in many grammar books; without one, its [title] is a heading over the content, and the content takes the width.
     * [id] is set only on sections a `seeAlso` link points at; it is unique within the course.
     */
    @Serializable
    @SerialName("section")
    data class Section(val id: Uuid? = null, val label: String? = null, val title: Inline? = null, val blocks: List<Block>) : Block {
        override fun validate(at: Path) = checks {
            notBlank(label, at / "label")
            text(title, at / "title")
            require(blocks.isNotEmpty(), at / "blocks", "needs at least one block")
            blocks(blocks, at / "blocks")
        }
    }

    /** A paragraph. A [role] says what it is for (not how it looks): a lead is the introduction of the part it opens. */
    @Serializable
    @SerialName("text")
    data class Text(val text: Inline, val role: Role? = null) : Block {
        override fun validate(at: Path) = checks { text(text, at / "text") }

        @Serializable
        enum class Role {
            @SerialName("lead") LEAD,
        }
    }

    /**
     * An example situation, with an optional [aside] (usually a picture) next to it. A [look] says how it is set off from the text:
     * a soft card (the default) or no frame at all, for a picture that simply stands beside a paragraph.
     */
    @Serializable
    @SerialName("situation")
    data class Situation(val blocks: List<Block>, val aside: Block? = null, val look: Style? = null) : Block {
        override fun validate(at: Path) = checks {
            require(blocks.isNotEmpty(), at / "blocks", "needs at least one block")
            blocks(blocks, at / "blocks")
            aside?.let { nested(it.validate(at / "aside")) }
        }

        @Serializable
        enum class Style {
            @SerialName("card") CARD,
            @SerialName("plain") PLAIN,
        }
    }

    /** A picture. Its [size] is a word, not pixels (the same words as an exercise's aside): the app keeps it small enough not to fill a wide window. */
    @Serializable
    @SerialName("image")
    data class Picture(val src: String, val alt: String, val caption: Inline? = null, val size: Aside.Size? = null) : Block {
        val image: Image get() = Image(src, alt, caption)
        override fun validate(at: Path) = checks { image(src, alt, caption, at) }
    }

    @Serializable
    @SerialName("table")
    data class Table(val head: List<Inline> = emptyList(), val rows: List<List<Cell>>) : Block {
        override fun validate(at: Path) = checks {
            head.forEachIndexed { i, cell -> text(cell, (at / "head")[i], mayBeEmpty = true) }
            require(rows.isNotEmpty(), at / "rows", "needs at least one row")
            var width: Int? = head.size.takeIf { it > 0 }
            var spans = mapOf<Int, Int>() // column → rows below the current one it still covers
            rows.forEachIndexed { r, row ->
                val rowAt = (at / "rows")[r]
                val covered = spans.keys
                val next = spans.filterValues { it > 1 }.mapValues { it.value - 1 }.toMutableMap()
                var column = 0
                row.forEachIndexed { c, cell ->
                    while (column in covered) column++
                    text(cell.text, rowAt[c], mayBeEmpty = true)
                    audio(cell.audio, rowAt[c] / "audio")
                    require(cell.rowSpan >= 1, rowAt[c] / "rowSpan", "must be at least 1")
                    require(r + cell.rowSpan <= rows.size, rowAt[c] / "rowSpan", "goes past the last row")
                    if (cell.rowSpan > 1) next[column] = cell.rowSpan - 1
                    column++
                }
                val rowWidth = row.size + covered.size
                if (width == null) width = rowWidth
                require(rowWidth == width, rowAt, "has $rowWidth columns, expected $width")
                spans = next
            }
        }
    }

    /** A conversation: one line per remark, the speaker's name on the left, each line with its own optional sound. */
    @Serializable
    @SerialName("dialogue")
    data class Dialogue(val lines: List<Line>) : Block {
        override fun validate(at: Path) = checks {
            require(lines.isNotEmpty(), at / "lines", "needs at least one line")
            lines.forEachIndexed { i, line ->
                val lineAt = (at / "lines")[i]
                text(line.who, lineAt / "who")
                text(line.text, lineAt / "text")
                text(line.translation, lineAt / "translation")
                audio(line.audio, lineAt / "audio")
            }
        }
    }

    /**
     * One remark of a [Dialogue]: who says it, what, its sound, and its [translation] — shown small and grey under it.
     * The course decides where a translation helps; there is no setting to hide it.
     */
    @Serializable
    data class Line(val who: Inline? = null, val text: Inline, val audio: String? = null, val translation: Inline? = null)

    /**
     * A recording of its own, as a book prints it beside a section: one [audio] file with a [title] ("Track 1:03").
     * Long recordings play in the player docked at the bottom of the window, where they can be paused and rewound.
     */
    @Serializable
    @SerialName("track")
    data class Track(val title: Inline, val audio: String) : Block {
        override fun validate(at: Path) = checks {
            text(title, at / "title")
            audio(audio, at / "audio")
        }
    }

    /**
     * A video somewhere on the web, which the app opens in the browser: the course points to it, it does not carry it.
     * [url] is an `https://` address; [note] says what to do there.
     */
    @Serializable
    @SerialName("video")
    data class Video(val title: Inline, val url: String, val note: Inline? = null) : Block {
        override fun validate(at: Path) = checks {
            text(title, at / "title")
            text(note, at / "note")
            require(
                url.startsWith("https://") && url.length > "https://".length && url.none { it.isWhitespace() },
                at / "url", "must be an https:// address without spaces",
            )
        }
    }

    /** A line of speech in a bubble, with an optional [aside] picture of the speaker. */
    @Serializable
    @SerialName("quote")
    data class Quote(val text: Inline, val who: String? = null, val aside: Block? = null) : Block {
        override fun validate(at: Path) = checks {
            text(text, at / "text")
            notBlank(who, at / "who")
            aside?.let { nested(it.validate(at / "aside")) }
        }
    }

    @Serializable
    @SerialName("examples")
    data class Examples(val items: List<Example>, val numbered: Boolean = false) : Block {
        override fun validate(at: Path) = checks {
            require(items.isNotEmpty(), at / "items", "needs at least one example")
            items.forEachIndexed { i, item ->
                text(item.text, (at / "items")[i] / "text")
                text(item.not, (at / "items")[i] / "not")
                text(item.translation, (at / "items")[i] / "translation")
                audio(item.audio, (at / "items")[i] / "audio")
            }
        }
    }

    /**
     * One example; [not] is the wrong form to contrast with, shown as "(not …)"; [translation] is shown small and grey
     * under it; [audio] gives it a play button.
     */
    @Serializable
    data class Example(val text: Inline, val not: Inline? = null, val translation: Inline? = null, val audio: String? = null)

    @Serializable
    @SerialName("words")
    data class Words(val words: List<String>) : Block {
        override fun validate(at: Path) = checks {
            require(words.isNotEmpty(), at / "words", "needs at least one word")
            words.forEachIndexed { i, word -> notBlank(word, (at / "words")[i]) }
        }
    }

    /** A remark set apart from the text. [kind] says what sort of remark it is; the app gives each its icon and caption. */
    @Serializable
    @SerialName("tip")
    data class Tip(val blocks: List<Block>, val kind: Kind = Kind.TIP) : Block {
        @Serializable
        enum class Kind {
            @SerialName("tip") TIP,
            @SerialName("note") NOTE,
            @SerialName("rule") RULE,
            @SerialName("warning") WARNING,
        }

        override fun validate(at: Path) = checks {
            require(blocks.isNotEmpty(), at / "blocks", "needs at least one block")
            blocks(blocks, at / "blocks")
        }
    }

    @Serializable
    @SerialName("seeAlso")
    data class SeeAlso(val links: List<Link>) : Block {
        override fun validate(at: Path) = checks {
            require(links.isNotEmpty(), at / "links", "needs at least one link")
            links.forEachIndexed { i, link ->
                require(listOfNotNull(link.section, link.unit).size == 1, (at / "links")[i], "needs exactly one of \"section\" or \"unit\"")
            }
        }
    }

    /** A link to a theory section of any unit of the course ([section] = its id), or to the top of a unit's theory ([unit] = its id). */
    @Serializable
    data class Link(val section: Uuid? = null, val unit: Uuid? = null)

    @Serializable(with = UnknownBlockSerializer::class)
    data class Unknown(val raw: JsonObject) : Block {
        val type: String get() = raw.typeName()
        override fun validate(at: Path) = checks { unknownType(at, "block type", type) }
    }
}

internal fun Checks.blocks(blocks: List<Block>, at: Path) =
    blocks.forEachIndexed { i, block -> nested(block.validate(at[i])) }

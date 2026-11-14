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
     * a soft card (the default) or no frame at all, for a picture that simply stands beside a paragraph. A [frame] is the frame of an
     * exercise's text (an email, an article, a newspaper clipping, see [Look]) drawn around the situation: a letter in the theory.
     */
    @Serializable
    @SerialName("situation")
    data class Situation(val blocks: List<Block>, val aside: Block? = null, val look: Style? = null, val frame: Look? = null) : Block {
        override fun validate(at: Path) = checks {
            require(blocks.isNotEmpty(), at / "blocks", "needs at least one block")
            blocks(blocks, at / "blocks")
            aside?.let { nested(it.validate(at / "aside")) }
            frame?.let { nested(it.validate(at / "frame")) }
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
    data class Picture(
        val src: String, val alt: String, val caption: Inline? = null, val size: Aside.Size? = null,
        val description: String? = null, val made: String? = null,
    ) : Block {
        val image: Image get() = Image(src, alt, caption, description, made)
        override fun validate(at: Path) = checks { image(src, alt, caption, description, made, at) }
    }

    @Serializable
    @SerialName("table")
    data class Table(val head: List<Inline> = emptyList(), val rows: List<List<Cell>>, val appearance: Appearance? = null) : Block {
        /**
         * How the table is drawn. Every field is optional. With none of them (or without `appearance`) the table is as wide as its place,
         * in a soft card, with thin lines between the rows, a plain header and no highlighted columns.
         */
        @Serializable
        data class Appearance(
            /** `fullWidth` (default): the columns share the whole width of the page; `onlyAsWideAsContent`: every column is as wide as its text. */
            val widthOfTable: WidthOfTable = WidthOfTable.FULL_WIDTH,
            /** `card` (default): a soft card with an edge round the table; `none`: the table stands straight on the page. */
            val background: Background = Background.CARD,
            /** `plain` (default): header text in the accent colour over a line; `highlighted`: the header row is filled with the accent's soft tint. */
            val headerRow: HeaderRow = HeaderRow.PLAIN,
            /** Numbers of columns, the first is 0, drawn bold on a soft background: e.g. the word of each row. */
            val highlightedColumns: List<Int> = emptyList(),
            /** Every other body row on a soft background (default false). */
            val stripedRows: Boolean = false,
            /** Thin lines between the body rows (default true). */
            val linesBetweenRows: Boolean = true,
        ) {
            @Serializable
            enum class WidthOfTable {
                @SerialName("fullWidth") FULL_WIDTH,
                @SerialName("onlyAsWideAsContent") ONLY_AS_WIDE_AS_CONTENT,
            }

            @Serializable
            enum class Background {
                @SerialName("card") CARD,
                @SerialName("none") NONE,
            }

            @Serializable
            enum class HeaderRow {
                @SerialName("plain") PLAIN,
                @SerialName("highlighted") HIGHLIGHTED,
            }
        }

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
            val columns = width
            appearance?.highlightedColumns?.forEachIndexed { i, column ->
                require(column >= 0 && (columns == null || column < columns), (at / "appearance" / "highlightedColumns")[i], "must be the number of a column, counting from 0")
            }
            appearance?.highlightedColumns?.let { numbers ->
                require(numbers.size == numbers.toSet().size, at / "appearance" / "highlightedColumns", "a column may be named once")
            }
        }
    }

    /** A conversation: one line per remark, the speaker's name on the left, each line with its own optional sound. */
    @Serializable
    @SerialName("dialogue")
    data class Dialogue(val lines: List<Line>, val look: Style? = null, val portraits: List<Portrait> = emptyList()) : Block {
        /** Lines in a column with the names on the left (the default), or speech bubbles, the first speaker on the left, the second on the right. */
        @Serializable
        enum class Style {
            @SerialName("lines") LINES,
            @SerialName("bubbles") BUBBLES,
        }

        /** A speaker's picture: [who] is the name as written in the lines' `who`. */
        @Serializable
        data class Portrait(val who: String, val image: Image)

        override fun validate(at: Path) = checks {
            require(lines.isNotEmpty(), at / "lines", "needs at least one line")
            portraits.forEachIndexed { i, p ->
                val pAt = (at / "portraits")[i]
                require(p.who.isNotBlank(), pAt / "who", "must not be empty")
                nested(p.image.validate(pAt / "image"))
            }
            require(portraits.map { it.who }.let { it.size == it.toSet().size }, at / "portraits", "each speaker may have one picture")
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
    data class Tip(val blocks: List<Block>, val kind: Kind = Kind.TIP, val title: Inline? = null, val aside: Block? = null) : Block {
        @Serializable
        enum class Kind {
            @SerialName("tip") TIP,
            @SerialName("note") NOTE,
            @SerialName("rule") RULE,
            @SerialName("warning") WARNING,
            /** "Did you know?": a fact that is nice to know. */
            @SerialName("fact") FACT,
            /** A custom or a tradition: the culture behind the language. */
            @SerialName("culture") CULTURE,
        }

        override fun validate(at: Path) = checks {
            require(blocks.isNotEmpty(), at / "blocks", "needs at least one block")
            text(title, at / "title")
            blocks(blocks, at / "blocks")
            aside?.let { nested(it.validate(at / "aside")) }
        }
    }

    /** [columns] blocks side by side (2 to 4); on a narrow window they stand one under another. Any blocks may be put in it. */
    @Serializable
    @SerialName("grid")
    data class Grid(val columns: Int = 2, val blocks: List<Block>) : Block {
        override fun validate(at: Path) = checks {
            require(columns in 2..4, at / "columns", "must be 2, 3 or 4")
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

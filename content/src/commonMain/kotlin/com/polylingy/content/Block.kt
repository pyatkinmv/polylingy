package com.polylingy.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonObject

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

    /** A lettered part of a page: the letter in the margin, the content beside it. */
    @Serializable
    @SerialName("section")
    data class Section(val label: String, val title: Inline? = null, val blocks: List<Block>) : Block {
        override fun validate(at: Path) = checks {
            notBlank(label, at / "label")
            text(title, at / "title")
            require(blocks.isNotEmpty(), at / "blocks", "needs at least one block")
            blocks(blocks, at / "blocks")
        }
    }

    @Serializable
    @SerialName("text")
    data class Text(val text: Inline) : Block {
        override fun validate(at: Path) = checks { text(text, at / "text") }
    }

    /** A framed example situation, with an optional [aside] (usually a picture) next to it. */
    @Serializable
    @SerialName("situation")
    data class Situation(val blocks: List<Block>, val aside: Block? = null) : Block {
        override fun validate(at: Path) = checks {
            require(blocks.isNotEmpty(), at / "blocks", "needs at least one block")
            blocks(blocks, at / "blocks")
            aside?.let { nested(it.validate(at / "aside")) }
        }
    }

    @Serializable
    @SerialName("image")
    data class Picture(val src: String, val alt: String, val caption: Inline? = null) : Block {
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
    data class Examples(val items: List<Example>) : Block {
        override fun validate(at: Path) = checks {
            require(items.isNotEmpty(), at / "items", "needs at least one example")
            items.forEachIndexed { i, item ->
                text(item.text, (at / "items")[i] / "text")
                text(item.not, (at / "items")[i] / "not")
            }
        }
    }

    /** One example; [not] is the wrong form to contrast with, shown as "(not …)". */
    @Serializable
    data class Example(val text: Inline, val not: Inline? = null)

    @Serializable
    @SerialName("words")
    data class Words(val words: List<String>) : Block {
        override fun validate(at: Path) = checks {
            require(words.isNotEmpty(), at / "words", "needs at least one word")
            words.forEachIndexed { i, word -> notBlank(word, (at / "words")[i]) }
        }
    }

    @Serializable
    @SerialName("tip")
    data class Tip(val blocks: List<Block>) : Block {
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
                require(listOfNotNull(link.theory, link.unit).size == 1, (at / "links")[i], "needs exactly one of \"theory\" or \"unit\"")
                notBlank(link.theory, (at / "links")[i] / "theory")
                notBlank(link.unit, (at / "links")[i] / "unit")
            }
        }
    }

    /** A link to a theory section of the same unit ([theory] = its label) or to another unit ([unit] = its id). */
    @Serializable
    data class Link(val theory: String? = null, val unit: String? = null)

    @Serializable(with = UnknownBlockSerializer::class)
    data class Unknown(val raw: JsonObject) : Block {
        val type: String get() = raw.typeName()
        override fun validate(at: Path) = checks { unknownType(at, "block type", type) }
    }
}

internal fun Checks.blocks(blocks: List<Block>, at: Path) =
    blocks.forEachIndexed { i, block -> nested(block.validate(at[i])) }

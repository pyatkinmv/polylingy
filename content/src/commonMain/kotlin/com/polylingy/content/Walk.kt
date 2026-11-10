package com.polylingy.content

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.uuid.Uuid

/** Every block of this page, nested ones included, with its path. */
fun Content.walk(at: Path): List<Pair<Path, Block>> = when (this) {
    is Content.Blocks -> buildList { walk(blocks, at / "blocks") }
    is Content.Unknown -> emptyList()
}

private fun MutableList<Pair<Path, Block>>.walk(blocks: List<Block>, at: Path) {
    blocks.forEachIndexed { i, block -> walk(block, at[i]) }
}

private fun MutableList<Pair<Path, Block>>.walk(block: Block, at: Path) {
    add(at to block)
    when (block) {
        is Block.Section -> walk(block.blocks, at / "blocks")
        is Block.Situation -> {
            walk(block.blocks, at / "blocks")
            block.aside?.let { walk(it, at / "aside") }
        }
        is Block.Quote -> block.aside?.let { walk(it, at / "aside") }
        is Block.Tip -> walk(block.blocks, at / "blocks")
        is Block.Text, is Block.Picture, is Block.Table, is Block.Examples, is Block.Words, is Block.SeeAlso, is Block.Dialogue,
        is Block.Track, is Block.Video, is Block.Unknown -> {}
    }
}

/** Every picture on this page, with the path of its `src`. */
fun Content.images(at: Path): List<Pair<Path, Image>> =
    walk(at).mapNotNull { (path, block) -> (block as? Block.Picture)?.let { path / "src" to it.image } }

/** Every sound on this page — dialogue lines, table cells, examples and tracks — with its path. */
fun Content.audios(at: Path): List<Pair<Path, String>> = walk(at).flatMap { (path, block) ->
    when (block) {
        is Block.Dialogue -> block.lines.mapIndexedNotNull { i, line -> line.audio?.let { (path / "lines")[i] / "audio" to it } }
        is Block.Table -> block.rows.flatMapIndexed { r, row -> row.mapIndexedNotNull { c, cell -> cell.audio?.let { (path / "rows")[r][c] / "audio" to it } } }
        is Block.Examples -> block.items.mapIndexedNotNull { i, item -> item.audio?.let { (path / "items")[i] / "audio" to it } }
        is Block.Track -> listOf(path / "audio" to block.audio)
        else -> emptyList()
    }
}

/** Every sound of this exercise, its explanation included, with its path. */
fun Exercise.audios(at: Path): List<Pair<Path, String>> {
    val ofItems = (this as? Exercise.Choice)?.items.orEmpty()
        .mapIndexedNotNull { i, item -> item.audio?.let { (at / "items")[i] / "audio" to it } }
    return listOfNotNull(audio?.let { at / "audio" to it }) + ofItems + explanation?.audios(at / "explanation").orEmpty()
}

/** Every picture of this exercise, its explanation included, with the path of its `src`. */
fun Exercise.images(at: Path): List<Pair<Path, Image>> {
    val own = when (this) {
        is Exercise.Pictures -> items.mapIndexed { i, item -> (at / "items")[i] / "image" / "src" to item.image }
        is Exercise.Match -> buildList {
            pairs.forEachIndexed { p, pair ->
                pair.forEachIndexed { s, side -> if (side is Side.Picture) add((at / "pairs")[p][s] / "image" / "src" to side.image) }
            }
            extra.forEachIndexed { e, side -> if (side is Side.Picture) add((at / "extra")[e] / "image" / "src" to side.image) }
        }
        is Exercise.Gaps, is Exercise.Choice, is Exercise.Table, is Exercise.Order, is Exercise.Sort, is Exercise.Expand, is Exercise.Fix,
        is Exercise.Unknown -> emptyList()
    }
    val aside = aside?.let { listOf(at / "aside" / "image" / "src" to it.image) }.orEmpty()
    return aside + own + explanation?.images(at / "explanation").orEmpty()
}

/** The same exercise under another id, e.g. when an imported course becomes a copy. */
fun Exercise.withId(id: Uuid): Exercise = when (this) {
    is Exercise.Gaps -> copy(id = id)
    is Exercise.Choice -> copy(id = id)
    is Exercise.Pictures -> copy(id = id)
    is Exercise.Table -> copy(id = id)
    is Exercise.Match -> copy(id = id)
    is Exercise.Order -> copy(id = id)
    is Exercise.Sort -> copy(id = id)
    is Exercise.Expand -> copy(id = id)
    is Exercise.Fix -> copy(id = id)
    is Exercise.Unknown -> copy(raw = JsonObject(raw + ("id" to JsonPrimitive(id.toString()))))
}

/** This page with every `seeAlso` link passed through [transform], nested ones included. Unknown content stays as it is. */
fun Content.mapLinks(transform: (Block.Link) -> Block.Link): Content = when (this) {
    is Content.Blocks -> copy(blocks = blocks.map { it.mapLinks(transform) })
    is Content.Unknown -> this
}

private fun Block.mapLinks(transform: (Block.Link) -> Block.Link): Block = when (this) {
    is Block.Section -> copy(blocks = blocks.map { it.mapLinks(transform) })
    is Block.Situation -> copy(blocks = blocks.map { it.mapLinks(transform) }, aside = aside?.mapLinks(transform))
    is Block.Quote -> copy(aside = aside?.mapLinks(transform))
    is Block.Tip -> copy(blocks = blocks.map { it.mapLinks(transform) })
    is Block.SeeAlso -> copy(links = links.map(transform))
    is Block.Text, is Block.Picture, is Block.Table, is Block.Examples, is Block.Words, is Block.Dialogue, is Block.Track, is Block.Video, is Block.Unknown -> this
}

/** This exercise with the links of its explanation passed through [transform]. */
fun Exercise.mapLinks(transform: (Block.Link) -> Block.Link): Exercise = when (this) {
    is Exercise.Gaps -> copy(explanation = explanation?.mapLinks(transform))
    is Exercise.Choice -> copy(explanation = explanation?.mapLinks(transform))
    is Exercise.Pictures -> copy(explanation = explanation?.mapLinks(transform))
    is Exercise.Table -> copy(explanation = explanation?.mapLinks(transform))
    is Exercise.Match -> copy(explanation = explanation?.mapLinks(transform))
    is Exercise.Order -> copy(explanation = explanation?.mapLinks(transform))
    is Exercise.Sort -> copy(explanation = explanation?.mapLinks(transform))
    is Exercise.Expand -> copy(explanation = explanation?.mapLinks(transform))
    is Exercise.Fix -> copy(explanation = explanation?.mapLinks(transform))
    is Exercise.Unknown -> this
}

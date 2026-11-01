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
        is Block.Text, is Block.Picture, is Block.Table, is Block.Examples, is Block.Words, is Block.SeeAlso, is Block.Unknown -> {}
    }
}

/** Every picture on this page, with the path of its `src`. */
fun Content.images(at: Path): List<Pair<Path, Image>> =
    walk(at).mapNotNull { (path, block) -> (block as? Block.Picture)?.let { path / "src" to it.image } }

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
        is Exercise.Gaps, is Exercise.Choice, is Exercise.Table, is Exercise.Order, is Exercise.Unknown -> emptyList()
    }
    return own + explanation?.images(at / "explanation").orEmpty()
}

/** The same exercise under another id, e.g. when an imported course becomes a copy. */
fun Exercise.withId(id: Uuid): Exercise = when (this) {
    is Exercise.Gaps -> copy(id = id)
    is Exercise.Choice -> copy(id = id)
    is Exercise.Pictures -> copy(id = id)
    is Exercise.Table -> copy(id = id)
    is Exercise.Match -> copy(id = id)
    is Exercise.Order -> copy(id = id)
    is Exercise.Unknown -> copy(raw = JsonObject(raw + ("id" to JsonPrimitive(id.toString()))))
}

package com.polylingy.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class WalkTest {
    private val theory = ContentJson.decode(Content.serializer(), Samples.theory)

    @Test fun walkVisitsNestedBlocksInOrder() {
        val types = theory.walk(Path("theory")).map { (path, block) -> "$path ${block::class.simpleName}" }
        assertEquals(
            listOf(
                "theory.blocks[0] Section",
                "theory.blocks[0].blocks[0] Text",
                "theory.blocks[0].blocks[1] Situation",
                "theory.blocks[0].blocks[1].blocks[0] Text",
                "theory.blocks[0].blocks[1].blocks[1] Table",
                "theory.blocks[0].blocks[1].aside Picture",
                "theory.blocks[1] Section",
                "theory.blocks[1].blocks[0] Examples",
                "theory.blocks[1].blocks[1] Quote",
                "theory.blocks[1].blocks[1].aside Picture",
                "theory.blocks[2] Section",
                "theory.blocks[2].blocks[0] Words",
                "theory.blocks[2].blocks[1] Table",
                "theory.blocks[2].blocks[2] Tip",
                "theory.blocks[2].blocks[2].blocks[0] Text",
                "theory.blocks[2].blocks[3] SeeAlso",
                "theory.blocks[3] Section",
                "theory.blocks[3].blocks[0] Dialogue",
                "theory.blocks[3].blocks[1] Table",
                "theory.blocks[4] Section",
                "theory.blocks[4].blocks[0] Track",
                "theory.blocks[4].blocks[1] Video",
            ),
            types,
        )
    }

    @Test fun unknownContentHasNoBlocks() =
        assertEquals(emptyList(), ContentJson.decode(Content.serializer(), """{ "format": "markdown" }""").walk(Path.ROOT))

    @Test fun theoryImages() = assertEquals(
        listOf("theory.blocks[0].blocks[1].aside.src" to "images/u1-cycling.png", "theory.blocks[1].blocks[1].aside.src" to "images/maria.png"),
        theory.images(Path("theory")).map { (path, image) -> path.toString() to image.src },
    )

    @Test fun exerciseImages() {
        fun images(json: String) = ContentJson.decode(Exercise.serializer(), json).images(Path("e")).map { (path, image) -> "$path ${image.src}" }
        assertEquals(listOf("e.items[0].image.src images/1-cook.png", "e.items[1].image.src https://img.polylingy.app/en/swim.png"), images(Samples.pictures))
        assertEquals(listOf("e.pairs[2][0].image.src images/dan.png"), images(Samples.match))
        assertEquals(
            listOf("e.extra[0].image.src x.png", "e.explanation.blocks[0].src y.png"),
            images("""{ "id": "${Samples.ID}", "type": "match", "pairs": [["a", "b"], ["c", "d"]],
                "extra": [{ "image": { "src": "x.png", "alt": "X" } }],
                "explanation": { "format": "blocks", "blocks": [{ "type": "image", "src": "y.png", "alt": "Y" }] } }"""),
        )
        assertEquals(emptyList(), images(Samples.gaps))
    }

    @Test fun withIdKeepsEverythingElse() {
        val id = Uuid.random()
        for ((name, json) in Samples.exercises + ("unknown" to """{ "id": "${Samples.ID}", "type": "dictation", "audio": "a" }""")) {
            val exercise = ContentJson.decode(Exercise.serializer(), json)
            val renamed = exercise.withId(id)
            assertEquals(id, renamed.id, name)
            assertEquals(exercise::class, renamed::class, name)
            assertEquals(exercise.itemCount, renamed.itemCount, name)
        }
    }
}

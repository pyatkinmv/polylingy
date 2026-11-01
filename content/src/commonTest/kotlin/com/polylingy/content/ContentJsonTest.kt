package com.polylingy.content

import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.uuid.Uuid

class ContentJsonTest {
    private fun exercise(json: String, lenient: Boolean = false) = ContentJson.decode(Exercise.serializer(), json, lenient)

    private fun failure(json: String): Problem = assertFailsWith<ContentFormatException> { exercise(json) }.problem

    @Test fun everySampleSurvivesARoundTrip() {
        for ((name, json) in Samples.exercises) {
            val first = exercise(json)
            val written = ContentJson.encode(Exercise.serializer(), first)
            assertEquals(first, exercise(written), name)
            assertEquals(written, ContentJson.encode(Exercise.serializer(), exercise(written)), name)
        }
        val theory = ContentJson.decode(Content.serializer(), Samples.theory)
        assertEquals(theory, ContentJson.decode(Content.serializer(), ContentJson.encode(Content.serializer(), theory)))
    }

    @Test fun typeDecidesTheClass() {
        assertIs<Exercise.Gaps>(exercise(Samples.gaps))
        assertIs<Exercise.Choice>(exercise(Samples.choice))
        assertIs<Exercise.Pictures>(exercise(Samples.pictures))
        assertIs<Exercise.Table>(exercise(Samples.table))
        assertIs<Exercise.Match>(exercise(Samples.match))
        assertIs<Exercise.Order>(exercise(Samples.order))
    }

    @Test fun fieldsAreRead() {
        val gaps = exercise(Samples.gaps) as Exercise.Gaps
        assertEquals(Uuid.parse(Samples.ID), gaps.id)
        assertEquals("Complete the sentences. Use the present continuous.", gaps.instruction?.text)
        assertEquals(listOf("sing", "wear", "get"), gaps.wordBank)
        assertEquals("not / read", gaps.items[2].hint)
        assertEquals(listOf("’m not reading", "am not reading"), gaps.items[2].text.blanks.single().variants)
        assertIs<Content.Blocks>(gaps.explanation)
        val match = exercise(Samples.match) as Exercise.Match
        assertEquals(Side.Picture(Image("images/dan.png", "Dan in the shower")), match.pairs[2][0])
    }

    @Test fun anUnknownFieldIsAnErrorWithItsPath() {
        val problem = failure("""{ "id": "${Samples.ID}", "type": "choice", "items": [{ "text": "{{a}}", "optoins": ["a", "b"] }] }""")
        assertEquals("items[0]", problem.path.text)
        assertEquals("Encountered an unknown key 'optoins'", problem.message)
    }

    @Test fun aMissingFieldIsAnError() {
        val problem = failure("""{ "id": "${Samples.ID}", "type": "gaps", "items": [{ "hint": "x" }] }""")
        assertEquals("items[0]", problem.path.text)
        assertEquals("Field 'text' is required for type with serial name 'com.polylingy.content.Exercise.GapItem', but it was missing", problem.message)
    }

    @Test fun aWrongValueIsAnError() {
        val problem = failure("""{ "id": "${Samples.ID}", "type": "words", "words": 5 }""".replace("\"words\", \"words\"", "\"order\", \"items\""))
        assertEquals("items", problem.path.text)
    }

    @Test fun notJsonAtAll() {
        val problem = assertFailsWith<ContentFormatException> { ContentJson.decode(Block.serializer(), "{ nope") }.problem
        assertEquals(Path.ROOT, problem.path)
    }

    @Test fun anUnknownTypeIsReadAsUnknown() {
        val unknown = assertIs<Exercise.Unknown>(exercise("""{ "id": "${Samples.ID}", "type": "dictation", "audio": "a.mp3" }"""))
        assertEquals("dictation", unknown.type)
        assertEquals(Uuid.parse(Samples.ID), unknown.id)
        assertEquals(null, unknown.instruction)
        assertEquals(null, unknown.explanation)
        assertEquals(0, unknown.itemCount)
        assertEquals(Uuid.NIL, assertIs<Exercise.Unknown>(exercise("""{ "type": "dictation", "id": "nope" }""")).id)
        val block = assertIs<Block.Unknown>(ContentJson.decode(Block.serializer(), """{ "type": "video", "src": "x" }"""))
        assertEquals("video", block.type)
        val content = assertIs<Content.Unknown>(ContentJson.decode(Content.serializer(), """{ "format": "markdown", "text": "# A" }"""))
        assertEquals("markdown", content.format)
    }

    @Test fun unknownContentIsNeverWritten() {
        val block = ContentJson.decode(Block.serializer(), """{ "type": "video", "src": "x" }""")
        assertFailsWith<SerializationException> { ContentJson.encode(Block.serializer(), block) }
        val exercise = exercise("""{ "id": "${Samples.ID}", "type": "dictation" }""")
        assertFailsWith<SerializationException> { ContentJson.encode(Exercise.serializer(), exercise) }
        val content = ContentJson.decode(Content.serializer(), """{ "format": "markdown" }""")
        assertFailsWith<SerializationException> { ContentJson.encode(Content.serializer(), content) }
    }

    @Test fun lenientModeSkipsUnknownFields() {
        val json = """{ "id": "${Samples.ID}", "type": "choice", "level": "B1",
            "items": [{ "text": "{{a}}", "options": ["a", "b"], "audio": "x.mp3" }] }"""
        val choice = assertIs<Exercise.Choice>(exercise(json, lenient = true))
        assertEquals(listOf("a", "b"), choice.items.single().options)
    }

    @Test fun sidesAndCellsAcceptTextOrObject() {
        assertEquals(
            listOf(Side.Text(Inline("a")), Side.Picture(Image("b.png", "B"))),
            ContentJson.decode(ListSerializer(Side.serializer()), """["a", { "image": { "src": "b.png", "alt": "B" } }]"""),
        )
        assertEquals(
            listOf(Cell(Inline("a")), Cell(Inline("b"), rowSpan = 2), Cell(Inline("c"))),
            ContentJson.decode(ListSerializer(Cell.serializer()), """["a", { "text": "b", "rowSpan": 2 }, { "text": "c" }]"""),
        )
        assertEquals("""["a",{"text":"b","rowSpan":2}]""",
            ContentJson.strict.encodeToString(ListSerializer(Cell.serializer()), listOf(Cell(Inline("a")), Cell(Inline("b"), 2))))
    }

    @Test fun malformedSidesAndCellsAreErrors() {
        for (bad in listOf("""[5]""", """[{ "picture": {} }]""")) {
            assertFailsWith<ContentFormatException>(bad) { ContentJson.decode(ListSerializer(Side.serializer()), bad) }
        }
        for (bad in listOf("""[5]""", """[{ "text": "a", "span": 2 }]""", """[{ "rowSpan": 2 }]""", """[{ "text": "a", "rowSpan": "x" }]""")) {
            assertFailsWith<ContentFormatException>(bad) { ContentJson.decode(ListSerializer(Cell.serializer()), bad) }
        }
    }

    @Test fun responsesSurviveARoundTrip() {
        val responses = listOf(
            Response.Blanks(listOf("is singing", "")),
            Response.Choice(listOf("rains", null)),
            Response.Match(listOf(2, 0, null)),
            Response.Order(listOf(listOf("What", "are"), emptyList())),
        )
        val serializer = ListSerializer(Response.serializer())
        val written = ContentJson.strict.encodeToString(serializer, responses)
        assertEquals(responses, ContentJson.strict.decodeFromString(serializer, written))
        assertEquals(listOf(ItemResult.CORRECT, ItemResult.TYPO, ItemResult.WRONG),
            ContentJson.strict.decodeFromString(ListSerializer(ItemResult.serializer()), """["CORRECT","TYPO","WRONG"]"""))
    }

    @Test fun pathsPrintRelativeToTheRoot() {
        assertEquals("$", Path.ROOT.toString())
        assertEquals("units[0].theory", (Path.ROOT / "units")[0].div("theory").toString())
        assertEquals("theory: x", Problem(Path("theory"), "x").toString())
    }
}

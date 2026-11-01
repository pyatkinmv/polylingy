package com.polylingy.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValidationTest {
    private val at = Path("exercises[0]")

    private fun exercise(json: String) = ContentJson.decode(Exercise.serializer(), json)
    private fun problemsOf(json: String) = exercise(json).validate(at).map { it.toString() }
    private fun blockProblems(json: String) = ContentJson.decode(Block.serializer(), json).validate(Path("blocks[0]")).map { it.toString() }

    /** Asserts the exercise has exactly [expected] problems. */
    private fun assertProblems(json: String, vararg expected: String) = assertEquals(expected.toList(), problemsOf(json), json)

    private fun ex(type: String, body: String) = """{ "id": "${Samples.ID}", "type": "$type", $body }"""

    @Test fun allSamplesAreValid() {
        for ((name, json) in Samples.exercises) assertEquals(emptyList(), problemsOf(json), name)
        assertEquals(emptyList(), ContentJson.decode(Content.serializer(), Samples.theory).validate(Path("theory")))
    }

    @Test fun itemCounts() {
        val counts = Samples.exercises.mapValues { exercise(it.value).itemCount }
        assertEquals(mapOf("gaps" to 4, "choice" to 2, "pictures" to 2, "table" to 4, "match" to 3, "order" to 2), counts)
    }

    // gaps

    @Test fun gapsNeedItemsAndBlanks() {
        assertProblems(ex("gaps", """"items": []"""), "exercises[0].items: needs at least one item")
        assertProblems(ex("gaps", """"items": [{ "text": "No blank here." }]"""),
            "exercises[0].items[0].text: needs at least one blank {{…}}")
    }

    @Test fun anAnswerFitsTheBlank() {
        val fits = "a".repeat(MAX_BLANK_LENGTH)
        assertProblems(ex("gaps", """"items": [{ "text": "{{$fits}}" }]"""))
        assertProblems(ex("table", """"rows": [["x", "{{short|${fits}b}}"]]"""),
            "exercises[0].rows[0][1]: answer \"${fits}b\" is longer than 40 characters")
    }

    @Test fun gapsCheckMarkupHintNoteExampleAndWordBank() = assertProblems(
        ex("gaps", """"example": "I {{am}}", "wordBank": [" "],
            "items": [{ "text": "a **{{b}}", "hint": "", "note": "{{x}}" }]"""),
        "exercises[0].example: blanks {{…}} are only allowed in exercises",
        "exercises[0].wordBank[0]: must not be empty",
        "exercises[0].items[0].text: ** at 3 is not closed",
        "exercises[0].items[0].hint: must not be empty",
        "exercises[0].items[0].note: blanks {{…}} are only allowed in exercises",
    )

    @Test fun instructionAndExplanationAreChecked() = assertProblems(
        ex("gaps", """"instruction": "", "explanation": { "format": "blocks", "blocks": [] }, "items": [{ "text": "{{a}}" }]"""),
        "exercises[0].instruction: must not be empty",
        "exercises[0].explanation.blocks: needs at least one block",
    )

    // choice

    @Test fun choiceRules() {
        assertProblems(ex("choice", """"items": []"""), "exercises[0].items: needs at least one item")
        assertProblems(ex("choice", """"items": [{ "text": "{{a}} {{b}}", "options": ["a", "b"] }]"""),
            "exercises[0].items[0].text: needs exactly one blank {{…}}, found 2")
        assertProblems(ex("choice", """"items": [{ "text": "{{a}}", "options": ["a"] }]"""),
            "exercises[0].items[0].options: needs at least 2 options")
        assertProblems(ex("choice", """"items": [{ "text": "{{a}}", "options": ["a", "a", ""] }]"""),
            "exercises[0].items[0].options: has the same option twice",
            "exercises[0].items[0].options[2]: must not be empty")
        assertProblems(ex("choice", """"items": [{ "text": "{{a|b}}", "options": ["a", "c"] }]"""),
            "exercises[0].items[0].text: a choice has one right answer, not variants")
        assertProblems(ex("choice", """"items": [{ "text": "{{a}}", "options": ["b", "c"], "note": "" }]"""),
            "exercises[0].items[0].note: must not be empty",
            "exercises[0].items[0].options: must include the right answer \"a\"")
    }

    // pictures

    @Test fun picturesRules() {
        assertProblems(ex("pictures", """"items": []"""), "exercises[0].items: needs at least one item")
        assertProblems(ex("pictures", """"wordBank": [""], "items": [{ "image": { "src": "../x.png", "alt": "" }, "text": "x", "note": "{{y}}" }]"""),
            "exercises[0].wordBank[0]: must not be empty",
            "exercises[0].items[0].image.src: must be an https:// URL or a relative path inside the course archive, like images/cat.png",
            "exercises[0].items[0].image.alt: must not be empty: describe the picture",
            "exercises[0].items[0].text: needs at least one blank {{…}}",
            "exercises[0].items[0].note: blanks {{…}} are only allowed in exercises")
    }

    @Test fun imageSources() {
        fun srcProblems(src: String) = Image(src, "alt").validate(Path("image")).map { it.message }
        for (ok in listOf("images/a.png", "a.png", "images/sub/a.png", "https://x.org/a.png")) assertEquals(emptyList(), srcProblems(ok), ok)
        assertEquals(listOf("a URL must start with https://"), srcProblems("http://x.org/a.png"))
        assertEquals(listOf("must not be empty"), srcProblems(" "))
        for (bad in listOf("/abs.png", "C:/a.png", "images\\a.png", "images//a.png", "../a.png")) assertEquals(1, srcProblems(bad).size, bad)
        assertEquals(listOf("image.caption: must not be empty"), Image("a.png", "alt", Inline("")).validate(Path("image")).map { it.toString() })
    }

    // table

    @Test fun tableRules() {
        assertProblems(ex("table", """"rows": []"""), "exercises[0].rows: needs at least one row")
        assertProblems(ex("table", """"rows": [["a", "b"]]"""), "exercises[0].rows: needs at least one blank {{…}}")
        assertProblems(ex("table", """"head": ["x", "{{y}}"], "rows": [["{{a}}"], ["b", "c"]]"""),
            "exercises[0].head[1]: blanks {{…}} are only allowed in exercises",
            "exercises[0].rows[0]: has 1 cells, expected 2")
        assertProblems(ex("table", """"rows": [["{{a}}", "b"], ["c"]]"""), "exercises[0].rows[1]: has 1 cells, expected 2")
    }

    // match

    @Test fun matchRules() {
        assertProblems(ex("match", """"pairs": [["a", "b"]]"""), "exercises[0].pairs: needs at least 2 pairs")
        assertProblems(ex("match", """"pairs": [["a", "b"], ["c"]]"""), "exercises[0].pairs[1]: a pair is [left, right], found 1 sides")
        assertProblems(ex("match", """"pairs": [["a", "b"], ["a", "c"]], "extra": ["b"]"""),
            "exercises[0].pairs: has the same left side twice",
            "exercises[0].pairs: has the same right side twice (pairs and extra)")
        assertProblems(ex("match", """"pairs": [["", "{{b}}"], [{ "image": { "src": "x.png", "alt": "" } }, "c"]], "extra": [""]"""),
            "exercises[0].pairs[0][0]: must not be empty",
            "exercises[0].pairs[0][1]: blanks {{…}} are only allowed in exercises",
            "exercises[0].pairs[1][0].image.alt: must not be empty: describe the picture",
            "exercises[0].extra[0]: must not be empty")
    }

    @Test fun matchRightSidesArePairsThenExtra() {
        val match = exercise(Samples.match) as Exercise.Match
        assertEquals(
            listOf("so I’m going to bed.", "The baby is sleeping.", "He’s having a shower.", "It rains a lot here."),
            match.rightSides.map { (it as Side.Text).text.text },
        )
    }

    // order

    @Test fun orderRules() {
        assertProblems(ex("order", """"items": []"""), "exercises[0].items: needs at least one item")
        assertProblems(ex("order", """"items": [{ "answer": "Hello", "note": "" }]"""),
            "exercises[0].items[0].answer: needs at least 2 words",
            "exercises[0].items[0].note: must not be empty")
        assertProblems(ex("order", """"items": [{ "answer": "Is it raining?", "accept": ["it Is raining?", "Is it snowing?", "Is it raining."] }]"""),
            "exercises[0].items[0].accept[1]: must use the same words and ending as the answer",
            "exercises[0].items[0].accept[2]: must use the same words and ending as the answer")
    }

    @Test fun orderTilesLeaveTheEndingOut() {
        val item = Exercise.OrderItem("  Is it,   raining now?! ")
        assertEquals(listOf("Is", "it,", "raining", "now"), item.words)
        assertEquals("?!", item.ending)
        assertEquals("", Exercise.OrderItem("no ending here").ending)
    }

    // blocks

    @Test fun blockRules() {
        assertEquals(listOf("blocks[0].label: must not be empty", "blocks[0].title: must not be empty", "blocks[0].blocks: needs at least one block"),
            blockProblems("""{ "type": "section", "label": " ", "title": "", "blocks": [] }"""))
        assertEquals(listOf("blocks[0].text: blanks {{…}} are only allowed in exercises"),
            blockProblems("""{ "type": "text", "text": "I {{am}}" }"""))
        assertEquals(listOf("blocks[0].blocks: needs at least one block", "blocks[0].aside.alt: must not be empty: describe the picture"),
            blockProblems("""{ "type": "situation", "blocks": [], "aside": { "type": "image", "src": "a.png", "alt": "" } }"""))
        assertEquals(listOf("blocks[0].text: must not be empty", "blocks[0].who: must not be empty", "blocks[0].aside.text: must not be empty"),
            blockProblems("""{ "type": "quote", "text": "", "who": "", "aside": { "type": "text", "text": "" } }"""))
        assertEquals(listOf("blocks[0].items: needs at least one example"), blockProblems("""{ "type": "examples", "items": [] }"""))
        assertEquals(listOf("blocks[0].items[0].not: must not be empty"),
            blockProblems("""{ "type": "examples", "items": [{ "text": "a", "not": "" }] }"""))
        assertEquals(listOf("blocks[0].words: needs at least one word"), blockProblems("""{ "type": "words", "words": [] }"""))
        assertEquals(listOf("blocks[0].words[1]: must not be empty"), blockProblems("""{ "type": "words", "words": ["a", ""] }"""))
        assertEquals(listOf("blocks[0].blocks: needs at least one block"), blockProblems("""{ "type": "tip", "blocks": [] }"""))
        assertEquals(listOf("blocks[0].blocks[0].text: must not be empty"),
            blockProblems("""{ "type": "tip", "blocks": [{ "type": "text", "text": "" }] }"""))
    }

    @Test fun seeAlsoRules() {
        assertEquals(listOf("blocks[0].links: needs at least one link"), blockProblems("""{ "type": "seeAlso", "links": [] }"""))
        assertEquals(listOf("blocks[0].links[0]: needs exactly one of \"section\" or \"unit\""),
            blockProblems("""{ "type": "seeAlso", "links": [{}] }"""))
        assertEquals(listOf("blocks[0].links[0]: needs exactly one of \"section\" or \"unit\""),
            blockProblems("""{ "type": "seeAlso", "links": [{ "section": "${Samples.SECTION_A}", "unit": "${Samples.ID}" }] }"""))
    }

    @Test fun theoryTableRules() {
        assertEquals(listOf("blocks[0].rows: needs at least one row"), blockProblems("""{ "type": "table", "rows": [] }"""))
        assertEquals(listOf("blocks[0].rows[1]: has 1 columns, expected 2"),
            blockProblems("""{ "type": "table", "rows": [["a", "b"], ["c"]] }"""))
        assertEquals(listOf("blocks[0].rows[0]: has 1 columns, expected 2"),
            blockProblems("""{ "type": "table", "head": ["x", "y"], "rows": [["a"]] }"""))
        assertEquals(listOf("blocks[0].rows[0][1].rowSpan: goes past the last row"),
            blockProblems("""{ "type": "table", "rows": [["a", { "text": "b", "rowSpan": 3 }], ["c"]] }"""))
        assertEquals(listOf("blocks[0].rows[0][0].rowSpan: must be at least 1"),
            blockProblems("""{ "type": "table", "rows": [[{ "text": "a", "rowSpan": 0 }]] }"""))
        assertEquals(listOf("blocks[0].head[0]: blanks {{…}} are only allowed in exercises"),
            blockProblems("""{ "type": "table", "head": ["{{x}}"], "rows": [["a"]] }"""))
    }

    @Test fun rowSpanInTheMiddleColumn() = assertEquals(
        emptyList(),
        blockProblems("""{ "type": "table", "rows": [["a", { "text": "b", "rowSpan": 2 }, "c"], ["d", "e"], ["f", "g", "h"]] }"""),
    )

    @Test fun unknownTypesAreReportedAsUnknown() {
        val problems = ContentJson.decode(Content.serializer(), """{ "format": "blocks", "blocks": [{ "type": "video", "src": "x" }] }""")
            .validate(Path("theory"))
        assertEquals(listOf(Problem(Path("theory.blocks[0]"), "unknown block type \"video\"", Problem.Kind.UNKNOWN_TYPE)), problems)
        assertTrue(ContentJson.decode(Content.serializer(), """{ "format": "markdown", "text": "# A" }""").validate(Path("theory"))
            .single().kind == Problem.Kind.UNKNOWN_TYPE)
        assertEquals(listOf("exercises[0]: unknown exercise type \"dictation\""),
            problemsOf("""{ "id": "${Samples.ID}", "type": "dictation", "audio": "a.mp3" }"""))
    }
}

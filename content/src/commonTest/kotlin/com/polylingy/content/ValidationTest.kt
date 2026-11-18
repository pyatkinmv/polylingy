package com.polylingy.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertEquals(mapOf("gaps" to 4, "choice" to 2, "pictures" to 2, "table" to 4, "match" to 3, "order" to 2,
            "choiceInText" to 2, "expand" to 2, "fix" to 4, "layers" to 2, "article" to 2, "listen" to 1, "annotatedOrder" to 1, "orderParts" to 1, "newspaper" to 2, "watch" to 1, "passage" to 1), counts)
    }

    @Test fun orderOfParts() {
        val item = (exercise(Samples.orderParts) as Exercise.Order).items.single()
        assertEquals(3, item.words.size)
        assertEquals("", item.ending)
        assertEquals(item.parts.joinToString("\n"), item.shown)
        assertProblems(ex("order", """"items": [{ "answer": "a b", "parts": ["a", "b"] }]"""),
            "exercises[0].items[0]: has both \"answer\" and \"parts\"; give one of them")
        assertProblems(ex("order", """"items": [{ "parts": ["a"], "accept": ["x y"] }]"""),
            "exercises[0].items[0].parts: needs at least 2 parts", "exercises[0].items[0].accept: works only with \"answer\"")
    }

    @Test fun everyTypeSaysWhetherItNeedsAKeyboard() {
        val typing = Samples.ofEachType.map { exercise(it) }.associate { it::class.simpleName.orEmpty() to it.needsKeyboard }
        assertEquals(mapOf("Gaps" to true, "Choice" to false, "Pictures" to true, "Table" to true, "Match" to false, "Order" to false, "Sort" to false,
            "Expand" to true, "Fix" to true), typing)
    }

    // sounds

    @Test fun soundsAreFilesOrUrls() {
        assertProblems(ex("choice", """"audio": "a.ogg", "items": [{ "text": "{{a|b}}" }]"""), "exercises[0].audio: must be an .mp3 or .wav file")
        assertProblems(ex("choice", """"audio": "../a.mp3", "items": [{ "text": "{{a|b}}" }]"""),
            "exercises[0].audio: must be an https:// URL or a relative path inside the course archive, like audio/hello.mp3")
        assertProblems(ex("choice", """"audio": "http://x.org/a.mp3", "items": [{ "text": "{{a|b}}" }]"""), "exercises[0].audio: a URL must start with https://")
        assertProblems(ex("choice", """"audio": " ", "items": [{ "text": "{{a|b}}" }]"""), "exercises[0].audio: must not be empty")
        assertEquals(listOf("blocks[0].lines: needs at least one line"), blockProblems("""{ "type": "dialogue", "lines": [] }"""))
        assertEquals(listOf("blocks[0].lines[0].who: ** at 1 is not closed", "blocks[0].lines[0].audio: must be an .mp3 or .wav file"),
            blockProblems("""{ "type": "dialogue", "lines": [ { "who": "**A", "text": "Hi", "audio": "hi.txt" } ] }"""))
        assertEquals(listOf("blocks[0].rows[0][0].audio: must be an .mp3 or .wav file"),
            blockProblems("""{ "type": "table", "rows": [ [ { "audio": "w.flac" } ] ] }"""))
    }

    @Test fun aTrackNeedsATitleAndASoundAndAVideoAnHttpsAddress() {
        assertEquals(listOf("blocks[0].title: must not be empty", "blocks[0].audio: must be an .mp3 or .wav file"),
            blockProblems("""{ "type": "track", "title": " ", "audio": "t.ogg" }"""))
        assertEquals(listOf("blocks[0].title: ** at 1 is not closed"), blockProblems("""{ "type": "track", "title": "**T", "audio": "audio/t.mp3" }"""))
        assertEquals(emptyList(), blockProblems("""{ "type": "video", "title": "Greetings", "url": "https://youtu.be/x" }"""))
        for (bad in listOf("http://youtu.be/x", "youtu.be/x", "https://", "https://a b.org", "javascript:alert(1)"))
            assertEquals(listOf("blocks[0].url: must be an https:// address without spaces"),
                blockProblems("""{ "type": "video", "title": "V", "url": "$bad" }"""), bad)
        assertEquals(listOf("blocks[0].title: must not be empty"), blockProblems("""{ "type": "video", "title": "", "url": "https://x.org" }"""))
    }

    @Test fun soundsOfAPageAndOfAnExercise() {
        val theory = ContentJson.decode(Content.serializer(), Samples.theory)
        assertEquals(listOf("audio/ex-1.mp3", "audio/d1-1.mp3", "https://audio.polylingy.app/zh/d1-2.mp3", "audio/w-laoshi.mp3", "audio/w-xuesheng.wav", "audio/t1-03.mp3"),
            theory.audios(Path("theory")).map { it.second })
        assertEquals("theory.blocks[4].blocks[0].audio", theory.audios(Path("theory")).last().first.text)
        assertEquals("theory.blocks[1].blocks[0].items[0].audio", theory.audios(Path("theory")).first().first.text)
        assertEquals("theory.blocks[3].blocks[0].lines[0].audio", theory.audios(Path("theory"))[1].first.text)
        assertEquals(listOf(Path("exercises[0].audio") to "audio/u1-xuesheng.mp3"), exercise(Samples.listen).audios(at))
    }

    // layers

    @Test fun lookAndAsideAreCheckedOnEveryType() {
        for (json in Samples.ofEachType) {
            val withLayers = json.trimEnd().removeSuffix("}") +
                """, "look": { "type": "email", "from": " ", "subject": "**x" }, "aside": { "image": { "src": "/x.png", "alt": "" }, "side": "top" } }"""
            assertEquals(listOf(
                "exercises[0].look.from: must not be empty",
                "exercises[0].look.subject: ** at 1 is not closed",
                "exercises[0].aside.image.src: must be an https:// URL or a relative path inside the course archive, like images/cat.png",
                "exercises[0].aside.image.alt: must not be empty: describe the picture",
            ), problemsOf(withLayers), json)
        }
    }

    @Test fun anExerciseIsCompleteWhenEveryItemIsAnswered() {
        fun complete(json: String, response: Response) = exercise(json).isComplete(response)
        assertFalse(complete(Samples.gaps, exercise(Samples.gaps).emptyResponse()))
        assertTrue(complete(Samples.gaps, Response.Blanks(listOf("a", "b", "c", "d"))))
        assertFalse(complete(Samples.gaps, Response.Blanks(listOf("a", "b", "", "d"))), "an empty blank is not an answer")
        assertFalse(complete(Samples.choice, exercise(Samples.choice).emptyResponse()))
        assertTrue(complete(Samples.choice, Response.Choice(listOf("’s raining", "don’t understand"))))
        assertFalse(complete(Samples.choice, Response.Choice(listOf("’s raining", null))))
        assertFalse(complete(Samples.match, exercise(Samples.match).emptyResponse()))
        assertTrue(complete(Samples.match, Response.Match(listOf(0, 1, 2))), "the extra side may stay unmatched")
        assertFalse(complete(Samples.order, exercise(Samples.order).emptyResponse()))
        assertFalse(complete(Samples.order, Response.Order(listOf(listOf("What", "are"), emptyList()))))
        assertTrue(complete(Samples.order, Response.Order(listOf(listOf("What", "are", "you", "doing", "tonight"), listOf("Is", "it", "raining", "now")))))
        assertTrue(complete(Samples.fix, exercise(Samples.fix).emptyResponse()), "a fix keeps a mark with an empty answer")
    }

    @Test fun aVideoLayerNeedsATitleAndAnHttpsAddress() {
        assertProblems(ex("choice", """"video": { "title": " ", "url": "http://x.org/v" }, "items": [{ "text": "{{a|b}}" }]"""),
            "exercises[0].video.title: must not be empty", "exercises[0].video.url: must be an https:// address without spaces")
        assertProblems(ex("choice", """"video": { "title": "Greetings", "url": "https://youtu.be/x" }, "items": [{ "text": "{{a|b}}" }]"""))
    }

    @Test fun aNewspaperNeedsAHeadline() {
        assertProblems(ex("choice", """"look": { "type": "newspaper", "headline": " ", "source": "" }, "items": [{ "text": "{{a|b}}" }]"""),
            "exercises[0].look.headline: must not be empty", "exercises[0].look.source: must not be empty")
    }

    @Test fun articleNeedsATitle() {
        assertProblems(ex("fix", """"look": { "type": "article", "title": "", "source": "" }, "items": [{ "text": "{{a}}" }]"""),
            "exercises[0].look.title: must not be empty",
            "exercises[0].look.source: must not be empty")
    }

    @Test fun anUnknownLookIsAnUnknownType() {
        val problems = exercise(ex("gaps", """"look": { "type": "chat", "with": "Tom" }, "items": [{ "text": "{{a}}" }]""")).validate(at)
        assertEquals(listOf(Problem(Path("exercises[0].look"), "unknown look \"chat\"", Problem.Kind.UNKNOWN_TYPE)), problems)
    }

    // choice in the text

    @Test fun choicePlacesInTheTextNeedOptions() {
        assertProblems(ex("choice", """"items": [{ "text": "I {{admit}} it." }]"""),
            "exercises[0].items[0].text: each place needs its options {{right|other}}, or the item needs \"options\": \"{{admit}}\" has one")
        assertProblems(ex("choice", """"items": [{ "text": "I {{admit|admit}} it." }]"""),
            "exercises[0].items[0].text: \"{{admit|admit}}\" has the same option twice")
        assertProblems(ex("choice", """"items": [{ "text": "No place." }]"""),
            "exercises[0].items[0].text: needs at least one blank {{…}}")
    }

    @Test fun choicePlacesFollowTheWrittenOrder() {
        val choice = exercise(Samples.choiceInText) as Exercise.Choice
        assertEquals(listOf(Exercise.ChoicePlace(listOf("apologise", "’m apologising"), "apologise"),
            Exercise.ChoicePlace(listOf("admit", "’m admitting"), "admit")), choice.places)
        val old = exercise(Samples.choice) as Exercise.Choice
        assertEquals(Exercise.ChoicePlace(listOf("rains", "’s raining"), "’s raining"), old.places.first())
    }

    // expand

    @Test fun expandRules() {
        assertProblems(ex("expand", """"items": []"""), "exercises[0].items: needs at least one item")
        assertProblems(ex("expand", """"items": [{ "text": "{{a}} {{b}}", "keys": [] }]"""),
            "exercises[0].items[0].text: needs exactly one blank {{…}}, found 2",
            "exercises[0].items[0].keys: needs at least one order of key words")
        assertProblems(ex("expand", """"items": [{ "text": "{{a b}}", "keys": [["a", " "], [], ["a", "c"]] }]"""),
            "exercises[0].items[0].keys[0][1]: must not be empty",
            "exercises[0].items[0].keys[1]: needs at least one key word",
            "exercises[0].items[0].keys[1]: must have the same key words as the first order, in another order",
            "exercises[0].items[0].keys[2]: must have the same key words as the first order, in another order")
    }

    @Test fun anExpandAnswerMayBeLongerThanABlank() {
        val long = "a ".repeat(70).trim()
        assertProblems(ex("expand", """"items": [{ "text": "{{$long}}", "keys": [["a"]] }]"""))
        val tooLong = "a".repeat(MAX_ANSWER_LENGTH + 1)
        assertProblems(ex("expand", """"items": [{ "text": "{{$tooLong}}", "keys": [["a"]] }]"""),
            "exercises[0].items[0].text: answer \"$tooLong\" is longer than $MAX_ANSWER_LENGTH characters")
    }

    // fix

    @Test fun fixMarksAreRightOrWrongWithFixes() {
        val fix = exercise(Samples.fix) as Exercise.Fix
        assertEquals(listOf(
            Exercise.FixMark("had been driving", emptyList()),
            Exercise.FixMark("had been breaking", listOf("broke")),
            Exercise.FixMark("signed", listOf("’ve signed", "have signed")),
            Exercise.FixMark("signed", emptyList()),
        ), fix.marks)
    }

    @Test fun fixRules() {
        assertProblems(ex("fix", """"items": [{ "text": "No marks." }]"""), "exercises[0].items[0].text: needs at least one blank {{…}}")
        assertProblems(ex("fix", """"items": [{ "text": "{{went|go}} {{ => went}} {{go => }} {{go => Go}}" }]"""),
            "exercises[0].items[0].text: \"{{went|go}}\": a wrong word is written {{wrong => fix|other fix}}",
            "exercises[0].items[0].text: \"{{=> went}}\" has nothing before =>",
            "exercises[0].items[0].text: \"{{go =>}}\" has an empty fix",
            "exercises[0].items[0].text: \"{{go => Go}}\": a fix must differ from the wrong word")
        val long = "a".repeat(MAX_BLANK_LENGTH + 1)
        assertProblems(ex("fix", """"items": [{ "text": "{{go => $long}}" }]"""),
            "exercises[0].items[0].text: \"$long\" is longer than $MAX_BLANK_LENGTH characters")
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
            "exercises[0].rows[0][1]: answer \"${fits}b\" is longer than $MAX_BLANK_LENGTH characters")
    }

    @Test fun gapsCheckMarkupHintNoteExampleAndWordBank() = assertProblems(
        ex("gaps", """"example": "I {{am}}", "wordBank": [" "],
            "items": [{ "text": "a **{{b}}[[ ]]", "note": "{{x}}" }]"""),
        "exercises[0].wordBank[0]: must not be empty",
        "exercises[0].example: blanks {{…}} are only allowed in exercises",
        "exercises[0].items[0].text: the hint [[…]] at 10 must be one line of text",
        "exercises[0].items[0].text: ** at 3 is not closed",
        "exercises[0].items[0].note: blanks {{…}} are only allowed in exercises",
    )

    @Test fun aHintAfterABlankIsOnlyForGaps() = assertProblems(
        ex("choice", """"items": [{ "text": "x {{a|b}}[[hint]]" }]"""),
        "exercises[0].items[0].text: a hint [[…]] after a blank is only for the sentences of a gaps exercise",
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
            "exercises[0].items[0].text: with \"options\" the blank holds only the right answer")
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

    @Test fun imageDescriptionAndMade() {
        assertEquals(emptyList(), Image("a.png", "alt", description = "A red tram at a stop", made = "Prompt: a red tram").validate(Path("image")))
        assertEquals(listOf("image.description: must not be empty: leave it out, or say something", "image.made: must be at most 2000 characters"),
            Image("a.png", "alt", description = " ", made = "x".repeat(2001)).validate(Path("image")).map { it.toString() })
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
        assertEquals(emptyList(), blockProblems("""{ "type": "section", "title": "Грамматика", "blocks": [ { "type": "text", "text": "A heading, no letter." } ] }"""))
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
        val problems = ContentJson.decode(Content.serializer(), """{ "format": "blocks", "blocks": [{ "type": "hologram", "src": "x" }] }""")
            .validate(Path("theory"))
        assertEquals(listOf(Problem(Path("theory.blocks[0]"), "unknown block type \"hologram\"", Problem.Kind.UNKNOWN_TYPE)), problems)
        assertTrue(ContentJson.decode(Content.serializer(), """{ "format": "markdown", "text": "# A" }""").validate(Path("theory"))
            .single().kind == Problem.Kind.UNKNOWN_TYPE)
        assertEquals(listOf("exercises[0]: unknown exercise type \"dictation\""),
            problemsOf("""{ "id": "${Samples.ID}", "type": "dictation", "audio": "a.mp3" }"""))
    }

    @Test fun sortNeedsTwoGroupsAndEachWordOnce() {
        assertProblems(ex("sort", """"groups": [ { "title": "a", "items": ["x", "y"] } ]"""),
            "exercises[0].groups: needs 2 to 6 groups", "exercises[0].groups: needs 3 to 24 words in all")
        assertTrue(problemsOf(ex("sort", """"groups": [ { "title": "a", "items": ["x", "y"] }, { "title": "b", "items": ["x"] } ]""")).isNotEmpty())
    }

    @Test fun anItemOfAChoiceMayHaveItsOwnSound() {
        assertEquals(emptyList(), problemsOf(Samples.choiceAudio))
        assertTrue(problemsOf(Samples.choiceAudio.replace("audio/word-01.mp3", "../x.mp3")).isNotEmpty())
        assertEquals(listOf("audio/word-01.mp3"), exercise(Samples.choiceAudio).audios(at).map { it.second })
    }
}

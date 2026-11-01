package com.polylingy.core.rules

import com.polylingy.content.ContentJson
import com.polylingy.content.Exercise
import com.polylingy.content.ItemCheck
import com.polylingy.content.ItemResult.CORRECT
import com.polylingy.content.ItemResult.TYPO
import com.polylingy.content.ItemResult.WRONG
import com.polylingy.content.Response
import com.polylingy.content.emptyResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExerciseCheckerTest {
    private val id = "6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d"
    private fun exercise(type: String, body: String) = ContentJson.decode(Exercise.serializer(), """{ "id": "$id", "type": "$type", $body }""")
    private val strict = CheckSettings(allowTypos = false)
    private val lenient = CheckSettings(allowTypos = true)

    private val gaps = exercise("gaps", """"items": [
        { "text": "Listen! Somebody {{is singing}}." },
        { "text": "Why {{are}} you {{wearing}} a coat?" },
        { "text": "I {{’m not reading|am not reading}} it." } ]""")

    @Test fun gapsAreCheckedBlankByBlankInReadingOrder() = assertEquals(
        listOf(ItemCheck(CORRECT, "is singing"), ItemCheck(CORRECT, "are"), ItemCheck(TYPO, "wearing"), ItemCheck(WRONG, "’m not reading")),
        Checking.check(gaps, Response.Blanks(listOf("Is singing.", "are", "waering", "don’t read")), lenient),
    )

    @Test fun typosCountOnlyWhenTheCourseAllowsThem() = assertEquals(
        listOf(CORRECT, CORRECT, WRONG, CORRECT),
        Checking.check(gaps, Response.Blanks(listOf("is singing", "are", "waering", "am not reading")), strict).map { it.result },
    )

    @Test fun picturesAndTablesUseBlanksToo() {
        val pictures = exercise("pictures", """"items": [ { "image": { "src": "a.png", "alt": "A" }, "text": "He {{’s cooking|is cooking}}." } ]""")
        assertEquals(listOf(ItemCheck(CORRECT, "is cooking")), Checking.check(pictures, Response.Blanks(listOf("is cooking")), lenient))
        val table = exercise("table", """"rows": [ ["I", "I {{am}} working", "{{Am}} I working?"], ["she", "she {{is}} working", "x"] ]""")
        assertEquals(listOf(CORRECT, WRONG, CORRECT), Checking.check(table, Response.Blanks(listOf("am", "is", "is")), lenient).map { it.result })
    }

    @Test fun choiceIsExact() {
        val choice = exercise("choice", """"items": [
            { "text": "It {{’s raining}}.", "options": ["rains", "’s raining"] },
            { "text": "I {{don’t understand}}.", "options": ["don’t understand", "’m not understanding"] },
            { "text": "{{Do you go}} there?", "options": ["Are you going", "Do you go"] } ]""")
        assertEquals(
            listOf(ItemCheck(CORRECT, "’s raining"), ItemCheck(WRONG, "don’t understand"), ItemCheck(WRONG, "Do you go")),
            Checking.check(choice, Response.Choice(listOf("’s raining", "’m not understanding", null)), lenient),
        )
    }

    @Test fun matchChecksEveryPair() {
        val match = exercise("match", """"pairs": [ ["I’m tired,", "so I’m going to bed."], ["Be quiet!", "The **baby** is sleeping."],
            [{ "image": { "src": "d.png", "alt": "Dan" } }, { "image": { "src": "s.png", "alt": "A shower" } }] ], "extra": ["It rains."]""")
        assertEquals(
            listOf(ItemCheck(CORRECT, "so I’m going to bed."), ItemCheck(WRONG, "The baby is sleeping."), ItemCheck(WRONG, "A shower")),
            Checking.check(match, Response.Match(listOf(0, 3, null)), lenient),
        )
    }

    @Test fun orderAcceptsTheAnswerOrAnAcceptedOrder() {
        val order = exercise("order", """"items": [
            { "answer": "Is it raining now?", "accept": ["Is it now raining?"] },
            { "answer": "What are you doing?" },
            { "answer": "I am here." } ]""")
        assertEquals(
            listOf(ItemCheck(CORRECT, "Is it raining now?"), ItemCheck(WRONG, "What are you doing?"), ItemCheck(WRONG, "I am here.")),
            Checking.check(order, Response.Order(listOf(listOf("Is", "it", "now", "raining"), listOf("What", "you", "are", "doing"), emptyList())), lenient),
        )
    }

    @Test fun anEmptyResponseIsAllWrong() {
        for (exercise in listOf(gaps, exercise("choice", """"items": [ { "text": "{{a}}", "options": ["a", "b"] } ]"""))) {
            assertEquals(List(exercise.itemCount) { WRONG }, Checking.check(exercise, exercise.emptyResponse(), lenient).map { it.result })
        }
    }

    @Test fun aResponseOfTheWrongShapeIsRejected() {
        assertFailsWith<IllegalArgumentException> { Checking.check(gaps, Response.Choice(listOf(null)), lenient) }
        assertFailsWith<IllegalArgumentException> { Checking.check(gaps, Response.Blanks(listOf("a")), lenient) }
        val choice = exercise("choice", """"items": [ { "text": "{{a}}", "options": ["a", "b"] } ]""")
        assertFailsWith<IllegalArgumentException> { Checking.check(choice, Response.Choice(emptyList()), lenient) }
        val match = exercise("match", """"pairs": [ ["a", "b"], ["c", "d"] ]""")
        assertFailsWith<IllegalArgumentException> { Checking.check(match, Response.Match(listOf(0)), lenient) }
        val order = exercise("order", """"items": [ { "answer": "a b" } ]""")
        assertFailsWith<IllegalArgumentException> { Checking.check(order, Response.Order(emptyList()), lenient) }
        val unknown = exercise("dictation", """"audio": "a.mp3"""")
        assertFailsWith<IllegalArgumentException> { Checking.check(unknown, Response.Blanks(emptyList()), lenient) }
    }
}

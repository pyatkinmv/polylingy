package com.polylingy.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ResponseTest {
    private fun exercise(json: String) = ContentJson.decode(Exercise.serializer(), json)

    @Test fun emptyResponsesHaveOneSlotPerItem() {
        assertEquals(Response.Blanks(List(4) { "" }), exercise(Samples.gaps).emptyResponse())
        assertEquals(Response.Blanks(List(2) { "" }), exercise(Samples.pictures).emptyResponse())
        assertEquals(Response.Blanks(List(4) { "" }), exercise(Samples.table).emptyResponse())
        assertEquals(Response.Choice(listOf(null, null)), exercise(Samples.choice).emptyResponse())
        assertEquals(Response.Match(listOf(null, null, null)), exercise(Samples.match).emptyResponse())
        assertEquals(Response.Order(listOf(emptyList(), emptyList())), exercise(Samples.order).emptyResponse())
        assertFailsWith<IllegalArgumentException> { exercise("""{ "id": "${Samples.ID}", "type": "x" }""").emptyResponse() }
    }

    @Test fun blanksInReadingOrder() {
        assertEquals(listOf("is singing", "are", "wearing", "’m not reading"), exercise(Samples.gaps).blanks.map { it.answer })
        assertEquals(listOf("am", "Am", "is", "Is"), exercise(Samples.table).blanks.map { it.answer })
        assertEquals(listOf("’s cooking", "’re swimming"), exercise(Samples.pictures).blanks.map { it.answer })
        assertEquals(emptyList(), exercise(Samples.choice).blanks)
    }
}

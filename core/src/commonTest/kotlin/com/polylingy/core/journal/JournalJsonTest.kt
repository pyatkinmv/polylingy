package com.polylingy.core.journal

import com.polylingy.content.ItemResult
import com.polylingy.content.Response
import com.polylingy.core.PolylingyJson
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class JournalJsonTest {
    private val moscow = TimeZone.of("Europe/Moscow")
    private val zoned = ZonedInstant(LocalDateTime(2026, 10, 31, 0, 30).toInstant(moscow), moscow)

    @Test fun zonedInstantLooksLikeJavaZonedDateTime() =
        assertEquals("2026-10-31T00:30:00+03:00[Europe/Moscow]", zoned.toString())

    @Test fun zonedInstantRoundTrips() = assertEquals(zoned, ZonedInstant.parse(zoned.toString()))

    @Test fun envelopeRoundTripsWithTypeDiscriminator() {
        val envelope = EventEnvelope(
            eventId = Uuid.random(),
            deviceId = Uuid.random(),
            occurredAt = zoned,
            event = AnswerSubmitted(Uuid.random(), Uuid.random(), Uuid.random(), 3, Response.Blanks(listOf("rojo", "rosa")), listOf(ItemResult.CORRECT, ItemResult.TYPO)),
        )
        val json = PolylingyJson.encodeToString(EventEnvelope.serializer(), envelope)
        assertTrue("\"type\":\"AnswerSubmitted\"" in json, json)
        assertTrue("\"response\":{\"type\":\"blanks\"" in json, json)
        assertEquals(envelope, PolylingyJson.decodeFromString(EventEnvelope.serializer(), json))
    }

    @Test fun unknownKeysAreTolerated() {
        val id = "00000000-0000-0000-0000-000000000000"
        val json = """
            {"eventId":"$id","deviceId":"$id","occurredAt":"$zoned","schemaVersion":1,"addedLater":true,
             "event":{"type":"AnswerSubmitted","courseId":"$id","topicId":"$id","exerciseId":"$id","courseVersion":1,
               "response":{"type":"choice","picked":["a"]},"results":["WRONG"],"timeSpent":12}}
        """.trimIndent()
        val event = PolylingyJson.decodeFromString(EventEnvelope.serializer(), json).event as AnswerSubmitted
        assertEquals(Response.Choice(listOf("a")), event.response)
        assertEquals(listOf(ItemResult.WRONG), event.results)
    }

    @Test fun anEntryOfSchemaOneNamesTheCoursesVersionCourseRevisionAndStaysReadable() {
        val id = "00000000-0000-0000-0000-000000000000"
        val old = """
            {"eventId":"$id","deviceId":"$id","occurredAt":"$zoned","schemaVersion":1,
             "event":{"type":"AnswerSubmitted","courseId":"$id","topicId":"$id","exerciseId":"$id","courseRevision":4,
               "response":{"type":"blanks","values":["x"]},"results":["CORRECT"]}}
        """.trimIndent()
        val envelope = PolylingyJson.decodeFromString(EventEnvelope.serializer(), old)
        assertEquals(1, envelope.schemaVersion, "an old entry is read as it is")
        assertEquals(4, (envelope.event as AnswerSubmitted).courseVersion)
        val written = PolylingyJson.encodeToString(EventEnvelope.serializer(), envelope)
        assertTrue("\"courseVersion\":4" in written && "courseRevision" !in written, written)
        assertEquals(2, EventEnvelope.CURRENT_SCHEMA_VERSION)
    }
}

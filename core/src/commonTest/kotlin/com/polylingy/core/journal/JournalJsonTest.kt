package com.polylingy.core.journal

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
            event = AnswerSubmitted(Uuid.random(), Uuid.random(), Uuid.random(), "rojo, rosa", AutoCheck.PARTIAL, Rating.INCORRECT),
        )
        val json = JournalJson.encodeToString(EventEnvelope.serializer(), envelope)
        assertTrue("\"type\":\"AnswerSubmitted\"" in json, json)
        assertEquals(envelope, JournalJson.decodeFromString(EventEnvelope.serializer(), json))
    }

    @Test fun optionalFieldsAndUnknownKeysAreTolerated() {
        val id = "00000000-0000-0000-0000-000000000000"
        val json = """
            {"eventId":"$id","deviceId":"$id","occurredAt":"$zoned","schemaVersion":1,"addedLater":true,
             "event":{"type":"AnswerSubmitted","courseId":"$id","topicId":"$id","exerciseId":"$id","input":"x"}}
        """.trimIndent()
        val event = JournalJson.decodeFromString(EventEnvelope.serializer(), json).event as AnswerSubmitted
        assertEquals(null, event.autoCheck)
        assertEquals(null, event.rating)
    }
}

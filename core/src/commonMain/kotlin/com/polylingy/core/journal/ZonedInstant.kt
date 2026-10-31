package com.polylingy.core.journal

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.offsetAt
import kotlinx.datetime.parse
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Instant

/**
 * A moment together with the time zone it happened in — a multiplatform stand-in for
 * `java.time.ZonedDateTime`. The zone fixes which calendar day an event belongs to,
 * so replaying the journal gives the same result on every device.
 *
 * Serialized like `ZonedDateTime.toString()`: `2026-10-31T00:30:00+03:00[Europe/Moscow]`.
 */
@Serializable(with = ZonedInstantSerializer::class)
data class ZonedInstant(val instant: Instant, val zone: TimeZone) {
    val localDate: LocalDate get() = instant.toLocalDateTime(zone).date

    override fun toString(): String {
        val dateTime = instant.format(DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET, zone.offsetAt(instant))
        return "$dateTime[${zone.id}]"
    }

    companion object {
        fun parse(text: String): ZonedInstant {
            val bracket = text.indexOf('[')
            require(bracket > 0 && text.endsWith(']')) { "Expected <date-time><offset>[<zone>]: $text" }
            val instant = Instant.parse(text.substring(0, bracket), DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET)
            val zone = TimeZone.of(text.substring(bracket + 1, text.length - 1))
            return ZonedInstant(instant, zone)
        }
    }
}

object ZonedInstantSerializer : KSerializer<ZonedInstant> {
    override val descriptor = PrimitiveSerialDescriptor("com.polylingy.core.journal.ZonedInstant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ZonedInstant) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): ZonedInstant = ZonedInstant.parse(decoder.decodeString())
}

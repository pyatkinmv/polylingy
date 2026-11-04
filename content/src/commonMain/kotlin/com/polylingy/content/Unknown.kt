package com.polylingy.content

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** The `"type"` (or `"format"`) of a JSON object, as written in the file. */
internal fun JsonObject.typeName(key: String = "type"): String =
    (get(key) as? JsonPrimitive)?.content.orEmpty()

/**
 * Reads an object of a type this app doesn't know as raw JSON. Such content comes only from files of a newer
 * format: it is never written back (a course with it is study-only until the app is updated).
 */
internal abstract class RawObjectSerializer<T>(name: String, private val wrap: (JsonObject) -> T) : KSerializer<T> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(name)

    override fun deserialize(decoder: Decoder): T = wrap((decoder as JsonDecoder).decodeJsonElement().jsonObject)

    override fun serialize(encoder: Encoder, value: T): Nothing =
        throw SerializationException("content of a type this app doesn't know can't be saved; update Polylingy")
}

internal object UnknownBlockSerializer : RawObjectSerializer<Block.Unknown>("unknownBlock", Block::Unknown)
internal object UnknownExerciseSerializer : RawObjectSerializer<Exercise.Unknown>("unknownExercise", Exercise::Unknown)
internal object UnknownLookSerializer : RawObjectSerializer<Look.Unknown>("unknownLook", Look::Unknown)
internal object UnknownContentSerializer : RawObjectSerializer<Content.Unknown>("unknownContent", Content::Unknown)

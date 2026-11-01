package com.polylingy.content

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.modules.SerializersModule

/** A file that can't be read at all; [problem] says what and where. */
class ContentFormatException(val problem: Problem) : IllegalArgumentException(problem.toString())

/**
 * JSON of course content. Objects are told apart by `"type"` (`"format"` for [Content]); types this app doesn't know
 * are read as `Unknown` and reported by validate(), so a file shows all of them at once.
 */
object ContentJson {
    val module = SerializersModule {
        polymorphicDefaultDeserializer(Block::class) { UnknownBlockSerializer }
        polymorphicDefaultDeserializer(Exercise::class) { UnknownExerciseSerializer }
        polymorphicDefaultDeserializer(Content::class) { UnknownContentSerializer }
    }

    /** For files of this app's format version: an unknown field is an error. */
    val strict = Json {
        classDiscriminator = "type"
        explicitNulls = false
        serializersModule = module
    }

    /** For files of a newer format version: unknown fields are skipped. */
    val lenient = Json(strict) { ignoreUnknownKeys = true }

    /** For export: indented, for people to read and edit. */
    val pretty = Json(strict) { prettyPrint = true }

    /** Reads [text]; a malformed file throws [ContentFormatException] with the path of the first error. */
    fun <T> decode(deserializer: DeserializationStrategy<T>, text: String, lenient: Boolean = false): T = try {
        // kotlinx reports exact paths only while "type" comes first in an object, so it is moved there.
        val normalized = strict.encodeToString(JsonElement.serializer(), discriminatorsFirst(strict.parseToJsonElement(text)))
        (if (lenient) this.lenient else strict).decodeFromString(deserializer, normalized)
    } catch (e: SerializationException) {
        throw ContentFormatException(problemOf(e))
    } catch (e: IllegalArgumentException) {
        throw ContentFormatException(problemOf(e))
    }

    fun <T> encode(serializer: SerializationStrategy<T>, value: T): String = pretty.encodeToString(serializer, value)

    private val discriminators = listOf("type", "format")

    private fun discriminatorsFirst(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(buildMap {
            for (key in discriminators) element[key]?.let { put(key, it) }
            for ((key, value) in element) if (key !in discriminators) put(key, discriminatorsFirst(value))
        })
        is JsonArray -> JsonArray(element.map(::discriminatorsFirst))
        else -> element
    }

    private val pathPattern = Regex("""\s*at path:? (\$\S*)""")
    private val offsetPattern = Regex("""^Unexpected JSON token at offset \d+: """)

    /** Turns kotlinx's "… at path: $.units[0].text" message into a [Problem] with that path. */
    internal fun problemOf(e: Exception): Problem {
        val message = e.message.orEmpty().lineSequence().first()
        val match = pathPattern.find(message)
        val path = match?.groupValues?.get(1)?.removePrefix("$")?.removePrefix(".").orEmpty()
        val text = (if (match != null) message.removeRange(match.range) else message).replace(offsetPattern, "").trim()
        return Problem(Path(path), text.ifEmpty { "not valid JSON" })
    }
}

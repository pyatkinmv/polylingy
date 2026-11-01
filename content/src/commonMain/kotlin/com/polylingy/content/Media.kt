package com.polylingy.content

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * A picture. [src] is either an `https://` URL or a path inside the course archive (`images/cat.png`);
 * [alt] is required: it is read out by screen readers and shown when the picture can't be loaded.
 */
@Serializable
data class Image(val src: String, val alt: String, val caption: Inline? = null) {
    fun validate(at: Path): List<Problem> = checks { image(src, alt, caption, at) }
}

internal fun Checks.image(src: String, alt: String, caption: Inline?, at: Path) {
    val srcAt = at / "src"
    when {
        src.isBlank() -> error(srcAt, "must not be empty")
        "://" in src -> require(src.startsWith("https://"), srcAt, "a URL must start with https://")
        else -> require(
            !src.startsWith("/") && '\\' !in src && ':' !in src && src.split('/').none { it == ".." || it.isEmpty() },
            srcAt, "must be an https:// URL or a relative path inside the course archive, like images/cat.png",
        )
    }
    require(alt.isNotBlank(), at / "alt", "must not be empty: describe the picture")
    text(caption, at / "caption")
}

/** One side of a matching pair: a text (JSON string) or a picture (`{"image": {…}}`). */
@Serializable(with = SideSerializer::class)
sealed interface Side {
    data class Text(val text: Inline) : Side
    data class Picture(val image: Image) : Side
}

internal fun Checks.side(side: Side, at: Path) = when (side) {
    is Side.Text -> text(side.text, at)
    is Side.Picture -> nested(side.image.validate(at / "image"))
}

object SideSerializer : KSerializer<Side> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("com.polylingy.content.Side")

    override fun deserialize(decoder: Decoder): Side {
        val input = decoder as JsonDecoder
        val element = input.decodeJsonElement()
        if (element is JsonPrimitive && element.isString) return Side.Text(Inline(element.content))
        val obj = element as? JsonObject
        if (obj == null || obj.keys != setOf("image")) {
            throw SerializationException("a matching side must be a text or {\"image\": {…}}, got $element")
        }
        return Side.Picture(input.json.decodeFromJsonElement(Image.serializer(), obj.getValue("image")))
    }

    override fun serialize(encoder: Encoder, value: Side) {
        val output = encoder as JsonEncoder
        when (value) {
            is Side.Text -> output.encodeString(value.text.text)
            is Side.Picture -> output.encodeJsonElement(buildJsonObject {
                put("image", output.json.encodeToJsonElement(Image.serializer(), value.image))
            })
        }
    }
}

/** A theory table cell: a text (JSON string) or `{"text": "…", "rowSpan": 3}`. */
@Serializable(with = CellSerializer::class)
data class Cell(val text: Inline, val rowSpan: Int = 1)

object CellSerializer : KSerializer<Cell> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("com.polylingy.content.Cell")

    override fun deserialize(decoder: Decoder): Cell {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        if (element is JsonPrimitive && element.isString) return Cell(Inline(element.content))
        val obj = element as? JsonObject
            ?: throw SerializationException("a table cell must be a text or {\"text\": …, \"rowSpan\": …}, got $element")
        val unknown = obj.keys - setOf("text", "rowSpan")
        if (unknown.isNotEmpty()) throw SerializationException("a table cell has unknown keys $unknown")
        val text = (obj["text"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            ?: throw SerializationException("a table cell needs \"text\"")
        val rowSpan = obj["rowSpan"]?.let { it.jsonPrimitive.intOrNull ?: throw SerializationException("rowSpan must be a number") } ?: 1
        return Cell(Inline(text), rowSpan)
    }

    override fun serialize(encoder: Encoder, value: Cell) {
        val output = encoder as JsonEncoder
        if (value.rowSpan == 1) output.encodeString(value.text.text)
        else output.encodeJsonElement(buildJsonObject {
            put("text", JsonPrimitive(value.text.text))
            put("rowSpan", JsonPrimitive(value.rowSpan))
        })
    }
}

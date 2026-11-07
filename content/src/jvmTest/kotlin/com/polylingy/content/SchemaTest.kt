package com.polylingy.content

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementNames
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * docs/course.schema.json, the formal description of the course format, against the code: every sample of
 * content-format.md passes it, broken content doesn't, and every object in it has exactly the fields of its class.
 * The course itself is checked the same way in core.
 */
class SchemaTest {
    private val mapper = ObjectMapper()
    private val root = mapper.readTree(File("../docs/course.schema.json")) as ObjectNode
    private val defs = root["\$defs"]

    /** Messages of checking [json] against the definition [def] of the schema. */
    private fun errors(def: String, json: String): List<String> {
        val schema = root.deepCopy().put("\$ref", "#/\$defs/$def")
        val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
        return factory.getSchema(schema).validate(mapper.readTree(json)).map { it.message }
    }

    private fun properties(def: String): Set<String> = defs[def]["properties"].fieldNames().asSequence().toSet()

    /** The serial names of the subclasses of a sealed class, without the one for types of a newer format. */
    private fun subtypes(descriptor: SerialDescriptor): Set<String> =
        descriptor.getElementDescriptor(1).elementNames.filterNot { it.startsWith("unknown") }.toSet()

    private fun refs(def: String, prefix: String): Set<String> =
        defs[def]["oneOf"].map { it["\$ref"].asText().substringAfterLast("/").removePrefix(prefix) }.toSet()

    @Test fun everySamplePassesTheSchema() {
        for ((name, json) in Samples.exercises) assertEquals(emptyList(), errors("exercise", json), name)
        assertEquals(emptyList(), errors("content", Samples.theory))
    }

    @Test fun whatTheAppWritesPassesTheSchema() {
        for ((name, json) in Samples.exercises) {
            val written = ContentJson.encode(Exercise.serializer(), ContentJson.decode(Exercise.serializer(), json))
            assertEquals(emptyList(), errors("exercise", written), name)
        }
    }

    @Test fun brokenContentFailsTheSchema() {
        val id = Samples.ID
        val broken = listOf(
            """{ "id": "$id", "type": "gaps", "items": [{ "text": "{{a}}", "hnit": "x" }] }""",
            """{ "id": "$id", "type": "gaps", "items": [] }""",
            """{ "id": "$id", "type": "dictation", "items": [{ "text": "{{a}}" }] }""",
            """{ "id": "$id", "type": "fix", "wordBank": ["a"], "items": [{ "text": "{{a}}" }] }""",
            """{ "id": "$id", "type": "gaps", "look": { "type": "chat" }, "items": [{ "text": "{{a}}" }] }""",
            """{ "id": "$id", "type": "gaps", "aside": { "image": { "src": "a.png", "alt": "A" }, "side": "middle" }, "items": [{ "text": "{{a}}" }] }""",
            """{ "id": "$id", "type": "expand", "items": [{ "text": "{{a}}", "keys": [] }] }""",
            """{ "id": "$id", "type": "choice", "items": [{ "text": "{{a}}", "options": ["a"] }] }""",
        )
        for (json in broken) assertTrue(errors("exercise", json).isNotEmpty(), json)
    }

    @Test fun everyTypeIsInTheSchema() {
        assertEquals(subtypes(Exercise.serializer().descriptor), refs("exercise", "exercise."))
        assertEquals(subtypes(Block.serializer().descriptor), refs("block", "block."))
        assertEquals(subtypes(Look.serializer().descriptor), refs("look", "look."))
    }

    @Test fun everyObjectHasTheFieldsOfItsClass() {
        val classes = mapOf(
            "image" to Image.serializer().descriptor,
            "aside" to Aside.serializer().descriptor,
            "look.email" to Look.Email.serializer().descriptor,
            "look.article" to Look.Article.serializer().descriptor,
            "look.newspaper" to Look.Newspaper.serializer().descriptor,
            "exercise.gaps" to Exercise.Gaps.serializer().descriptor,
            "gapItem" to Exercise.GapItem.serializer().descriptor,
            "exercise.choice" to Exercise.Choice.serializer().descriptor,
            "choiceItem" to Exercise.ChoiceItem.serializer().descriptor,
            "exercise.pictures" to Exercise.Pictures.serializer().descriptor,
            "pictureItem" to Exercise.PictureItem.serializer().descriptor,
            "exercise.table" to Exercise.Table.serializer().descriptor,
            "exercise.match" to Exercise.Match.serializer().descriptor,
            "exercise.order" to Exercise.Order.serializer().descriptor,
            "orderItem" to Exercise.OrderItem.serializer().descriptor,
            "exercise.expand" to Exercise.Expand.serializer().descriptor,
            "expandItem" to Exercise.ExpandItem.serializer().descriptor,
            "exercise.fix" to Exercise.Fix.serializer().descriptor,
            "fixItem" to Exercise.FixItem.serializer().descriptor,
            "content" to Content.Blocks.serializer().descriptor,
            "block.section" to Block.Section.serializer().descriptor,
            "block.text" to Block.Text.serializer().descriptor,
            "block.situation" to Block.Situation.serializer().descriptor,
            "block.image" to Block.Picture.serializer().descriptor,
            "block.table" to Block.Table.serializer().descriptor,
            "block.quote" to Block.Quote.serializer().descriptor,
            "block.examples" to Block.Examples.serializer().descriptor,
            "example" to Block.Example.serializer().descriptor,
            "block.words" to Block.Words.serializer().descriptor,
            "block.tip" to Block.Tip.serializer().descriptor,
            "block.seeAlso" to Block.SeeAlso.serializer().descriptor,
            "block.dialogue" to Block.Dialogue.serializer().descriptor,
            "block.track" to Block.Track.serializer().descriptor,
            "block.video" to Block.Video.serializer().descriptor,
            "line" to Block.Line.serializer().descriptor,
        )
        for ((def, descriptor) in classes) {
            assertEquals(descriptor.elementNames.toSet(), properties(def) - "type" - "format", def)
        }
    }
}

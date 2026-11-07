package com.polylingy.core.content

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
import kotlin.time.Instant

/**
 * docs/course.schema.json against the course model: the example courses and every file the app writes pass it,
 * a broken course doesn't, and the course, its parts and its rules have exactly the fields of their classes.
 * Exercises and blocks are checked the same way in content (SchemaTest).
 */
class CourseSchemaTest {
    private val mapper = ObjectMapper()
    private val root = mapper.readTree(File("../docs/course.schema.json")) as ObjectNode
    private val defs = root["\$defs"]
    private val now = Instant.parse("2026-11-01T10:00:00Z")

    private fun errors(json: String, def: String = "course"): List<String> {
        val schema = root.deepCopy().put("\$ref", "#/\$defs/$def")
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schema).validate(mapper.readTree(json)).map { it.message }
    }

    private fun properties(def: String): Set<String> = defs[def]["properties"].fieldNames().asSequence().toSet()

    private fun subtypes(descriptor: SerialDescriptor): Set<String> = descriptor.getElementDescriptor(1).elementNames.toSet()

    private fun refs(def: String, prefix: String): Set<String> =
        defs["rules"]["properties"][def]["oneOf"].map { it["\$ref"].asText().substringAfterLast("/").removePrefix(prefix) }.toSet()

    @Test fun theExampleCoursesPassTheSchema() {
        val files = File("../examples").listFiles { f -> f.extension == "json" }!!
        assertTrue(files.isNotEmpty())
        for (file in files) assertEquals(emptyList(), errors(file.readText()), file.name)
        // Archives of the current format; english-grammar.zip stays in format 1 on purpose.
        for (zip in File("../examples").listFiles { f -> f.extension == "zip" }!!) {
            val json = java.util.zip.ZipFile(zip).use { z -> z.getInputStream(z.getEntry("course.json")).readBytes().decodeToString() }
            if ("\"formatVersion\": $FORMAT_VERSION" in json) assertEquals(emptyList(), errors(json), zip.name)
        }
    }

    @Test fun everyCourseTheAppWritesPassesTheSchema() {
        for (sample in listOf(CourseSamples.units, CourseSamples.flat)) {
            val written = CourseJson.write(CourseJson.read(sample, now).course)
            assertEquals(emptyList(), errors(written), written)
        }
    }

    @Test fun aBrokenCourseFailsTheSchema() {
        val gaps = """{ "type": "gaps", "items": [ { "text": "{{a}}" } ] }"""
        val broken = listOf(
            """{ "formatVersion": 2, "name": "X", "topics": [ { "title": "T", "exercises": [ $gaps ] } ] }""",
            """{ "formatVersion": 5, "name": "X", "level": "B1", "topics": [ { "title": "T", "exercises": [ $gaps ] } ] }""",
            """{ "formatVersion": 5, "name": "X", "topics": [ { "title": "T", "exercises": [] } ] }""",
            """{ "formatVersion": 5, "name": "X", "units": [ { "title": "U" } ] }""",
            """{ "formatVersion": 5, "name": "X", "rules": { "picker": { "type": "random" } }, "topics": [] }""",
            """{ "formatVersion": 5, "name": "X", "theme": { "accent": "green" }, "topics": [] }""",
        )
        for (json in broken) assertTrue(errors(json).isNotEmpty(), json)
    }

    @Test fun everyRuleIsInTheSchema() {
        assertEquals(subtypes(GradingSpec.serializer().descriptor), refs("grading", "grading."))
        assertEquals(subtypes(MasterySpec.serializer().descriptor), refs("mastery", "mastery."))
        assertEquals(subtypes(SchedulerSpec.serializer().descriptor), refs("scheduler", "scheduler."))
        assertEquals(subtypes(PickerSpec.serializer().descriptor), refs("picker", "picker."))
        assertEquals(Preset.entries.map { Preset.serializer().descriptor.getElementName(it.ordinal) }.toSet(),
            defs["rules"]["properties"]["preset"]["enum"].map { it.asText() }.toSet())
    }

    @Test fun everyObjectHasTheFieldsOfItsClass() {
        val classes = mapOf(
            "course" to Course.serializer().descriptor,
            "unit" to CourseUnit.serializer().descriptor,
            "topic" to Topic.serializer().descriptor,
            "theme" to Theme.serializer().descriptor,
            "rules" to RulesSpec.serializer().descriptor,
            "display" to DisplaySpec.serializer().descriptor,
            "grading.all-correct" to GradingSpec.AllCorrect.serializer().descriptor,
            "grading.threshold" to GradingSpec.Threshold.serializer().descriptor,
            "mastery.consecutive" to MasterySpec.Consecutive.serializer().descriptor,
            "scheduler.once" to SchedulerSpec.Once.serializer().descriptor,
            "scheduler.ladder" to SchedulerSpec.Ladder.serializer().descriptor,
            "scheduler.sm2" to SchedulerSpec.Sm2.serializer().descriptor,
            "picker.conveyor" to PickerSpec.Conveyor.serializer().descriptor,
            "picker.shuffle" to PickerSpec.Shuffle.serializer().descriptor,
            "picker.alternate" to PickerSpec.Alternate.serializer().descriptor,
        )
        for ((def, descriptor) in classes) assertEquals(descriptor.elementNames.toSet(), properties(def) - "type", def)
    }
}

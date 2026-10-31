package com.polylingy.client

import com.polylingy.core.content.Course
import com.polylingy.core.content.Exercise
import com.polylingy.core.content.Explanation
import com.polylingy.core.content.ExplanationFormat
import com.polylingy.core.content.Topic
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** A course file the user can't import; [message] says what is wrong and where. */
class CourseFormatException(message: String) : IllegalArgumentException(message)

/**
 * The user-facing course file format (files from the first app version, without ids, still import):
 *
 * ```json
 * {"name": "…", "createdAt": "…", "topics": [{"subject": "…", "generalExplanation": "…",
 *   "generalExplanationFormat": "HTML", "exercises": [{"task": "{0} …", "answer": "…" | {"0": "…"},
 *   "exampleExplanation": "…"}]}]}
 * ```
 *
 * `id` fields are optional; missing, non-UUID or duplicate ids are replaced with fresh UUIDs.
 */
object CourseFile {
    private val pretty = Json { prettyPrint = true }

    /** Parses a whole course. Revision and updatedAt are left for [com.polylingy.core.content.CourseService.import] to set. */
    fun parseCourse(text: String, now: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): Course {
        val root = Node(parse(text), "course")
        val ids = mutableSetOf<Uuid>()
        return Course(
            id = root.id(ids),
            name = root.optionalString("name") ?: "Unnamed Course",
            createdAt = root.optionalString("createdAt")?.let { parseTime(it, zone) ?: root.fail("createdAt", "not a date-time") } ?: now,
            revision = 0,
            updatedAt = now,
            topics = root.optionalArray("topics").orEmpty().map { topic(it, ids) },
        )
    }

    /** Parses one topic, as pasted into the "Add topic" dialog. */
    fun parseTopic(text: String): Topic = topic(Node(parse(text), "topic"), mutableSetOf())

    fun export(course: Course): String = pretty.encodeToString(JsonObject.serializer(), buildJsonObject {
        put("id", course.id.toString())
        put("name", course.name)
        put("createdAt", course.createdAt.toString())
        putJsonArray("topics") {
            for (topic in course.topics) add(buildJsonObject {
                put("id", topic.id.toString())
                put("subject", topic.subject)
                topic.explanation?.let { explanation ->
                    put("generalExplanation", explanation.text)
                    if (explanation.format == ExplanationFormat.HTML) put("generalExplanationFormat", "HTML")
                }
                putJsonArray("exercises") {
                    for (exercise in topic.exercises) add(buildJsonObject {
                        put("id", exercise.id.toString())
                        put("task", exercise.task)
                        if (exercise.answers.keys == setOf("0")) {
                            put("answer", exercise.answers.getValue("0"))
                        } else {
                            putJsonObject("answer") { exercise.answers.forEach { (k, v) -> put(k, v) } }
                        }
                        exercise.explanation?.let { put("exampleExplanation", it) }
                    })
                }
            })
        }
    })

    private fun parse(text: String): JsonElement = try {
        Json.parseToJsonElement(text)
    } catch (e: SerializationException) {
        throw CourseFormatException("Not valid JSON: ${e.message?.lineSequence()?.firstOrNull()}")
    }

    private fun topic(node: Node, ids: MutableSet<Uuid>): Topic {
        val exercises = node.optionalArray("exercises").orEmpty()
        if (exercises.isEmpty()) node.fail("exercises", "a topic needs at least one exercise")
        return Topic(
            id = node.id(ids),
            subject = node.optionalString("subject") ?: "",
            explanation = node.optionalString("generalExplanation")?.takeIf { it.isNotEmpty() }?.let {
                Explanation(it, if (node.optionalString("generalExplanationFormat") == "HTML") ExplanationFormat.HTML else ExplanationFormat.TEXT)
            },
            exercises = exercises.map { exercise(it, ids) },
        )
    }

    private fun exercise(node: Node, ids: MutableSet<Uuid>): Exercise = Exercise(
        id = node.id(ids),
        task = node.optionalString("task") ?: node.fail("task", "missing"),
        answers = when (val answer = node.obj["answer"]) {
            null -> node.fail("answer", "missing")
            is JsonPrimitive -> mapOf("0" to node.string(answer, "answer"))
            is JsonObject -> answer.mapValues { (key, value) -> node.string(value, "answer.$key") }
                .ifEmpty { node.fail("answer", "has no blanks") }
            is JsonArray -> node.fail("answer", "expected a string or an object like {\"0\": \"…\"}")
        },
        explanation = node.optionalString("exampleExplanation")?.takeIf { it.isNotEmpty() },
    )

    /** Older files store `createdAt` as a local date-time without an offset; newer files use instants. */
    private fun parseTime(text: String, zone: TimeZone): Instant? =
        runCatching { Instant.parse(text) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(text).toInstant(zone) }.getOrNull()

    /** A JSON object together with its path in the file, for error messages. */
    private class Node(element: JsonElement, val path: String) {
        val obj: JsonObject = element as? JsonObject ?: throw CourseFormatException("$path: expected a JSON object")

        fun optionalString(key: String): String? = obj[key]?.let { string(it, key) }

        fun optionalArray(key: String): List<Node>? = obj[key]?.let { value ->
            val array = value as? JsonArray ?: fail(key, "expected a list")
            array.mapIndexed { i, item -> Node(item, "$path.$key[$i]") }
        }

        fun string(value: JsonElement, key: String): String =
            (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: fail(key, "expected a string")

        /** The node's own id if it is a UUID not seen yet in this file, otherwise a fresh one. */
        fun id(seen: MutableSet<Uuid>): Uuid {
            val own = (obj["id"] as? JsonPrimitive)?.content?.let { runCatching { Uuid.parse(it) }.getOrNull() }
            val id = own?.takeIf { it !in seen } ?: Uuid.random()
            seen += id
            return id
        }

        fun fail(key: String, problem: String): Nothing = throw CourseFormatException("$path.$key: $problem")
    }
}

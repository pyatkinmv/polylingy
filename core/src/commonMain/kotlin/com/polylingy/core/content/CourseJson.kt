package com.polylingy.core.content

import com.polylingy.content.ContentFormatException
import com.polylingy.content.ContentJson
import com.polylingy.content.Path
import com.polylingy.content.Problem
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** A course file that can't be imported; [problems] lists everything wrong with it, each with its path. */
class CourseFormatException(val problems: List<Problem>) : IllegalArgumentException(problems.joinToString("\n"))

/**
 * A course read from a file. [warnings] lists content this app doesn't know; there is some only in a file
 * of a newer format, which is then [Course.studyOnly].
 */
data class ReadCourse(val course: Course, val warnings: List<Problem>)

/** The course file: reading with every check of docs/content-format.md, writing, and storage. */
object CourseJson {
    /**
     * Reads a course file. Missing, non-UUID or repeated ids get fresh UUIDs; `createdAt` defaults to [now].
     * A file of this app's format version must be fully known; one of a newer version is read leniently.
     */
    fun read(text: String, now: Instant): ReadCourse {
        val root = upgrade(parse(text))
        val version = formatVersion(root)
        val lenient = version > FORMAT_VERSION
        val normalized = ContentJson.strict.encodeToString(JsonElement.serializer(), Filler(now).course(root))
        val course = decode(normalized, lenient)
        val problems = course.validate()
        val (unknown, errors) = problems.partition { it.kind == Problem.Kind.UNKNOWN_TYPE }
        val fatal = if (lenient) errors else problems
        if (fatal.isNotEmpty()) throw CourseFormatException(fatal)
        return if (lenient) ReadCourse(course.copy(sourceJson = normalized), unknown) else ReadCourse(course, emptyList())
    }

    /** The course as a file: indented, always in this app's format — except a study-only course, kept as it came. */
    fun write(course: Course): String {
        val source = course.sourceJson ?: return ContentJson.encode(Course.serializer(), course)
        return ContentJson.pretty.encodeToString(JsonElement.serializer(), patch(parse(source), course))
    }

    /** Reads a course this app saved; such a document is trusted and isn't checked again. */
    fun readStored(text: String): Course {
        val parsed = parse(text)
        val root = upgrade(parsed)
        val lenient = formatVersion(root) > FORMAT_VERSION
        val json = if (root === parsed) text else ContentJson.strict.encodeToString(JsonElement.serializer(), root)
        val course = decode(json, lenient)
        return if (lenient) course.copy(sourceJson = json) else course
    }

    /**
     * Brings a document of an older format up to this one; a newer or current one is returned as it is.
     * 1 → 2: `settings` moved into `rules` — `allowTypos` became `grading.typos`, `newPerDay` became `picker.newPerDay` —
     * and the pickers were renamed: `reviews-first` → `conveyor`, `random` → `shuffle`.
     * 2 → 3: only additions (exercise layers `look` and `aside`, choice places in the text, types `expand` and `fix`),
     * so the number alone changes.
     */
    internal fun upgrade(root: JsonObject): JsonObject {
        val version = (root["formatVersion"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull ?: return root
        return when (version) {
            1 -> to3(to2(root))
            2 -> to3(root)
            else -> root
        }
    }

    private fun to3(root: JsonObject) = JsonObject(root + ("formatVersion" to JsonPrimitive(3)))

    private fun to2(root: JsonObject): JsonObject {
        val settings = root["settings"] as? JsonObject
        var rules = root["rules"] as? JsonObject ?: if (settings != null) JsonObject(emptyMap()) else null
        fun JsonObject.with(key: String, value: JsonElement) = JsonObject(this + (key to value))
        rules?.let { r ->
            var picker = r["picker"] as? JsonObject
            picker?.get("type")?.let { type ->
                val renamed = mapOf("reviews-first" to "conveyor", "random" to "shuffle")[(type as? JsonPrimitive)?.content]
                if (renamed != null) picker = picker!!.with("type", JsonPrimitive(renamed))
            }
            var grading = r["grading"] as? JsonObject
            settings?.get("allowTypos")?.let { grading = (grading ?: JsonObject(mapOf("type" to JsonPrimitive("all-correct")))).with("typos", it) }
            settings?.get("newPerDay")?.let { picker = (picker ?: JsonObject(mapOf("type" to JsonPrimitive("conveyor")))).with("newPerDay", it) }
            var upgraded = r
            grading?.let { upgraded = upgraded.with("grading", it) }
            picker?.let { upgraded = upgraded.with("picker", it) }
            rules = upgraded
        }
        val rest = if (settings != null) root - "settings" else root
        return JsonObject(rest + ("formatVersion" to JsonPrimitive(2)) + (rules?.let { mapOf("rules" to it) } ?: emptyMap()))
    }

    private fun parse(text: String): JsonObject {
        val element = try {
            ContentJson.strict.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw CourseFormatException(listOf(Problem(Path.ROOT, "not valid JSON: ${e.message?.lineSequence()?.first()}")))
        }
        return element as? JsonObject ?: throw CourseFormatException(listOf(Problem(Path.ROOT, "a course file must be a JSON object")))
    }

    private fun formatVersion(root: JsonObject): Int {
        val at = Path("formatVersion")
        val value = root["formatVersion"] ?: throw CourseFormatException(listOf(Problem(at, "missing; this app writes $FORMAT_VERSION")))
        return (value as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull?.takeIf { it >= 1 }
            ?: throw CourseFormatException(listOf(Problem(at, "must be a whole number like $FORMAT_VERSION")))
    }

    private fun decode(text: String, lenient: Boolean): Course = try {
        ContentJson.decode(Course.serializer(), text, lenient)
    } catch (e: ContentFormatException) {
        throw CourseFormatException(listOf(e.problem))
    }

    /** Puts the course's ids, name, revision and time into its original document. */
    private fun patch(root: JsonObject, course: Course): JsonObject {
        fun JsonObject.with(key: String, value: JsonElement) = JsonObject(this + (key to value))
        fun JsonObject.ids(key: String, ids: List<Uuid>, inner: (JsonObject, Int) -> JsonObject = { o, _ -> o }): JsonObject {
            val array = this[key] as? JsonArray ?: return this
            return with(key, JsonArray(array.mapIndexed { i, e -> inner((e as JsonObject).with("id", JsonPrimitive(ids[i].toString())), i) }))
        }
        fun JsonObject.topicIds(key: String, topics: List<Topic>) =
            ids(key, topics.map { it.id }) { topic, t -> topic.ids("exercises", topics[t].exercises.map { it.id }) }
        return root.with("id", JsonPrimitive(course.id.toString()))
            .with("name", JsonPrimitive(course.name))
            .with("revision", JsonPrimitive(course.revision))
            .with("updatedAt", JsonPrimitive(course.updatedAt.toString()))
            .ids("units", course.units.map { it.id }) { unit, u -> unit.topicIds("topics", course.units[u].topics) }
            .topicIds("topics", course.topics)
    }

    /** Gives every object that needs an id a unique one, and sets the document fields an import resets. */
    private class Filler(private val now: Instant) {
        private val seen = mutableSetOf<Uuid>()

        fun course(root: JsonObject): JsonObject {
            val withFields = JsonObject(
                root.withId() + mapOf(
                    "createdAt" to (root["createdAt"] ?: JsonPrimitive(now.toString())),
                    "revision" to JsonPrimitive(0),
                    "updatedAt" to JsonPrimitive(now.toString()),
                ),
            )
            return withFields
                .mapKey("units") { units -> units.objects { it.withId().mapKey("topics") { topics -> topics.objects(::topic) } } }
                .mapKey("topics") { it.objects(::topic) }
        }

        private fun topic(topic: JsonObject) = topic.withId().mapKey("exercises") { exercises -> exercises.objects { it.withId() } }

        private fun JsonObject.withId(): JsonObject {
            val own = (this["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { runCatching { Uuid.parse(it) }.getOrNull() }
            val id = own?.takeIf { seen.add(it) } ?: generateSequence { Uuid.random() }.first { seen.add(it) }
            return JsonObject(mapOf("id" to JsonPrimitive(id.toString())) + (this - "id"))
        }

        private fun JsonObject.mapKey(key: String, transform: (JsonElement) -> JsonElement): JsonObject =
            this[key]?.let { JsonObject(this + (key to transform(it))) } ?: this

        private fun JsonElement.objects(transform: (JsonObject) -> JsonObject): JsonElement =
            if (this is JsonArray) JsonArray(map { (it as? JsonObject)?.let(transform) ?: it }) else this
    }
}

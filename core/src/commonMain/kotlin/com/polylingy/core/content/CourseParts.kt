package com.polylingy.core.content

import com.polylingy.content.ContentJson
import com.polylingy.content.Path
import com.polylingy.content.Problem
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A problem with a unit or topic being edited. [path] is inside the part (`exercises[1].items[0].text`) and [line]
 * is its line in the edited text, when the problem is in the part; a problem it causes elsewhere in the course
 * (a link that now leads nowhere, say) has [inPart] false and a path from the course root.
 */
data class PartProblem(val path: Path, val message: String, val line: Int?, val inPart: Boolean = true) {
    override fun toString() = (if (line != null) "Line $line · " else "") + "$path: $message"
}

/** An edited or deleted part that can't be saved; [problems] say why. */
class PartException(val problems: List<PartProblem>) : IllegalArgumentException(problems.joinToString("\n"))

/** The result of checking an edited part: the course as it would be, or what is wrong. */
data class PartCheck(val course: Course?, val problems: List<PartProblem>) {
    val ok: Boolean get() = course != null
}

/** Which part of a course is edited: a unit, or a topic of a unit (or of a flat course when [unitId] is null). */
sealed interface CoursePart {
    /** [unitId] null — a new unit, added at the end. */
    data class Unit(val unitId: Uuid?) : CoursePart

    /** [topicId] null — a new topic, added at the end of [unitId] (or of the flat course). */
    data class Topic(val unitId: Uuid?, val topicId: Uuid?) : CoursePart
}

/**
 * Units and topics as small JSON documents: what the editor shows, and how an edited one goes back into the course.
 * An edited part is put into the course's document, and the whole course is read again with every check of an
 * import (`CourseJson.read`), so a part is valid exactly when a course file with it would be.
 */
object CourseParts {
    /** The smallest topic that works; shown in grey in an empty editor. */
    val TOPIC_TEMPLATE = """
        {
          "title": "Topic title",
          "exercises": [
            {
              "type": "gaps",
              "items": [
                {
                  "text": "I {{am}} here."
                }
              ]
            }
          ]
        }
    """.trimIndent()

    /** The smallest unit that works: a title and one topic. Subtitle and theory are optional. */
    val UNIT_TEMPLATE = """
        {
          "title": "Unit title",
          "topics": [
            {
              "title": "Topic title",
              "exercises": [
                {
                  "type": "gaps",
                  "items": [
                    {
                      "text": "I {{am}} here."
                    }
                  ]
                }
              ]
            }
          ]
        }
    """.trimIndent()

    /** The part as the editor shows it: indented JSON with its ids. Null if the course has no such part. */
    fun json(course: Course, part: CoursePart): String? {
        val doc = document(course)
        val at = place(course, part) ?: return null
        if (at.index == null) return null
        val element = (doc.at(at.parent) as? JsonArray)?.getOrNull(at.index) ?: return null
        return editorJson.encodeToString(JsonElement.serializer(), element)
    }

    /** Indented by two spaces, like the templates: deep parts stay readable in a narrow editor. */
    @OptIn(ExperimentalSerializationApi::class)
    private val editorJson = Json(ContentJson.pretty) { prettyPrintIndent = "  " }

    /** Checks [text] as [part] of [course] and returns the course with it, or the problems. */
    fun check(course: Course, part: CoursePart, text: String, now: Instant): PartCheck {
        val element = try {
            ContentJson.strict.parseToJsonElement(text)
        } catch (e: SerializationException) {
            val message = e.message?.lineSequence()?.firstOrNull().orEmpty()
            val offset = Regex("offset (\\d+)").find(message)?.groupValues?.get(1)?.toIntOrNull()
            return PartCheck(null, listOf(PartProblem(Path.ROOT, "not valid JSON: ${message.substringBefore(" at path")}", offset?.let { JsonLines.lineAt(text, it) })))
        }
        if (element !is JsonObject) return PartCheck(null, listOf(PartProblem(Path.ROOT, "must be a JSON object { … }", 1)))
        val at = place(course, part) ?: return PartCheck(null, listOf(PartProblem(Path.ROOT, "this part is no longer in the course", null)))

        val doc = document(course)
        val array = doc.at(at.parent) as? JsonArray ?: JsonArray(emptyList())
        val index = at.index ?: array.size
        val replaced = JsonArray(array.toMutableList().also { if (at.index == null) it.add(element) else it[index] = element })
        val prefix = at.parent[index]
        val edited = doc.put(at.parent, replaced)

        return try {
            val read = CourseJson.read(ContentJson.strict.encodeToString(JsonElement.serializer(), edited), now).course
            PartCheck(read.copy(createdAt = course.createdAt, revision = course.revision, updatedAt = course.updatedAt), emptyList())
        } catch (e: CourseFormatException) {
            val lines = JsonLines.of(text)
            PartCheck(null, e.problems.map { problem -> problem.within(prefix, lines) })
        }
    }

    /** The course without [part]; the problems if the rest of the course needs it (a unit's last topic, a link into it). */
    fun remove(course: Course, part: CoursePart, now: Instant): PartCheck {
        val at = place(course, part)?.takeIf { it.index != null } ?: return PartCheck(null, listOf(PartProblem(Path.ROOT, "this part is no longer in the course", null)))
        val doc = document(course)
        val array = doc.at(at.parent) as JsonArray
        val edited = doc.put(at.parent, JsonArray(array.filterIndexed { i, _ -> i != at.index }))
        return try {
            val read = CourseJson.read(ContentJson.strict.encodeToString(JsonElement.serializer(), edited), now).course
            PartCheck(read.copy(createdAt = course.createdAt, revision = course.revision, updatedAt = course.updatedAt), emptyList())
        } catch (e: CourseFormatException) {
            PartCheck(null, e.problems.map { PartProblem(it.path, it.message, null, inPart = false) })
        }
    }

    /** Topics of [unitId] in [before] that are gone in [after]: an edited unit dropped them, so they will be deleted. */
    fun droppedTopics(before: Course, after: Course, unitId: Uuid): List<Topic> {
        val kept = after.units.firstOrNull { it.id == unitId }?.topics.orEmpty().mapTo(mutableSetOf()) { it.id }
        return before.units.firstOrNull { it.id == unitId }?.topics.orEmpty().filter { it.id !in kept }
    }

    /** Where a part sits in the document: the path of its array and its index there (null — a new one, at the end). */
    private data class Place(val parent: Path, val index: Int?)

    private fun place(course: Course, part: CoursePart): Place? = when (part) {
        is CoursePart.Unit -> {
            if (part.unitId == null) Place(Path("units"), null)
            else course.units.indexOfFirst { it.id == part.unitId }.takeIf { it >= 0 }?.let { Place(Path("units"), it) }
        }
        is CoursePart.Topic -> {
            val parent = if (part.unitId == null) Path("topics")
            else course.units.indexOfFirst { it.id == part.unitId }.takeIf { it >= 0 }?.let { (Path("units")[it]) / "topics" } ?: return null
            val topics = if (part.unitId == null) course.topics else course.units.first { it.id == part.unitId }.topics
            if (part.topicId == null) Place(parent, null)
            else topics.indexOfFirst { it.id == part.topicId }.takeIf { it >= 0 }?.let { Place(parent, it) }
        }
    }

    private fun document(course: Course): JsonObject =
        ContentJson.strict.parseToJsonElement(CourseJson.write(course)) as JsonObject

    /** The problem as the editor shows it: relative to the part when it is inside it, with its line. */
    private fun Problem.within(prefix: Path, lines: Map<String, Int>): PartProblem {
        val full = path.text
        val own = prefix.text
        return when {
            full == own -> PartProblem(Path.ROOT, message, Regex("unknown key '([^']+)'").find(message)?.groupValues?.get(1)?.let { lines[it] } ?: 1)
            full.startsWith("$own.") || full.startsWith("$own[") -> {
                val inner = Path(full.removePrefix(own).removePrefix("."))
                // An unknown key is shown on its own line rather than where its object starts.
                val key = Regex("unknown key '([^']+)'").find(message)?.groupValues?.get(1)
                val line = key?.let { lines[if (inner.text.isEmpty()) it else "${inner.text}.$it"] } ?: JsonLines.nearest(lines, inner.text)
                PartProblem(inner, message, line)
            }
            else -> PartProblem(path, message, null, inPart = false)
        }
    }

    // Navigating the course document by paths like `units[1].topics`.

    private fun JsonElement.at(path: Path): JsonElement? = steps(path).fold(this as JsonElement?) { e, step ->
        when (step) {
            is Int -> (e as? JsonArray)?.getOrNull(step)
            else -> (e as? JsonObject)?.get(step as String)
        }
    }

    private fun JsonObject.put(path: Path, value: JsonElement): JsonObject = putAt(this, steps(path), value) as JsonObject

    private fun putAt(e: JsonElement, steps: List<Any>, value: JsonElement): JsonElement {
        if (steps.isEmpty()) return value
        val step = steps.first()
        return when (step) {
            is Int -> JsonArray((e as JsonArray).toMutableList().also { it[step] = putAt(it[step], steps.drop(1), value) })
            else -> {
                val obj = e as JsonObject
                val key = step as String
                JsonObject(obj + (key to putAt(obj[key] ?: JsonObject(emptyMap()), steps.drop(1), value)))
            }
        }
    }

    private fun steps(path: Path): List<Any> =
        Regex("([^.\\[\\]]+)|\\[(\\d+)]").findAll(path.text).map { m -> m.groupValues[2].toIntOrNull() ?: m.groupValues[1] }.toList()
}

/** Lines of values in a JSON text, by path (`exercises[1].items[0].text`), for showing problems next to their line. */
internal object JsonLines {
    fun lineAt(text: String, offset: Int): Int = text.take(offset.coerceIn(0, text.length)).count { it == '\n' } + 1

    /** The line where each value starts, by its path; the root is "". */
    fun of(text: String): Map<String, Int> {
        val lines = mutableMapOf<String, Int>()
        var i = 0
        fun line() = lineAt(text, i)
        fun skip() { while (i < text.length && text[i].isWhitespace()) i++ }
        fun string(): String {
            val sb = StringBuilder()
            i++ // opening quote
            while (i < text.length && text[i] != '"') {
                if (text[i] == '\\' && i + 1 < text.length) { sb.append(text[i + 1]); i += 2 } else { sb.append(text[i]); i++ }
            }
            i++
            return sb.toString()
        }
        fun value(path: String) {
            skip()
            if (i >= text.length) return
            lines.putIfAbsent(path, line())
            when (text[i]) {
                '{' -> {
                    i++
                    while (true) {
                        skip()
                        if (i >= text.length || text[i] == '}') { i++; return }
                        if (text[i] == ',') { i++; continue }
                        if (text[i] != '"') { i++; continue }
                        val keyLine = line()
                        val key = string()
                        val child = if (path.isEmpty()) key else "$path.$key"
                        lines.putIfAbsent(child, keyLine)
                        skip()
                        if (i < text.length && text[i] == ':') i++
                        value(child)
                    }
                }
                '[' -> {
                    i++
                    var n = 0
                    while (true) {
                        skip()
                        if (i >= text.length || text[i] == ']') { i++; return }
                        if (text[i] == ',') { i++; continue }
                        value("$path[$n]")
                        n++
                    }
                }
                '"' -> string()
                else -> while (i < text.length && text[i] !in ",}] \n\r\t") i++
            }
        }
        runCatching { value("") }
        return lines
    }

    /** The line of [path], or of the nearest enclosing value that has one. */
    fun nearest(lines: Map<String, Int>, path: String): Int? {
        var p = path
        while (true) {
            lines[p]?.let { return it }
            if (p.isEmpty()) return null
            p = when {
                p.endsWith("]") -> p.substringBeforeLast("[")
                p.contains(".") -> p.substringBeforeLast(".")
                else -> ""
            }
        }
    }
}

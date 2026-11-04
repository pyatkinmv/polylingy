package com.polylingy.core.content

import com.polylingy.content.ContentJson
import com.polylingy.content.Exercise
import com.polylingy.core.FixedClock
import com.polylingy.core.InMemoryCourseRepository
import com.polylingy.core.InMemoryCourseSettings
import com.polylingy.core.at
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class CoursePartsTest {
    private val clock = FixedClock(at(LocalDate(2026, 11, 3)))
    private val repository = InMemoryCourseRepository()
    private val service = CourseService(repository, InMemoryCourseSettings(), clock)
    private val course = service.import(CourseJson.read(CourseSamples.units, clock.now().instant).course)
    private val unit = course.units.single()
    private val topic = unit.topics[0]

    private fun topicPart(id: Uuid? = topic.id) = CoursePart.Topic(unit.id, id)

    @Test fun aTopicIsShownWithItsIdsAndGoesBackUnchanged() {
        val json = service.partJson(course.id, topicPart())!!
        assertContains(json, "\"id\": \"${topic.id}\"")
        assertContains(json, "\"id\": \"${topic.exercises[0].id}\"")
        val check = service.checkPart(course.id, topicPart(), json)
        assertTrue(check.ok, check.problems.toString())
        assertEquals(course.units, check.course!!.units)
    }

    @Test fun editingASentenceKeepsTheIdsSoProgressStays() {
        val json = service.partJson(course.id, topicPart())!!.replace("Listen! Somebody", "Hush! Somebody")
        val saved = service.savePart(course.id, topicPart(), json)
        val edited = saved.units.single().topics[0]
        assertEquals(topic.id, edited.id)
        assertEquals(topic.exercises.map { it.id }, edited.exercises.map { it.id })
        assertContains(((edited.exercises[0] as Exercise.Gaps).items[0].text.text), "Hush!")
        assertEquals(course.revision + 1, saved.revision)
        assertEquals(course.createdAt, saved.createdAt)
    }

    @Test fun theTemplatesAreValidNewParts() {
        val withTopic = service.savePart(course.id, topicPart(null), CourseParts.TOPIC_TEMPLATE)
        assertEquals(listOf("Things happening now", "Questions", "Topic title"), withTopic.units.single().topics.map { it.title })
        val withUnit = service.savePart(course.id, CoursePart.Unit(null), CourseParts.UNIT_TEMPLATE)
        assertEquals(listOf("Present continuous", "Unit title"), withUnit.units.map { it.title })
        assertEquals(1, withUnit.units[1].topics.size)
        assertTrue(withUnit.ids().toSet().size == withUnit.ids().size, "fresh, unique ids")
    }

    @Test fun aTopicOfAFlatCourseIsAddedToTheCourse() {
        val flat = service.import(CourseJson.read(CourseSamples.flat, clock.now().instant).course)
        val saved = service.savePart(flat.id, CoursePart.Topic(null, null), CourseParts.TOPIC_TEMPLATE)
        assertEquals(3, saved.topics.size)
    }

    @Test fun problemsAreGivenInsideThePartWithTheirLines() {
        val broken = CourseParts.TOPIC_TEMPLATE.replace("\"items\"", "\"itmes\"")
        val unknown = service.checkPart(course.id, topicPart(null), broken).problems.single()
        assertEquals("exercises[0]", unknown.path.text)
        assertEquals(6, unknown.line, "the line of the unknown key")
        assertTrue(unknown.inPart)

        val blank = service.checkPart(course.id, topicPart(null), CourseParts.TOPIC_TEMPLATE.replace("{{am}}", "{{am")).problems
            .first { "not closed" in it.message }
        assertEquals("exercises[0].items[0].text", blank.path.text)
        assertEquals(8, blank.line)
        assertEquals("Line 8 · exercises[0].items[0].text: ${blank.message}", blank.toString())
    }

    @Test fun textThatIsNotJsonPointsAtItsLine() {
        val problem = service.checkPart(course.id, topicPart(null), "{\n  \"title\": \"T\",\n  oops\n}").problems.single()
        assertContains(problem.message, "not valid JSON")
        assertEquals(3, problem.line)
        assertEquals(1, service.checkPart(course.id, topicPart(null), "[]").problems.single().line)
    }

    @Test fun deletingATopicLeavesTheOthers() {
        val saved = service.deletePart(course.id, topicPart())
        assertEquals(listOf("Questions"), saved.units.single().topics.map { it.title })
    }

    @Test fun aUnitKeepsAtLeastOneTopicAndTheCourseAtLeastOneUnit() {
        val once = service.deletePart(course.id, topicPart())
        val last = assertFailsWith<PartException> { service.deletePart(course.id, CoursePart.Topic(unit.id, once.units.single().topics.single().id)) }
        assertEquals("units[0].topics: needs at least one topic", last.problems.single().let { "${it.path}: ${it.message}" })
        assertFalse(last.problems.single().inPart)
        assertFailsWith<PartException> { service.deletePart(course.id, CoursePart.Unit(unit.id)) }
    }

    @Test fun anEditedUnitTellsWhichTopicsItDropped() {
        val json = service.partJson(course.id, CoursePart.Unit(unit.id))!!
        val tree = ContentJson.strict.parseToJsonElement(json).jsonObject
        val withoutQuestions = ContentJson.pretty.encodeToString(
            JsonElement.serializer(),
            JsonObject(tree + ("topics" to JsonArray(tree.getValue("topics").jsonArray.take(1)))),
        )
        val check = service.checkPart(course.id, CoursePart.Unit(unit.id), withoutQuestions)
        assertTrue(check.ok, check.problems.toString())
        assertEquals(listOf("Questions"), CourseParts.droppedTopics(course, check.course!!, unit.id).map { it.title })
    }

    @Test fun aGoneOrStudyOnlyCourseCantBeChanged() {
        assertNull(service.partJson(course.id, topicPart(Uuid.random())))
        assertFalse(service.checkPart(course.id, topicPart(Uuid.random()), "{}").ok)
        val newer = service.import(CourseJson.read(CourseSamples.flat.replace("\"formatVersion\": 1", "\"formatVersion\": 4"), clock.now().instant).course)
        assertFalse(service.checkPart(newer.id, CoursePart.Topic(null, null), CourseParts.TOPIC_TEMPLATE).ok)
        assertFailsWith<PartException> { service.deletePart(newer.id, CoursePart.Topic(null, newer.topics[0].id)) }
    }

    @Test fun linesOfJsonValues() {
        val lines = JsonLines.of("{\n  \"a\": [\n    { \"b\": \"x\\\"y\" },\n    2\n  ],\n  \"c\": true\n}")
        assertEquals(1, lines[""])
        assertEquals(2, lines["a"])
        assertEquals(3, lines["a[0].b"])
        assertEquals(4, lines["a[1]"])
        assertEquals(6, lines["c"])
        assertEquals(3, JsonLines.nearest(lines, "a[0].b.zzz"))
        assertNotNull(JsonLines.nearest(lines, "nothing"))
    }
}

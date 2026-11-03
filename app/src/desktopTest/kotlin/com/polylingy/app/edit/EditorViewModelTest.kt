package com.polylingy.app.edit

import com.polylingy.app.testContainer
import com.polylingy.client.CourseSource
import com.polylingy.content.Exercise
import com.polylingy.content.ItemResult
import com.polylingy.content.Response
import com.polylingy.core.content.CourseParts
import com.polylingy.core.content.CoursePart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorViewModelTest {
    private val json = """{ "formatVersion": 1, "name": "Grammar",
        "units": [
          { "title": "Present continuous",
            "theory": { "format": "blocks", "blocks": [ { "type": "text", "text": "He**’s cycling** to work." } ] },
            "topics": [
              { "title": "Now", "exercises": [
                { "type": "gaps", "items": [ { "text": "Somebody {{is singing}}." } ] },
                { "type": "choice", "items": [ { "text": "What {{are you doing}}?", "options": ["do you do", "are you doing"] } ] } ] },
              { "title": "Questions", "exercises": [ { "type": "gaps", "items": [ { "text": "{{Are}} you OK?" } ] } ] } ] },
          { "title": "Present simple", "topics": [ { "title": "Habits", "exercises": [ { "type": "gaps", "items": [ { "text": "She {{works}}." } ] } ] } ] } ] }"""

    private val container = testContainer()
    private val course = container.library.import(CourseSource("c.json", json)).course
    private val unit = course.units[0]

    private fun editor(part: CoursePart) = EditorViewModel(container.courseService, container.study, container.storage.courses, course.id, part)

    @Test fun aNewTopicStartsEmptyWithItsTemplate() {
        val vm = editor(CoursePart.Topic(unit.id, null))
        assertTrue(vm.isNew)
        assertEquals("", vm.text.value)
        assertNull(vm.check.value)
        assertNull(vm.edited.value)
        assertEquals(CourseParts.TOPIC_TEMPLATE, vm.template)

        vm.useTemplate()
        assertTrue(vm.check.value!!.ok)
        assertEquals("Topic title", vm.edited.value!!.title)
        assertIs<SaveOutcome.Saved>(vm.save())
        assertEquals(listOf("Now", "Questions", "Topic title"), container.storage.courses.get(course.id)!!.units[0].topics.map { it.title })
    }

    @Test fun anEditedTopicShowsItsJsonAndPreviewsItsExercises() {
        val vm = editor(CoursePart.Topic(unit.id, unit.topics[0].id))
        assertFalse(vm.isNew)
        assertFalse(vm.changed)
        assertEquals(0, vm.preview.value.topic)
        assertIs<Exercise.Gaps>(vm.preview.value.shown)

        vm.showExercise(1)
        assertIs<Exercise.Choice>(vm.preview.value.shown)
        vm.answer(Response.Choice(listOf("are you doing")))
        vm.checkAnswer()
        assertEquals(listOf(ItemResult.CORRECT), vm.preview.value.checks!!.map { it.result })
        vm.tryAgain()
        assertNull(vm.preview.value.checks)
        assertTrue(container.storage.journal.all().isEmpty(), "the preview never writes to the journal")
    }

    @Test fun problemsKeepTheLastValidPreviewAndBlockSaving() {
        val vm = editor(CoursePart.Topic(unit.id, unit.topics[0].id))
        vm.change(vm.text.value.replace("\"Now\"", "\"Right now\""))
        assertEquals("Right now", vm.edited.value!!.title)
        vm.change(vm.text.value.replace("\"items\"", "\"itmes\""))
        val check = vm.check.value!!
        assertFalse(check.ok)
        assertTrue(check.problems.all { it.line != null })
        assertEquals("Right now", vm.edited.value!!.title, "the preview stays on the last valid text")
        assertIs<SaveOutcome.Failed>(vm.save())
    }

    @Test fun aChangedExerciseDropsWhatWasEnteredInThePreview() {
        val vm = editor(CoursePart.Topic(unit.id, unit.topics[0].id))
        vm.answer(Response.Blanks(listOf("is sing")))
        assertEquals(Response.Blanks(listOf("is sing")), vm.preview.value.response)
        vm.change(vm.text.value.replace("Somebody", "Listen! Somebody"))
        assertEquals(Response.Blanks(listOf("")), vm.preview.value.response)
    }

    @Test fun aUnitPreviewsItsTheoryFirstThenEachTopic() {
        val vm = editor(CoursePart.Unit(unit.id))
        assertNull(vm.preview.value.topic, "the theory")
        assertNull(vm.preview.value.shown)
        vm.showTopic(1)
        assertEquals(1, vm.preview.value.topic)
        assertIs<Exercise.Gaps>(vm.preview.value.shown)
        vm.showTheory()
        assertNull(vm.preview.value.topic)
    }

    @Test fun aUnitThatDropsATopicAsksFirst() {
        val vm = editor(CoursePart.Unit(unit.id))
        val questions = vm.text.value.indexOf("    {\n      \"id\": \"${unit.topics[1].id}\"")
        val edited = vm.text.value.substring(0, questions).trimEnd().removeSuffix(",") + "\n  ]\n}"
        vm.change(edited)
        assertTrue(vm.check.value!!.ok, vm.check.value!!.problems.toString())
        val ask = assertIs<SaveOutcome.ConfirmDropped>(vm.save())
        assertEquals(listOf("Questions"), ask.topics.map { it.title })
        assertEquals(2, container.storage.courses.get(course.id)!!.units[0].topics.size, "nothing saved yet")
        assertIs<SaveOutcome.Saved>(vm.save(confirmed = true))
        assertEquals(listOf("Now"), container.storage.courses.get(course.id)!!.units[0].topics.map { it.title })
    }

    @Test fun aNewUnitGoesAtTheEnd() {
        val vm = editor(CoursePart.Unit(null))
        vm.useTemplate()
        assertEquals("Unit title", vm.edited.value!!.title)
        assertIs<SaveOutcome.Saved>(vm.save())
        assertEquals(listOf("Present continuous", "Present simple", "Unit title"), container.storage.courses.get(course.id)!!.units.map { it.title })
    }

    @Test fun clearingTheTextEmptiesThePreview() {
        val vm = editor(CoursePart.Topic(unit.id, null))
        vm.useTemplate()
        vm.change("")
        assertNull(vm.edited.value)
        assertNull(vm.preview.value.shown)
        assertIs<SaveOutcome.Failed>(vm.save())
    }
}

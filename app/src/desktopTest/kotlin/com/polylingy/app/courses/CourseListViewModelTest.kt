package com.polylingy.app.courses

import com.polylingy.app.MovableClock
import com.polylingy.app.PickedFile
import com.polylingy.app.testContainer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CourseListViewModelTest {
    private val clock = MovableClock()
    private val container = testContainer(clock)
    private val vm = CourseListViewModel(container.library, container.courseService, container.storage.courses, container.study, container.storage::brokenCourses)

    @AfterTest fun close() = container.storage.close()

    private val names get() = vm.courses.value.map { it.course.name }

    private fun file(name: String) = PickedFile("$name.json", """{ "formatVersion": 1, "name": "$name",
        "topics": [ { "title": "T", "exercises": [ { "type": "gaps", "items": [ { "text": "{{a}}" } ] } ] } ] }""".toByteArray())

    @Test fun importedCourseIsListed() {
        val outcome = assertIs<ImportOutcome.Imported>(vm.import(file("Spanish")))
        assertEquals(emptyList(), outcome.warnings)
        assertEquals(listOf("Spanish"), names)
    }

    @Test fun aBrokenFileFailsWithItsProblems() {
        val outcome = assertIs<ImportOutcome.Failed>(vm.import(PickedFile("x.json", "{}".toByteArray())))
        assertEquals("x.json", outcome.fileName)
        assertEquals(listOf("formatVersion: missing; this app writes 3"), outcome.problems.map { it.toString() })
        assertEquals(emptyList(), names)
    }

    @Test fun editDetailsAndDelete() {
        vm.import(file("Old"))
        val id = vm.courses.value.single().course.id
        assertTrue(vm.editDetails(id, "New", "Short *note*").isSuccess)
        assertEquals(listOf("New"), names)
        assertEquals("Short *note*", vm.courses.value.single().course.description?.text)
        assertEquals("** at 1 is not closed", vm.editDetails(id, "New", "**x").exceptionOrNull()?.message)
        vm.delete(id)
        assertEquals(emptyList(), names)
    }

    @Test fun eachCourseComesWithItsCounts() {
        vm.import(file("Spanish"))
        val counts = vm.courses.value.single().counts
        assertEquals(1 to 0, counts.new to counts.learned)
    }

    @Test fun exportThenImportAddsACopy() {
        vm.import(file("Spanish Basics"))
        val exported = vm.export(vm.courses.value.single().course.id).getOrThrow()
        assertEquals("spanish_basics.json", exported.fileName)
        vm.import(PickedFile(exported.fileName, exported.bytes))
        assertEquals(listOf("Spanish Basics", "Spanish Basics (2)"), names)
    }

    @Test fun exportOfAMissingCourseFails() = assertTrue(vm.export(kotlin.uuid.Uuid.random()).isFailure)
}

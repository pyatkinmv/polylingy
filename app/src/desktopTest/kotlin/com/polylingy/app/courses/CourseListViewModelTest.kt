package com.polylingy.app.courses

import com.polylingy.app.MovableClock
import com.polylingy.app.testContainer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class CourseListViewModelTest {
    private val clock = MovableClock()
    private val container = testContainer(clock)
    private val vm = CourseListViewModel(container.studyService, container.courseService, container.storage.courses, clock)

    @AfterTest fun close() = container.storage.close()

    private val names get() = vm.courseStats.value.map { it.course.name }

    private val topicJson = """{"subject": "Greetings", "exercises": [{"task": "hello", "answer": "hola"}]}"""

    @Test fun createdCourseAppearsWithNothingToStudy() {
        vm.create("Spanish")
        val stats = vm.courseStats.value.single()
        assertEquals("Spanish", stats.course.name)
        assertEquals(0, stats.eligible)
    }

    @Test fun renameAndDelete() {
        vm.create("Old")
        val id = vm.courseStats.value.single().course.id
        vm.rename(id, "New")
        assertEquals(listOf("New"), names)
        vm.delete(id)
        assertEquals(emptyList(), names)
    }

    @Test fun addedTopicIsCountedAsNew() {
        vm.create("Spanish")
        val id = vm.courseStats.value.single().course.id
        assertNull(vm.addTopic(id, topicJson))
        assertEquals(1, vm.courseStats.value.single().newCount)
    }

    @Test fun invalidTopicJsonReturnsAReadableError() {
        vm.create("Spanish")
        val id = vm.courseStats.value.single().course.id
        assertContains(vm.addTopic(id, """{"subject": "x", "exercises": []}""")!!, "at least one exercise")
        assertContains(vm.addTopic(id, "not json")!!, "Not valid JSON")
        assertEquals(0, vm.courseStats.value.single().newCount)
    }

    @Test fun exportThenImportAddsACopy() {
        vm.create("Spanish Basics")
        val id = vm.courseStats.value.single().course.id
        vm.addTopic(id, topicJson)
        val exported = vm.export(id)!!
        assertEquals("spanish_basics.json", exported.fileName)

        assertNull(vm.import(exported.json))
        assertEquals(listOf("Spanish Basics", "Spanish Basics"), names)
        assertNotEquals(vm.courseStats.value[0].course.id, vm.courseStats.value[1].course.id)
    }

    @Test fun importErrorIsReturned() = assertContains(vm.import("[]")!!, "expected a JSON object")
}

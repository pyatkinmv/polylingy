package com.polylingy.core.content

import com.polylingy.core.FixedClock
import com.polylingy.core.InMemoryCourseRepository
import com.polylingy.core.at
import com.polylingy.core.course
import com.polylingy.core.topic
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CourseServiceTest {
    private val clock = FixedClock(at(LocalDate(2026, 1, 15)))
    private val repo = InMemoryCourseRepository()
    private val service = CourseService(repo, clock)

    @Test fun createdCourseIsStoredAtRevisionOne() {
        val course = service.create("  Spanish  ")
        assertEquals("Spanish", course.name)
        assertEquals(1, course.revision)
        assertEquals(clock.now().instant, course.createdAt)
        assertEquals(course, repo.get(course.id))
    }

    @Test fun blankNameIsRejected() {
        assertFailsWith<IllegalArgumentException> { service.create("   ") }
    }

    @Test fun nameLongerThanFiftyIsRejected() {
        service.create("x".repeat(50))
        assertFailsWith<IllegalArgumentException> { service.create("x".repeat(51)) }
    }

    @Test fun everyEditBumpsRevisionAndUpdatedAt() {
        val created = service.create("A")
        clock.set(LocalDate(2026, 1, 16))
        val renamed = service.rename(created.id, "B")
        assertEquals(2, renamed.revision)
        assertEquals(clock.now().instant, renamed.updatedAt)
        assertEquals(created.createdAt, renamed.createdAt)
    }

    @Test fun addAndDeleteTopic() {
        val course = service.create("A")
        val t = topic()
        assertEquals(listOf(t), service.addTopic(course.id, t).topics)
        assertEquals(emptyList(), service.deleteTopic(course.id, t.id).topics)
        assertEquals(3, repo.get(course.id)!!.revision)
    }

    @Test fun deletingATopicKeepsTheOthersUnchanged() {
        val (a, b, c) = listOf(topic("a"), topic("b"), topic("c"))
        val course = service.import(course(a, b, c))
        assertEquals(listOf(a, c), service.deleteTopic(course.id, b.id).topics)
    }

    @Test fun topicWithoutExercisesIsRejected() {
        val course = service.create("A")
        assertFailsWith<IllegalArgumentException> { service.addTopic(course.id, topic(exercises = emptyList())) }
    }

    @Test fun deleteRemovesTheCourse() {
        val course = service.create("A")
        service.delete(course.id)
        assertNull(repo.get(course.id))
    }

    @Test fun importKeepsIdsOfANewCourse() {
        val incoming = course(topic())
        val imported = service.import(incoming)
        assertEquals(incoming.id, imported.id)
        assertEquals(incoming.topics, imported.topics)
        assertEquals(1, imported.revision)
    }

    @Test fun importingAnExistingCourseAgainMakesACopyWithFreshIds() {
        val incoming = course(topic())
        service.import(incoming)
        val copy = service.import(incoming)
        assertNotEquals(incoming.id, copy.id)
        assertNotEquals(incoming.topics.single().id, copy.topics.single().id)
        assertNotEquals(incoming.topics.single().exercises.single().id, copy.topics.single().exercises.single().id)
        assertTrue(repo.all().size == 2)
    }
}

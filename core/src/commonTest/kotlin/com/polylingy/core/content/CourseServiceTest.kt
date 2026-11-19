package com.polylingy.core.content

import com.polylingy.content.Block
import com.polylingy.content.Content
import com.polylingy.content.withId
import com.polylingy.core.FixedClock
import com.polylingy.core.InMemoryCourseRepository
import com.polylingy.core.InMemoryCourseSettings
import com.polylingy.core.at
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class CourseServiceTest {
    private val clock = FixedClock(at(LocalDate(2026, 11, 1)))
    private val repository = InMemoryCourseRepository()
    private val settings = InMemoryCourseSettings()
    private val service = CourseService(repository, settings, clock)

    private fun file(name: String = "Spanish Basics", sample: String = CourseSamples.flat) =
        CourseJson.read(sample.replace("\"Spanish Basics\"", "\"$name\""), clock.now().instant).course

    @Test fun importKeepsTheVersionOfTheFileAndAFileWithoutOneIsVersionOne() {
        val course = service.import(file())
        assertEquals(1, course.version)
        assertEquals(7, service.import(file("Seven").copy(version = 7)).version, "an import keeps the version of its file")
        assertEquals(clock.now().instant, course.updatedAt)
        assertEquals(course, repository.get(course.id))
    }

    @Test fun importingTheSameFileTwiceMakesACopyWithFreshIdsAndANumberedName() {
        val sample = CourseJson.read(CourseSamples.units, clock.now().instant).course
        val first = service.import(sample)
        val second = service.import(sample)
        assertEquals("English Grammar in Use", first.name)
        assertEquals("English Grammar in Use (2)", second.name)
        assertTrue(first.ids().intersect(second.ids().toSet()).isEmpty())
        assertEquals(first.allTopics.map { it.title }, second.allTopics.map { it.title })
        assertEquals("English Grammar in Use (3)", service.import(sample).name)
    }

    @Test fun aCopyLinksToItsOwnUnits() {
        val sample = CourseJson.read(CourseSamples.units, clock.now().instant).course
        val copy = sample.withFreshIds()
        val theory = copy.units.single().theory as Content.Blocks
        val links = ((theory.blocks[1] as Block.Section).blocks.single() as Block.SeeAlso).links
        assertEquals(copy.units.single().id, links[1].unit)
        assertEquals(Uuid.parse(CourseSamples.SECTION_A), links[0].section, "section ids stay")
        assertEquals(emptyList(), copy.validate())
    }

    @Test fun aCourseSharingAnyIdWithAnotherIsCopied() {
        val first = service.import(file())
        val sharesATopic = file("Other").let { it.copy(topics = listOf(first.topics[0]) + it.topics.drop(1)) }
        val imported = service.import(sharesATopic)
        assertNotEquals(first.topics[0].id, imported.topics[0].id)
        assertEquals("Other", imported.name)
    }

    @Test fun aNewerFileOfTheSameCourseReplacesItsContentAndKeepsIdsAndSettings() {
        val stored = service.import(file().copy(version = 1))
        val settingsBefore = settings.of(stored)
        val edited = stored.topics[0].copy(title = "Fixed title")
        val added = stored.topics[0].copy(id = Uuid.random(), title = "A new topic", exercises = stored.topics[0].exercises.map { it.withId(Uuid.random()) })
        val updated = service.update(stored.id, stored.copy(version = 2, name = "Spanish Basics 2", topics = listOf(edited) + stored.topics.drop(1) + added))
        assertEquals(stored.id, updated.id)
        assertEquals(2, updated.version, "after an update the course has the version of the file")
        assertEquals(stored.createdAt, updated.createdAt)
        assertEquals("Spanish Basics 2", updated.name)
        assertEquals(stored.topics[0].id, updated.topics[0].id, "what keeps its id keeps its progress")
        assertEquals("Fixed title", updated.topics[0].title)
        assertEquals(stored.topics.size + 1, updated.topics.size)
        assertEquals(updated, repository.get(stored.id))
        assertEquals(settingsBefore, settings.of(updated), "the settings are not the file's business")
    }

    @Test fun aFileThatIsNotNewerIsRefused() {
        val stored = service.import(file().copy(version = 3))
        for (version in listOf(1, 2, 3)) {
            val e = assertFailsWith<CourseUpdateException> { service.update(stored.id, stored.copy(version = version)) }
            assertTrue("already at 3" in e.message!!, e.message)
        }
        assertEquals(stored, repository.get(stored.id), "a refused update changes nothing")
    }

    @Test fun aCourseWithoutAVersionIsVersionOneAndAnyHigherFileUpdatesIt() {
        val stored = service.import(file())
        assertEquals(1, stored.version)
        assertEquals(2, service.update(stored.id, stored.copy(version = 2)).version)
    }

    @Test fun aFileOfAnotherCourseOrWithAnotherCoursesIdsIsRefused() {
        val first = service.import(file("First").copy(version = 1))
        val second = service.import(file("Second").withFreshIds())
        assertFailsWith<CourseUpdateException> { service.update(first.id, second.copy(version = 5)) }
        val sharing = first.copy(version = 2, topics = listOf(second.topics[0]) + first.topics.drop(1))
        val e = assertFailsWith<CourseUpdateException> { service.update(first.id, sharing) }
        assertTrue("another course" in e.message!!, e.message)
        assertEquals(first, repository.get(first.id))
    }

    @Test fun aVersionIsReadFromTheFileAndWrittenBack() {
        val withVersion = CourseSamples.flat.replaceFirst("\"formatVersion\":", "\"version\": 7, \"formatVersion\":")
        val course = CourseJson.read(withVersion, clock.now().instant).course
        assertEquals(7, course.version)
        assertEquals(7, CourseJson.read(CourseJson.write(course), clock.now().instant).course.version)
        assertTrue("\"version\": 1" in CourseJson.write(file()), "an export always says the version")
    }

    @Test fun namesAreComparedIgnoringCase() {
        service.import(file("French"))
        assertEquals("FRENCH (2)", service.import(file("FRENCH")).name)
    }

    @Test fun aNumberedLongNameStaysWithinFiftyCharacters() {
        val long = "x".repeat(50)
        service.import(file(long))
        val second = service.import(file(long))
        assertEquals("x".repeat(46) + " (2)", second.name)
    }

    @Test fun editDetailsBumpsTheVersionAndKeepsNamesUnique() {
        val french = service.import(file("French"))
        val spanish = service.import(file("Spanish"))
        assertEquals("french (2)", service.editDetails(spanish.id, "  french ", "").name)
        val edited = service.editDetails(french.id, "French", "  Verbs and *nouns*. ")
        assertEquals("French", edited.name)
        assertEquals("Verbs and *nouns*.", edited.description?.text)
        assertEquals(2, edited.version)
        assertNull(service.editDetails(french.id, "French", " ").description, "a blank description is removed")
    }

    @Test fun editDetailsChecksNameAndDescription() {
        val course = service.import(file())
        assertFailsWith<IllegalArgumentException> { service.editDetails(course.id, " ", "") }
        assertFailsWith<IllegalArgumentException> { service.editDetails(course.id, "x".repeat(51), "") }
        assertFailsWith<IllegalArgumentException> { service.editDetails(kotlin.uuid.Uuid.random(), "Y", "") }
        val markup = assertFailsWith<IllegalArgumentException> { service.editDetails(course.id, "Y", "**open") }
        assertEquals("** at 1 is not closed", markup.message)
        assertFailsWith<IllegalArgumentException> { service.editDetails(course.id, "Y", "a {{blank}}") }
    }

    @Test fun aCourseFromANewerFormatCantBeEdited() {
        val newer = CourseJson.read(CourseSamples.flat.replace("\"formatVersion\": 1", "\"formatVersion\": 14"), clock.now().instant).course
        val course = service.import(newer)
        assertFailsWith<IllegalArgumentException> { service.editDetails(course.id, "Other", "") }
    }

    @Test fun deleteRemovesTheCourse() {
        val course = service.import(file())
        service.delete(course.id)
        assertNull(repository.get(course.id))
    }

    @Test fun validNameTrims() = assertEquals("Spanish", CourseService.validName("  Spanish  "))
}

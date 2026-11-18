package com.polylingy.core.content

import com.polylingy.content.Content
import com.polylingy.content.Exercise
import com.polylingy.content.Problem
import com.polylingy.core.content.CourseSamples.COURSE_ID
import com.polylingy.core.content.CourseSamples.EXERCISE_ID
import com.polylingy.core.content.CourseSamples.SECTION_A
import com.polylingy.core.content.CourseSamples.SECTION_B
import com.polylingy.core.content.CourseSamples.TOPIC_ID
import com.polylingy.core.content.CourseSamples.UNIT_ID
import com.polylingy.core.content.CourseSamples.flatWith
import com.polylingy.core.content.CourseSamples.flatWithExercise
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class CourseJsonTest {
    private val now = Instant.parse("2026-11-01T10:00:00Z")

    private fun read(text: String) = CourseJson.read(text, now)
    private fun problems(text: String): List<String> = assertFailsWith<CourseFormatException> { read(text) }.problems.map { it.toString() }

    // reading

    @Test fun aCourseWithUnitsIsRead() {
        val (course, warnings) = read(CourseSamples.units)
        assertEquals(emptyList(), warnings)
        assertEquals(Uuid.parse(COURSE_ID), course.id)
        assertEquals("English Grammar in Use", course.name)
        assertEquals("Units on *present* tenses.", course.description?.text)
        assertEquals("#2F6B5E", course.theme?.accent)
        assertEquals(12, course.formatVersion, "a file of format 1 is brought up to 12")
        assertEquals(RulesSpec(Preset.SM2, grading = GradingSpec.Threshold(0.75, typos = false)), course.rules, "settings.allowTypos became grading.typos")
        val unit = course.units.single()
        assertEquals(Uuid.parse(UNIT_ID), unit.id)
        assertEquals("I am doing", unit.subtitle?.text)
        assertIs<Content.Blocks>(unit.theory)
        assertEquals(listOf("Things happening now", "Questions"), course.allTopics.map { it.title })
        assertEquals(Uuid.parse(TOPIC_ID), unit.topics[0].id)
        assertEquals(Uuid.parse(EXERCISE_ID), unit.topics[0].exercises[0].id)
        assertIs<Exercise.Choice>(unit.topics[0].exercises[1])
        assertEquals(FORMAT_VERSION, course.formatVersion)
        assertFalse(course.studyOnly)
        assertNull(course.sourceJson)
    }

    @Test fun importResetsRevisionAndTimesButKeepsCreatedAt() {
        val course = read(CourseSamples.units.replace("\"formatVersion\": 1,", "\"formatVersion\": 1, \"revision\": 7, \"updatedAt\": \"2020-01-01T00:00:00Z\", \"createdAt\": \"2021-05-05T00:00:00Z\",")).course
        assertEquals(0, course.revision)
        assertEquals(now, course.updatedAt)
        assertEquals(Instant.parse("2021-05-05T00:00:00Z"), course.createdAt)
        assertEquals(now, read(CourseSamples.flat).course.createdAt)
    }

    @Test fun missingIdsAreCreatedAndAllUnique() {
        val course = read(CourseSamples.flat).course
        val ids = course.ids()
        assertEquals(1 + 2 + 2, ids.size)
        assertEquals(ids.size, ids.toSet().size)
        assertNotEquals(Uuid.NIL, course.id)
    }

    @Test fun badAndRepeatedIdsAreReplaced() {
        val course = read(flatWith("""
            { "id": "$TOPIC_ID", "title": "A", "exercises": [ { "id": "$TOPIC_ID", "type": "gaps", "items": [ { "text": "{{a}}" } ] } ] },
            { "id": "not-a-uuid", "title": "B", "exercises": [ { "id": 5, "type": "gaps", "items": [ { "text": "{{b}}" } ] } ] }
        """)).course
        assertEquals(Uuid.parse(TOPIC_ID), course.topics[0].id)
        val ids = course.ids()
        assertEquals(ids.size, ids.toSet().size)
    }

    // the whole file

    @Test fun notJson() {
        val problem = problems("{ nope").single()
        assertTrue(problem.startsWith("$: not valid JSON: "), problem)
    }

    @Test fun notAnObject() = assertEquals(listOf("$: a course file must be a JSON object"), problems("[]"))

    @Test fun formatVersionIsRequiredAndWholeNumber() {
        assertEquals(listOf("formatVersion: missing; this app writes 12"), problems("""{ "name": "X", "topics": [] }"""))
        for (bad in listOf("\"1\"", "1.5", "0", "null")) {
            assertEquals(listOf("formatVersion: must be a whole number like 12"), problems("""{ "formatVersion": $bad, "name": "X" }"""), bad)
        }
    }

    @Test fun anUnknownFieldIsReportedWithItsFullPath() {
        val text = CourseSamples.units.replace("\"options\": [\"rains\"", "\"optoins\": [\"rains\"")
        assertEquals(listOf("units[0].topics[0].exercises[1].items[0]: Encountered an unknown key 'optoins'"), problems(text))
    }

    @Test fun allContentProblemsAreReportedAtOnce() = assertEquals(
        listOf(
            "units[0].topics[0].exercises[0].items[0].text: needs at least one blank {{…}}",
            "units[0].topics[1].exercises[0]: unknown exercise type \"dictation\"",
        ),
        problems(CourseSamples.units.replace("Somebody {{is singing}}", "Somebody sings").replace("\"type\": \"order\"", "\"type\": \"dictation\"")),
    )

    // course structure

    @Test fun courseFields() = assertEquals(
        listOf(
            "name: must not be empty",
            "description: blanks {{…}} are only allowed in exercises",
            "theme.accent: must be a colour like #2F6B5E",
        ),
        problems(flatWithExercise("""{ "type": "gaps", "items": [ { "text": "{{a}}" } ] }""")
            .replace("\"name\": \"X\"", "\"name\": \" \", \"description\": \"{{x}}\", \"theme\": { \"accent\": \"green\" }")),
    )

    @Test fun nameAtMostFiftyCharacters() = assertEquals(
        listOf("name: is longer than 50 characters"),
        problems(flatWithExercise("""{ "type": "gaps", "items": [ { "text": "{{a}}" } ] }""").replace("\"X\"", "\"${"x".repeat(51)}\"")),
    )

    @Test fun unitsOrTopicsButNotBoth() {
        assertEquals(listOf("$: needs units or topics"), problems("""{ "formatVersion": 1, "name": "X" }"""))
        val both = CourseSamples.units.replace("\"units\": [", "\"topics\": [ { \"title\": \"T\", \"exercises\": [ { \"type\": \"gaps\", \"items\": [ { \"text\": \"{{a}}\" } ] } ] } ], \"units\": [")
        assertEquals(listOf("$: has both units and topics; a course has one or the other"), problems(both))
    }

    @Test fun unitFields() = assertEquals(
        listOf(
            "units[0].title: must not be empty",
            "units[0].subtitle: must not be empty",
            "units[0].theory.blocks: needs at least one block",
            "units[0].topics: needs at least one topic",
        ),
        problems("""{ "formatVersion": 1, "name": "X", "units": [ { "title": "", "subtitle": "", "theory": { "format": "blocks", "blocks": [] }, "topics": [] } ] }"""),
    )

    @Test fun topicFields() = assertEquals(
        listOf(
            "topics[0].title: must not be empty",
            "topics[0].explanation.blocks: needs at least one block",
            "topics[0].exercises: needs at least one exercise",
        ),
        problems(flatWith("""{ "title": " ", "explanation": { "format": "blocks", "blocks": [] }, "exercises": [] }""")),
    )

    @Test fun rulesAreChecked() {
        val text = flatWithExercise("""{ "type": "gaps", "items": [ { "text": "{{a}}" } ] }""").replace("\"name\": \"X\"", """"name": "X",
            "rules": { "grading": { "type": "threshold", "min": 1.5 }, "mastery": { "type": "consecutive", "learning": 0, "review": 0 },
              "scheduler": { "type": "ladder", "days": [1, 0] } }""")
        assertEquals(
            listOf(
                "rules.grading.min: must be more than 0 and at most 1, like 0.75",
                "rules.mastery.learning: must be at least 1",
                "rules.mastery.review: must be at least 1",
                "rules.scheduler.days[1]: must be at least 1 day",
            ),
            problems(text),
        )
        val empty = text.replace("[1, 0]", "[]").replace("1.5", "0.5").replace("\"learning\": 0, \"review\": 0", "\"learning\": 2, \"review\": 1")
        assertEquals(listOf("rules.scheduler.days: needs at least one interval"), problems(empty))
        val unknownPreset = flatWithExercise("""{ "type": "gaps", "items": [ { "text": "{{a}}" } ] }""").replace("\"name\": \"X\"", """"name": "X", "rules": { "preset": "turbo" }""")
        assertTrue(problems(unknownPreset).single().startsWith("rules.preset: "), problems(unknownPreset).single())
    }

    @Test fun aDailyLimitIsAtLeastOne() {
        val course = flatWithExercise("""{ "type": "gaps", "items": [ { "text": "{{a}}" } ] }""")
        fun with(field: String) = course.replace("\"name\": \"X\"", "\"name\": \"X\", $field")
        assertEquals(PickerSpec.Conveyor(newPerDay = 10), read(with("\"rules\": { \"picker\": { \"type\": \"conveyor\", \"newPerDay\": 10 } }")).course.rules?.picker)
        assertEquals(listOf("rules.picker.newPerDay: must be at least 1, or left out for no limit"),
            problems(with("\"rules\": { \"picker\": { \"type\": \"alternate\", \"newPerDay\": 0 } }")))
        assertNull(read(course).course.rules, "no rules, no limit")
    }

    // format 1 → 2

    private fun v1(field: String) = flatWithExercise("""{ "type": "gaps", "items": [ { "text": "{{a}}" } ] }""")
        .replace("\"formatVersion\": $FORMAT_VERSION", "\"formatVersion\": 1").replace("\"name\": \"X\"", "\"name\": \"X\", $field")

    @Test fun formatOneSettingsMoveIntoTheRules() {
        val both = read(v1("\"settings\": { \"allowTypos\": false, \"newPerDay\": 10 }")).course
        assertEquals(FORMAT_VERSION, both.formatVersion)
        assertEquals(RulesSpec(grading = GradingSpec.AllCorrect(typos = false), picker = PickerSpec.Conveyor(newPerDay = 10)), both.rules)
        val kept = read(v1("\"settings\": { \"newPerDay\": 3 }, \"rules\": { \"preset\": \"quick\", \"grading\": { \"type\": \"threshold\", \"min\": 0.5 }, \"picker\": { \"type\": \"random\" } }")).course
        assertEquals(RulesSpec(Preset.QUICK, grading = GradingSpec.Threshold(0.5), picker = PickerSpec.Shuffle(newPerDay = 3)), kept.rules)
        assertEquals(PickerSpec.Conveyor(), read(v1("\"rules\": { \"picker\": { \"type\": \"reviews-first\" } }")).course.rules?.picker)
        assertNull(read(v1("\"settings\": {}")).course.rules?.grading, "an empty settings object changes nothing")
    }

    @Test fun formatsTwoToFiveAreReadAsSix() {
        for (old in 2..5) {
            val file = flatWithExercise("""{ "type": "gaps", "items": [ { "text": "{{a}}" } ] }""").replace("\"formatVersion\": 1", "\"formatVersion\": $old")
            assertEquals(12, read(file).course.formatVersion, "$old")
        }
    }

    @Test fun anExpandModelAnswerMustPassItsKeys() {
        val course = flatWithExercise("""{ "type": "expand", "items": [ { "text": "{{always borrowing|borrowing it}}", "keys": [["always", "borrowing"]] } ] }""")
        assertEquals(listOf("topics[0].exercises[0].items[0].keys: the model answer \"borrowing it\" doesn't pass them: Not found: always"), problems(course))
    }

    @Test fun formatTwoHasNoSettingsAndNoOldPickerNames() {
        val v2 = flatWithExercise("""{ "type": "gaps", "items": [ { "text": "{{a}}" } ] }""").replace("\"formatVersion\": 1", "\"formatVersion\": $FORMAT_VERSION")
        assertTrue(problems(v2.replace("\"name\": \"X\"", "\"name\": \"X\", \"settings\": { \"allowTypos\": false }")).single().contains("settings"))
        assertTrue(problems(v2.replace("\"name\": \"X\"", "\"name\": \"X\", \"rules\": { \"picker\": { \"type\": \"random\" } }")).single().startsWith("rules.picker"))
    }

    @Test fun aStoredCourseOfFormatOneIsUpgradedToo() {
        val stored = CourseJson.write(read(flatWithExercise("""{ "type": "gaps", "items": [ { "text": "{{a}}" } ] }""")).course)
            .replace("\"formatVersion\": $FORMAT_VERSION", "\"formatVersion\": 1")
            .replace("\"name\": \"X\"", "\"name\": \"X\", \"settings\": { \"allowTypos\": false }")
        val course = CourseJson.readStored(stored)
        assertEquals(FORMAT_VERSION, course.formatVersion)
        assertEquals(false, course.rules?.grading?.typos)
        assertFalse(course.studyOnly)
    }

    @Test fun linksMustPointAtSomethingThatExists() {
        val text = CourseSamples.units
            .replace("{ \"section\": \"$SECTION_B\" }", "{ \"section\": \"$TOPIC_ID\" }")
            .replace("{ \"unit\": \"$UNIT_ID\" }", "{ \"unit\": \"$COURSE_ID\" }")
        assertEquals(
            listOf(
                "units[0].theory.blocks[1].blocks[0].links[1].unit: this course has no unit with id \"$COURSE_ID\"",
                "units[0].topics[0].explanation.blocks[0].links[0].section: this course has no theory section with id \"$TOPIC_ID\"",
            ),
            problems(text),
        )
        val flatLink = flatWithExercise("""{ "type": "gaps", "items": [ { "text": "{{a}}" } ],
            "explanation": { "format": "blocks", "blocks": [ { "type": "seeAlso", "links": [ { "section": "$SECTION_A" } ] } ] } }""")
        assertEquals(listOf("topics[0].exercises[0].explanation.blocks[0].links[0].section: this course has no theory section with id \"$SECTION_A\""), problems(flatLink))
    }

    @Test fun sectionIdsAreUniqueWithinTheCourse() {
        val text = CourseSamples.units.replace("\"id\": \"$SECTION_B\"", "\"id\": \"$SECTION_A\"")
        assertEquals(
            listOf(
                "units[0].theory.blocks[1].id: another section of this course has the id \"$SECTION_A\"",
                "units[0].topics[0].explanation.blocks[0].links[0].section: this course has no theory section with id \"$SECTION_B\"",
            ),
            problems(text),
        )
    }

    @Test fun aSectionIdMustBeAUuid() {
        val text = CourseSamples.units.replace("\"id\": \"$SECTION_A\"", "\"id\": \"not-finished\"")
        assertTrue(problems(text).single().startsWith("units[0].theory.blocks[0].id"), problems(text).single())
    }

    // newer format

    private val newer = CourseSamples.units
        .replace("\"formatVersion\": 1", "\"formatVersion\": 13, \"level\": \"B1\"")
        .replace("\"type\": \"order\"", "\"type\": \"dictation\"")
        .replace("{ \"type\": \"text\", \"text\": \"He**’s cycling**.\" }", "{ \"type\": \"hologram\", \"src\": \"v.mp4\" }")

    @Test fun aNewerFileIsReadLenientlyWithWarnings() {
        val (course, warnings) = read(newer)
        assertTrue(course.studyOnly)
        assertEquals(13, course.formatVersion)
        assertEquals(
            listOf(
                "units[0].theory.blocks[0].blocks[0]: unknown block type \"hologram\"",
                "units[0].topics[1].exercises[0]: unknown exercise type \"dictation\"",
            ),
            warnings.map { it.toString() },
        )
        assertTrue(warnings.all { it.kind == Problem.Kind.UNKNOWN_TYPE })
        assertIs<Exercise.Unknown>(course.allTopics[1].exercises[0])
        assertContains(course.sourceJson!!, "dictation")
    }

    @Test fun aNewerFileStillFailsOnRealErrors() =
        assertEquals(listOf("topics[0].title: must not be empty"), problems(flatWith("""{ "title": "", "exercises": [ { "type": "x" } ] }""").replace("\"formatVersion\": 1", "\"formatVersion\": 13")))

    @Test fun aStudyOnlyCourseIsWrittenAsItCameWithNewIdsAndName() {
        val course = read(newer).course
        val renamed = course.withFreshIds().copy(name = "Copy", revision = 3, updatedAt = Instant.parse("2027-01-01T00:00:00Z"))
        val written = CourseJson.write(renamed)
        assertContains(written, "\"level\": \"B1\"")
        assertContains(written, "\"dictation\"")
        val again = CourseJson.readStored(written)
        assertTrue(again.studyOnly)
        assertEquals("Copy", again.name)
        assertEquals(3, again.revision)
        assertEquals(renamed.ids(), again.ids())
        assertEquals(Instant.parse("2027-01-01T00:00:00Z"), again.updatedAt)
    }

    // writing

    @Test fun writeThenReadGivesTheSameCourse() {
        for (sample in listOf(CourseSamples.units, CourseSamples.flat)) {
            val course = read(sample).course
            val written = CourseJson.write(course)
            assertEquals(course, read(written).course)
            assertEquals(course, CourseJson.readStored(written))
            assertEquals(written, CourseJson.write(read(written).course))
        }
    }

    @Test fun writtenFilesStartWithTheFormatVersion() {
        val written = CourseJson.write(read(CourseSamples.flat).course)
        assertTrue(written.lines()[1].trim() == "\"formatVersion\": 12,", written.lines()[1])
    }

    @Test fun storedDocumentsAreReadWithoutChecks() {
        val course = read(CourseSamples.flat).course
        assertEquals(course, CourseJson.readStored(CourseJson.write(course)))
        assertFailsWith<CourseFormatException> { CourseJson.readStored("""{ "formatVersion": 1 }""") }
    }
}

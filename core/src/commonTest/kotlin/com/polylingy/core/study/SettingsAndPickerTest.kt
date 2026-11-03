package com.polylingy.core.study

import com.polylingy.content.Exercise
import com.polylingy.content.ItemResult
import com.polylingy.content.Response
import com.polylingy.core.FixedClock
import com.polylingy.core.InMemoryCourseRepository
import com.polylingy.core.InMemoryCourseSettings
import com.polylingy.core.InMemoryEventLog
import com.polylingy.core.at
import com.polylingy.core.content.Course
import com.polylingy.core.content.CourseJson
import com.polylingy.core.content.CourseService
import com.polylingy.core.content.GradingSpec
import com.polylingy.core.content.MasterySpec
import com.polylingy.core.content.PickerSpec
import com.polylingy.core.content.Preset
import com.polylingy.core.content.RulesSpec
import com.polylingy.core.content.SchedulerSpec
import com.polylingy.core.journal.AnswerSubmitted
import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** Course settings apart from the document, and the three pickers in a whole session (docs/learning-model.md, section 7). */
class SettingsAndPickerTest {
    private val clock = FixedClock(at(LocalDate(2026, 11, 3)))
    private val courses = InMemoryCourseRepository()
    private val settings = InMemoryCourseSettings()
    private val journal = InMemoryEventLog()
    private val courseService = CourseService(courses, settings, clock)
    private val study = StudyService(courses, settings, journal, clock, Uuid.NIL, Random(7))

    private fun gaps(answer: String) = """{ "type": "gaps", "items": [ { "text": "{{$answer}}" } ] }"""

    /** Topics A, B, C… of one exercise each, or [exercises] each; imported, so their settings come from [rules]. */
    private fun import(rules: String, topics: Int = 3, exercises: Int = 1): Course = courseService.import(
        CourseJson.read(
            """{ "formatVersion": 2, "name": "Flat", "rules": $rules,
                "topics": [ ${(0 until topics).joinToString(",") { t ->
                """{ "title": "${'A' + t}", "exercises": [ ${(1..exercises).joinToString(",") { gaps("${'a' + t}$it") }} ] }"""
            }} ] }""",
            clock.now().instant,
        ).course,
    )

    private fun blankOf(exercise: Exercise) = (exercise as Exercise.Gaps).items.single().text.blanks.single().answer

    /**
     * Plays a session: each topic answers by its [script] (true — right) in the order the picker shows them.
     * Returns the topic titles in that order.
     */
    private fun session(course: Course, script: Map<String, List<Boolean>>): List<String> {
        val left = script.mapValues { it.value.toMutableList() }
        val shown = mutableListOf<String>()
        var current: Uuid? = null
        while (true) {
            val next = study.next(course.id, current = current) ?: break
            val right = left.getValue(next.topic.title).removeFirst()
            study.answer(course.id, next.topic.id, next.exercise.id, Response.Blanks(listOf(if (right) blankOf(next.exercise) else "nope")))
            shown += next.topic.title
            current = next.topic.id
            check(shown.size < 50) { "the session doesn't end: $shown" }
        }
        return shown
    }

    private val twoInARow = """{ "mastery": { "type": "consecutive", "learning": 2, "review": 1 }"""

    @Test fun theConveyorStaysOnATopicUntilItIsDone() {
        val course = import("$twoInARow, \"picker\": { \"type\": \"conveyor\", \"newPerDay\": 2 } }")
        val order = session(course, mapOf("A" to listOf(true, false, true, true), "B" to listOf(true, true), "C" to emptyList()))
        assertEquals(listOf("A", "A", "A", "A", "B", "B"), order, "C waits: two new topics a day")
    }

    @Test fun alternatingMovesOnAfterEveryAnswer() {
        val course = import("$twoInARow, \"picker\": { \"type\": \"alternate\", \"newPerDay\": 2 } }")
        val order = session(course, mapOf("A" to listOf(true, false, true, true), "B" to listOf(true, true), "C" to emptyList()))
        assertEquals(listOf("A", "B", "A", "B", "A", "A"), order, "the same answers, another order; the last topic left goes on by itself")
    }

    @Test fun shuffleStillFinishesARoundBeforeTheNextTopic() {
        val course = import("$twoInARow, \"picker\": { \"type\": \"shuffle\" } }", topics = 4)
        val order = session(course, ('A'..'D').associate { "$it" to listOf(true, true) })
        assertEquals(8, order.size)
        order.chunked(2).forEach { assertEquals(1, it.toSet().size, "a topic twice in a row: $order") }
        assertEquals(setOf("A", "B", "C", "D"), order.toSet())
    }

    @Test fun theLastExerciseOfATopicIsNotShownAgainEvenAfterAnotherTopic() {
        val course = import("$twoInARow, \"picker\": { \"type\": \"alternate\" } }", topics = 2, exercises = 2)
        val shown = mutableListOf<Pair<String, Uuid>>()
        var current: Uuid? = null
        repeat(4) {
            val next = study.next(course.id, current = current)!!
            study.answer(course.id, next.topic.id, next.exercise.id, Response.Blanks(listOf("nope")))
            shown += next.topic.title to next.exercise.id
            current = next.topic.id
        }
        assertEquals(listOf("A", "B", "A", "B"), shown.map { it.first })
        assertNotEquals(shown[0].second, shown[2].second, "A's exercises take turns although B came between")
        assertNotEquals(shown[1].second, shown[3].second)
    }

    @Test fun importStartsTheSettingsFromTheFileAndExportWritesThemBack() {
        val course = import("""{ "preset": "quick", "picker": { "type": "alternate", "newPerDay": 4 } }""")
        val stored = settings.get(course.id)!!
        assertEquals(RulesSpec(Preset.QUICK, GradingSpec.AllCorrect(), MasterySpec.Consecutive(1, 1), SchedulerSpec.Once, PickerSpec.Alternate(4)), stored)
        assertEquals(stored, study.settings(course.id))

        val changed = stored.copy(picker = PickerSpec.Conveyor(newPerDay = 1))
        study.saveSettings(course.id, changed)
        assertEquals(changed, courseService.forExport(courses.get(course.id)!!).rules)
        assertEquals(course.revision, courses.get(course.id)!!.revision, "settings don't touch the document")

        courseService.delete(course.id)
        assertNull(settings.get(course.id))
    }

    @Test fun aCourseStoredBeforeSettingsUsesTheRulesOfItsDocument() {
        val course = CourseJson.read(
            """{ "formatVersion": 2, "name": "Old", "rules": { "preset": "sm2" }, "topics": [ { "title": "A", "exercises": [ ${gaps("a")} ] } ] }""",
            clock.now().instant,
        ).course.also(courses::save)
        assertEquals(RulesSpec(Preset.SM2).resolved(), study.settings(course.id))
        assertNull(study.settings(Uuid.random()))
    }

    @Test fun savedSettingsRecountProgressFromTheWholeJournal() {
        val course = import("{}", topics = 1)
        val next = study.next(course.id)!!
        study.answer(course.id, next.topic.id, next.exercise.id, Response.Blanks(listOf(blankOf(next.exercise))))
        assertEquals(0, study.progress(course.id)!!.counts.learned, "the ladder wants 3 in a row")

        val quick = RulesSpec(Preset.QUICK).resolved()
        assertEquals(1, study.progressWith(course.id, quick)!!.counts.learnedForGood, "the preview counts with the new rules")
        assertEquals(0, study.progress(course.id)!!.counts.learned, "nothing saved yet")
        study.saveSettings(course.id, quick)
        assertEquals(1, study.progress(course.id)!!.counts.learnedForGood)
        assertEquals(1, journal.all().size, "the journal is never touched")
    }

    @Test fun theDailyLimitComesFromTheSettings() {
        val course = import("""{ "picker": { "type": "conveyor", "newPerDay": 2 } }""")
        assertEquals(2, study.progress(course.id)!!.newPerDay)
        assertEquals(2, study.progress(course.id)!!.counts.newToday)
        study.saveSettings(course.id, study.settings(course.id)!!.copy(picker = PickerSpec.Conveyor()))
        assertNull(study.progress(course.id)!!.newPerDay)
        assertNull(study.progress(course.id)!!.counts.newToday)
    }

    @Test fun typosAreKeptInTheJournalAndShownByTheSettings() {
        val course = import("""{ "grading": { "type": "all-correct", "typos": false } }""", topics = 1)
        val exercise = course.topics.single().exercises.single()
        val word = Response.Blanks(listOf("wroking"))
        val working = Exercise.Gaps(exercise.id, items = listOf(Exercise.GapItem(com.polylingy.content.Inline("{{working}}"))))
        assertEquals(ItemResult.WRONG, study.preview(course.id, working, word).single().result, "not forgiven: shown as wrong")
        study.saveSettings(course.id, study.settings(course.id)!!.copy(grading = GradingSpec.AllCorrect(typos = true)))
        assertEquals(ItemResult.TYPO, study.preview(course.id, working, word).single().result)
        assertEquals(0, journal.all().size, "a preview writes nothing")
    }

    @Test fun anAnswerWithATypoIsStoredAsATypoWhateverTheSettings() {
        val course = courseService.import(
            CourseJson.read(
                """{ "formatVersion": 2, "name": "T", "rules": { "grading": { "type": "all-correct", "typos": false } },
                    "topics": [ { "title": "A", "exercises": [ ${gaps("working")} ] } ] }""",
                clock.now().instant,
            ).course,
        )
        val next = study.next(course.id)!!
        val outcome = study.answer(course.id, next.topic.id, next.exercise.id, Response.Blanks(listOf("wroking")))
        assertEquals(ItemResult.WRONG, outcome.checks.single().result)
        assertEquals(listOf(ItemResult.TYPO), (journal.all().single().event as AnswerSubmitted).results)
        assertEquals(1, study.progress(course.id)!!.counts.learning, "not forgiven: a failed answer, the round goes on")

        study.saveSettings(course.id, study.settings(course.id)!!.copy(grading = GradingSpec.AllCorrect(typos = true), mastery = MasterySpec.Consecutive(1, 1)))
        assertEquals(1, study.progress(course.id)!!.counts.learned, "forgiven now: the same answer counts")
    }

    @Test fun settingsWithProblemsOrOfANewerCourseAreRefused() {
        val course = import("{}")
        assertFailsWith<IllegalArgumentException> { study.saveSettings(course.id, RulesSpec(mastery = MasterySpec.Consecutive(0, 1))) }
        assertFailsWith<IllegalArgumentException> { study.saveSettings(course.id, RulesSpec(picker = PickerSpec.Conveyor(newPerDay = 0))) }
        assertFailsWith<IllegalArgumentException> { study.saveSettings(Uuid.random(), RulesSpec()) }
        val newer = CourseJson.read(
            """{ "formatVersion": 3, "name": "N", "topics": [ { "title": "A", "exercises": [ ${gaps("a")} ] } ] }""",
            clock.now().instant,
        ).course.also(courses::save)
        assertFailsWith<IllegalArgumentException> { study.saveSettings(newer.id, RulesSpec()) }
    }
}

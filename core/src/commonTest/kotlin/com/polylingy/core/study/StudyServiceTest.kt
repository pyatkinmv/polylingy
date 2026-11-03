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
import com.polylingy.core.journal.AnswerSubmitted
import com.polylingy.core.learning.TopicStatus
import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class StudyServiceTest {
    private val monday = LocalDate(2026, 11, 2)
    private val clock = FixedClock(at(monday))
    private val journal = InMemoryEventLog()
    private val settings = InMemoryCourseSettings()

    private fun gaps(answer: String) = """{ "type": "gaps", "items": [ { "text": "{{$answer}}" } ] }"""

    /** Two units: the first with theory and two topics (the second with two exercises), the second with one topic. */
    private fun twoUnits(rules: String = "") = CourseJson.read(
        """{ "formatVersion": 1, "name": "Course" $rules,
            "units": [
              { "title": "U1", "theory": { "format": "blocks", "blocks": [ { "type": "text", "text": "Theory" } ] },
                "topics": [ { "title": "1.1", "exercises": [ ${gaps("a")} ] },
                            { "title": "1.2", "exercises": [ ${gaps("b")}, ${gaps("c")}, { "type": "dictation" } ] } ] },
              { "title": "U2", "topics": [ { "title": "2.1", "exercises": [ ${gaps("d")} ] } ] } ] }""".replace("formatVersion\": 1", "formatVersion\": 3"),
        clock.now().instant,
    ).course

    private fun service(course: Course, random: Random = Random(1)) =
        StudyService(InMemoryCourseRepository(course), settings, journal, clock, Uuid.NIL, random) to course

    private fun StudyService.answerRight(course: Course, next: NextExercise) = answer(course.id, next.topic.id, next.exercise.id, Response.Blanks(listOf(blankOf(next.exercise))))
    private fun StudyService.answerWrong(course: Course, next: NextExercise) = answer(course.id, next.topic.id, next.exercise.id, Response.Blanks(listOf("nope")))
    private fun blankOf(exercise: Exercise) = (exercise as Exercise.Gaps).items.single().text.blanks.single().answer

    @Test fun aNewCourseOffersTheFirstTopicOfTheFirstUnit() {
        val (study, course) = service(twoUnits())
        val next = study.next(course.id)!!
        assertEquals("1.1", next.topic.title)
        assertEquals("U1", next.unit?.title)
        assertTrue(study.theoryFirst(course.id, course.units[0].id))
        assertFalse(study.theoryFirst(course.id, course.units[1].id), "no theory to show")
    }

    @Test fun theConveyorStaysOnATopicUntilItsRoundIsDone() {
        val (study, course) = service(twoUnits())
        val first = study.next(course.id)!!
        study.answerRight(course, first)
        assertEquals(TopicStatus.InProgress, study.progress(course.id)!!.units[0].topics[0].status)
        assertFalse(study.theoryFirst(course.id, course.units[0].id))
        assertEquals("1.1", study.next(course.id, current = first.topic.id)!!.topic.title)
        repeat(2) { study.answerRight(course, study.next(course.id)!!) }
        assertEquals(TopicStatus.Passed(LocalDate(2026, 11, 3)), study.progress(course.id)!!.units[0].topics[0].status)
        assertEquals("1.2", study.next(course.id)!!.topic.title)
    }

    /** A flat course of [n] one-blank topics; [extra] goes into the course object, e.g. settings. */
    private fun flat(n: Int, extra: String = "") = CourseJson.read(
        """{ "formatVersion": 1, "name": "Flat" $extra,
            "topics": [ ${(1..n).joinToString(",") { """{ "title": "T$it", "exercises": [ ${gaps("a$it")} ] }""" }} ] }""",
        clock.now().instant,
    ).course

    private fun StudyService.answerTopic(course: Course, topic: Int, right: Boolean) {
        val t = course.topics[topic]
        val exercise = t.exercises.single()
        answer(course.id, t.id, exercise.id, Response.Blanks(listOf(if (right) blankOf(exercise) else "nope")))
    }

    @Test fun countsSplitTopicsIntoGroupsAndKnowTheNextReview() {
        val (study, course) = service(twoUnits())
        assertEquals(ProgressCounts(0, 0, 0, 0, 3, null, null), study.progress(course.id)!!.counts)
        repeat(3) { study.answerRight(course, study.next(course.id)!!) }
        study.answerWrong(course, study.next(course.id)!!)
        val progress = study.progress(course.id)!!
        assertEquals(ProgressCounts(0, 1, 0, 1, 1, null, LocalDate(2026, 11, 3)), progress.counts, "1.1 learned, 1.2 learning, 2.1 new")
        assertEquals(1, progress.counts.learned)
        assertEquals(3, progress.counts.total)
        assertEquals(1, progress.counts.newNow, "no limit: every new topic is on offer")
        assertEquals(ProgressCounts(0, 0, 0, 0, 1, null, null), progress.units[1].counts)

        clock.now = at(LocalDate(2026, 11, 3))
        val tuesday = study.progress(course.id)!!
        assertEquals(1, tuesday.counts.toReview, "passed yesterday, due today")
        assertEquals(1, tuesday.counts.learned, "a topic to review still counts as learned")
    }

    @Test fun aMistakeInALearnedTopicKeepsItLearnedAndToReview() {
        val (study, course) = service(flat(3))
        for (topic in 0..2) repeat(3) { study.answerTopic(course, topic, right = true) }
        assertEquals(ProgressCounts(0, 3, 0, 0, 0, null, LocalDate(2026, 11, 3)), study.progress(course.id)!!.counts, "Monday: all learned")

        clock.now = at(LocalDate(2026, 11, 3))
        assertEquals(3, study.progress(course.id)!!.counts.toReview, "Tuesday: all due")
        for (topic in 0..2) study.answerTopic(course, topic, right = false)
        val wrong = study.progress(course.id)!!
        assertEquals(ProgressCounts(0, 0, 3, 0, 0, null, null), wrong.counts, "a review round under way is still to review")
        assertEquals(listOf(TopicGroup.TO_REVIEW), wrong.topics.map { it.group }.distinct())
        assertEquals(3, wrong.counts.learned, "a mistake doesn't unlearn")

        for (topic in 0..2) study.answerTopic(course, topic, right = true)
        assertEquals(ProgressCounts(0, 3, 0, 0, 0, null, LocalDate(2026, 11, 4)), study.progress(course.id)!!.counts, "rounds done; back tomorrow")
    }

    @Test fun learnedForGoodIsItsOwnGroup() {
        val (study, course) = service(flat(2, """, "rules": { "preset": "quick" }"""))
        study.answerTopic(course, 0, right = true)
        val counts = study.progress(course.id)!!.counts
        assertEquals(1 to 1, counts.learnedForGood to counts.new)
        assertEquals(1, counts.learned)
    }

    @Test fun theDailyLimitCountsNewTopicsBegunToday() {
        val (study, course) = service(flat(5, """, "settings": { "newPerDay": 2 }"""))
        assertEquals(2, study.progress(course.id)!!.counts.newToday)
        study.answerTopic(course, 0, right = true)
        assertEquals(1, study.progress(course.id)!!.counts.newToday, "one begun, still learning")
        study.answerTopic(course, 1, right = false)
        val limitReached = study.progress(course.id)!!.counts
        assertEquals(0, limitReached.newToday)
        assertEquals(2, limitReached.learning)
        assertFalse(limitReached.done, "two first rounds to finish")

        val offered = (1..10).mapNotNull { study.next(course.id) }.map { it.topic.title }.toSet()
        assertTrue(offered.all { it in setOf("T1", "T2") }, "only the topics begun today, no third new one: $offered")

        clock.now = at(LocalDate(2026, 11, 3))
        assertEquals(2, study.progress(course.id)!!.counts.newToday, "a new day, a new limit")
    }

    @Test fun withTheLimitTakenAndNothingToReviewTheDayIsDone() {
        val (study, course) = service(flat(3, """, "settings": { "newPerDay": 1 }"""))
        repeat(3) { study.answerTopic(course, 0, right = true) }
        val counts = study.progress(course.id)!!.counts
        assertEquals(2, counts.new)
        assertEquals(0, counts.newToday)
        assertTrue(counts.done)
        assertNull(study.next(course.id), "new topics wait for tomorrow")
    }

    @Test fun outcomeTellsTheStatusBeforeAndAfter() {
        val (study, course) = service(twoUnits(""", "rules": { "preset": "quick" }"""))
        val next = study.next(course.id)!!
        val wrong = study.answerWrong(course, next)
        assertEquals(listOf(ItemResult.WRONG), wrong.checks.map { it.result })
        assertEquals(TopicStatus.New to TopicStatus.InProgress, wrong.before to wrong.after)
        val right = study.answerRight(course, next)
        assertEquals(TopicStatus.InProgress to TopicStatus.Learned, right.before to right.after)
        val event = journal.all().last().event as AnswerSubmitted
        assertEquals(listOf(ItemResult.CORRECT), event.results)
        assertEquals(course.revision, event.courseRevision)
    }

    @Test fun theWholeCourseFromNewToLearnedWithTheQuickPreset() {
        val (study, course) = service(twoUnits(""", "rules": { "preset": "quick" }"""))
        var answered = 0
        while (true) {
            val next = study.next(course.id) ?: break
            study.answerRight(course, next)
            answered++
        }
        assertEquals(3, answered)
        val progress = study.progress(course.id)!!
        assertTrue(progress.units.all { it.learned })
        assertTrue(progress.allTopics.all { it.status == TopicStatus.Learned })
    }

    @Test fun reviewsComeFirstWhenTheyAreDue() {
        val (study, course) = service(twoUnits(""", "rules": { "mastery": { "type": "consecutive", "learning": 1, "review": 1 } }"""))
        study.answerRight(course, study.next(course.id)!!)
        assertEquals("1.2", study.next(course.id)!!.topic.title)
        clock.set(monday.plusDays(1))
        assertEquals("1.1", study.next(course.id)!!.topic.title, "1.1 is due again")
        assertEquals(TopicStatus.Due, study.progress(course.id)!!.units[0].topics[0].status)
    }

    @Test fun nothingLeftForTodayIsNull() {
        val (study, course) = service(twoUnits(""", "rules": { "mastery": { "type": "consecutive", "learning": 1, "review": 1 } }"""))
        repeat(3) { study.answerRight(course, study.next(course.id)!!) }
        assertNull(study.next(course.id))
        assertNull(study.next(course.id, unitId = course.units[0].id))
    }

    @Test fun aUnitOffersOnlyItsOwnTopics() {
        val (study, course) = service(twoUnits())
        assertEquals("2.1", study.next(course.id, unitId = course.units[1].id)!!.topic.title)
        assertNull(study.next(course.id, unitId = Uuid.random()))
    }

    @Test fun practisingATopicEarlyStartsARound() {
        val (study, course) = service(twoUnits(""", "rules": { "mastery": { "type": "consecutive", "learning": 1, "review": 1 } }"""))
        val first = study.next(course.id)!!
        study.answerRight(course, first)
        val early = study.practise(course.id, first.topic.id)!!
        assertEquals(first.topic.id, early.topic.id)
        val outcome = study.answerWrong(course, early)
        assertEquals(TopicStatus.InProgress, outcome.after)
        assertEquals("1.1", study.next(course.id)!!.topic.title, "the round under way comes first")
        assertNull(study.practise(course.id, Uuid.random()))
        assertNull(study.practise(Uuid.random(), first.topic.id))
    }

    @Test fun anExerciseIsNotRepeatedRightAwayWhenThereAreOthers() {
        val (study, course) = service(twoUnits())
        val topic = course.units[0].topics[1]
        val shown = (1..20).map { study.practise(course.id, topic.id, lastExercise = topic.exercises[0].id)!!.exercise.id }.toSet()
        assertEquals(setOf(topic.exercises[1].id), shown)
        val single = course.units[0].topics[0]
        assertEquals(single.exercises[0].id, study.practise(course.id, single.id, lastExercise = single.exercises[0].id)!!.exercise.id)
    }

    @Test fun exercisesOfANewerFormatAreNeverShown() {
        val (study, course) = service(twoUnits())
        val topic = course.units[0].topics[1]
        repeat(30) { assertIs<Exercise.Gaps>(study.practise(course.id, topic.id)!!.exercise) }
    }

    @Test fun typosCountOnlyWhenTheCourseForgivesThem() {
        for ((allow, expected) in listOf(true to ItemResult.TYPO, false to ItemResult.WRONG)) {
            val course = CourseJson.read(
                """{ "formatVersion": 1, "name": "T", "settings": { "allowTypos": $allow },
                    "topics": [ { "title": "A", "exercises": [ ${gaps("working")} ] } ] }""",
                clock.now().instant,
            ).course
            val (study, _) = service(course)
            val next = study.next(course.id)!!
            assertEquals(expected, study.answer(course.id, next.topic.id, next.exercise.id, Response.Blanks(listOf("wroking"))).checks.single().result)
        }
    }

    @Test fun unknownCourseTopicOrExercise() {
        val (study, course) = service(twoUnits())
        assertNull(study.next(Uuid.random()))
        assertNull(study.progress(Uuid.random()))
        assertFalse(study.theoryFirst(Uuid.random(), course.units[0].id))
        assertFalse(study.theoryFirst(course.id, Uuid.random()))
        val topic = course.units[0].topics[0]
        assertFailsWith<IllegalArgumentException> { study.answer(Uuid.random(), topic.id, topic.exercises[0].id, Response.Blanks(listOf(""))) }
        assertFailsWith<IllegalArgumentException> { study.answer(course.id, Uuid.random(), topic.exercises[0].id, Response.Blanks(listOf(""))) }
        assertFailsWith<IllegalArgumentException> { study.answer(course.id, topic.id, Uuid.random(), Response.Blanks(listOf(""))) }
    }

    @Test fun aFlatCourseWithTheRandomPicker() {
        val course = CourseJson.read(
            """{ "formatVersion": 1, "name": "Flat", "rules": { "picker": { "type": "random" } },
                "topics": [ { "title": "A", "exercises": [ ${gaps("a")} ] }, { "title": "B", "exercises": [ ${gaps("b")} ] },
                            { "title": "C", "exercises": [ ${gaps("c")} ] } ] }""",
            clock.now().instant,
        ).course
        val picks = (1..30).map { StudyService(InMemoryCourseRepository(course), InMemoryCourseSettings(), InMemoryEventLog(), clock, Uuid.NIL, Random(it)).next(course.id)!!.topic.title }.toSet()
        assertEquals(setOf("A", "B", "C"), picks)
        val (study, _) = service(course)
        assertNotEquals(null, study.progress(course.id)!!.topics.firstOrNull())
        assertNull(study.next(course.id)!!.unit)
    }

    private fun LocalDate.plusDays(n: Int) = LocalDate.fromEpochDays(toEpochDays() + n)
}

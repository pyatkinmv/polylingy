package com.polylingy.core.study

import com.polylingy.content.Exercise
import com.polylingy.content.Response
import com.polylingy.core.FixedClock
import com.polylingy.core.InMemoryCourseRepository
import com.polylingy.core.InMemoryCourseSettings
import com.polylingy.core.InMemoryEventLog
import com.polylingy.core.at
import com.polylingy.core.content.Course
import com.polylingy.core.content.CourseJson
import com.polylingy.core.content.ExerciseSpec
import com.polylingy.core.content.MasterySpec
import com.polylingy.core.content.PickerSpec
import com.polylingy.core.content.RulesSpec
import com.polylingy.core.journal.PracticeAnswered
import com.polylingy.core.journal.AnswerSubmitted
import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Extra practice stands apart from the plan: its answers are a fact of their own kind that progress and schedules never read. */
class PracticeTest {
    private val clock = FixedClock(at(LocalDate(2026, 11, 3)))
    private val journal = InMemoryEventLog()
    private val settings = InMemoryCourseSettings()

    private fun gaps(answer: String) = """{ "type": "gaps", "items": [ { "text": "{{$answer}}" } ] }"""

    /** Unit 1: topic A (3 exercises), topic B (2); unit 2: topic C (2, never answered); topic D (1, never answered). */
    private val course: Course = CourseJson.read(
        """{ "formatVersion": 1, "name": "Course",
            "units": [
              { "title": "U1", "topics": [
                  { "title": "A", "exercises": [ ${gaps("a1")}, ${gaps("a2")}, ${gaps("a3")} ] },
                  { "title": "B", "exercises": [ ${gaps("b1")}, ${gaps("b2")} ] } ] },
              { "title": "U2", "topics": [
                  { "title": "C", "exercises": [ ${gaps("c1")}, ${gaps("c2")} ] },
                  { "title": "D", "exercises": [ ${gaps("d1")} ] } ] } ] }""",
        clock.now().instant,
    ).course
    private val study = StudyService(InMemoryCourseRepository(course), settings, journal, clock, Uuid.NIL, Random(1))

    private val topicA = course.allTopics[0]
    private val topicB = course.allTopics[1]
    private val topicC = course.allTopics[2]

    private fun blank(exercise: Exercise) = (exercise as Exercise.Gaps).items.single().text.blanks.single().answer

    /** The plan has started A and B: one right answer each (mastery 3 in a row keeps them under way). */
    private fun startTopics() {
        for (t in listOf(topicA, topicB)) {
            val e = t.exercises.first()
            study.answer(course.id, t.id, e.id, Response.Blanks(listOf(blank(e))))
        }
    }

    private fun practise(scope: PracticeScope, count: Int, right: Boolean = true): List<NextExercise> {
        var state = PracticeState()
        val shown = mutableListOf<NextExercise>()
        repeat(count) {
            val next = study.practiceNext(course.id, scope, state) ?: return shown
            shown += next
            val answer = if (right) blank(next.exercise) else "nope"
            val result = study.practiceAnswer(course.id, next.topic.id, next.exercise.id, Response.Blanks(listOf(answer)))
            state = PracticeState(state.answers + PracticeAnswer(next.topic.id, next.exercise.id, result.passed), next.topic.id)
        }
        return shown
    }

    // ---- the plan is untouched

    @Test fun practiceNeverChangesTheProgressTheStatusesOrWhatComesNext() {
        startTopics()
        clock.now = at(LocalDate(2026, 11, 4))
        val before = study.progress(course.id)
        val nextBefore = study.next(course.id)
        practise(PracticeScope.Course, 12, right = false)
        practise(PracticeScope.Course, 12, right = true)
        assertEquals(before, study.progress(course.id), "progress derived from the journal is the same with the practice answers in it")
        assertEquals(nextBefore?.topic, study.next(course.id)?.topic)
        assertTrue(journal.all().any { it.event is PracticeAnswered }, "the practice is in the journal")
    }

    @Test fun practiceAnswersAreNotCountedAsBeginningATopicOrAnswersOfThePlan() {
        startTopics()
        val topicsStarted = study.progress(course.id)!!.counts
        practise(PracticeScope.Topic(topicC.id), 4)
        assertEquals(topicsStarted, study.progress(course.id)!!.counts, "C was practised but is still new for the plan")
        assertFalse(study.canPractise(course.id, PracticeScope.Unit(course.units[1].id)), "nothing of unit 2 was started by the plan")
        assertTrue(journal.all().none { it.event is AnswerSubmitted && (it.event as AnswerSubmitted).topicId == topicC.id })
    }

    @Test fun theExerciseChoiceOfThePlanDoesNotSeeThePractice() {
        settings.save(course.id, RulesSpec(mastery = MasterySpec.Consecutive(2, 1), picker = PickerSpec.Conveyor(exercise = ExerciseSpec.MISSED_FIRST)), clock.now().instant)
        study.answer(course.id, topicA.id, topicA.exercises[1].id, Response.Blanks(listOf("nope")))   // the plan missed A2
        val planNext = study.next(course.id)!!.exercise.id
        practise(PracticeScope.Topic(topicA.id), 5, right = false)                                     // practice misses everything
        assertEquals(planNext, study.next(course.id)!!.exercise.id, "the plan's choice is what it was; practice added nothing to its history")
    }

    // ---- what can be practised

    @Test fun onlyWhatThePlanHasStartedCanBePractisedInACourseOrUnit() {
        assertFalse(study.canPractise(course.id, PracticeScope.Course), "nothing started yet")
        startTopics()
        assertEquals(listOf("A", "B"), study.practiceTopics(course.id, PracticeScope.Course).map { it.title })
        assertEquals(listOf("A", "B"), study.practiceTopics(course.id, PracticeScope.Unit(course.units[0].id)).map { it.title })
        assertEquals(emptyList(), study.practiceTopics(course.id, PracticeScope.Unit(course.units[1].id)))
        assertEquals(listOf("C"), study.practiceTopics(course.id, PracticeScope.Topic(topicC.id)).map { it.title }, "a topic asked for by hand is practised")
    }

    @Test fun nothingToPractiseIsNull() {
        assertNull(study.practiceNext(course.id, PracticeScope.Course, PracticeState()))
    }

    // ---- passes

    @Test fun eachExerciseOfATopicComesOnceInAPassAndThenAnotherPassStarts() {
        val shown = practise(PracticeScope.Topic(topicA.id), 7)
        val ids = shown.map { it.exercise.id }
        assertEquals(topicA.exercises.map { it.id }.toSet(), ids.take(3).toSet(), "the first pass has all three")
        assertEquals(topicA.exercises.map { it.id }.toSet(), ids.drop(3).take(3).toSet(), "so has the second")
        assertEquals(7, ids.size)
    }

    @Test fun aCourseSessionGoesThroughTheTopicsPassByPassAndNeverEnds() {
        startTopics()
        // the default picker (conveyor: in the order of the course, stay on a topic): A A A then B B, then A again
        val titles = practise(PracticeScope.Course, 8).map { it.topic.title }
        assertEquals(listOf("A", "A", "A", "B", "B", "A", "A", "A"), titles)
    }

    @Test fun theFocusOfTheCourseDecidesWhetherToStayOnATopicOrMoveOn() {
        startTopics()
        settings.save(course.id, RulesSpec(picker = PickerSpec.Alternate()), clock.now().instant)
        val titles = practise(PracticeScope.Course, 6).map { it.topic.title }
        assertEquals(listOf("A", "B", "A", "B", "A", "B"), titles, "another topic after every answer; B has two exercises, A three, so A's last one is alone in the queue for a moment")
    }

    @Test fun theCoursesExerciseChoiceOrdersTheExercisesWithinAPass() {
        settings.save(course.id, RulesSpec(picker = PickerSpec.Conveyor(exercise = ExerciseSpec.IN_ORDER)), clock.now().instant)
        val ids = practise(PracticeScope.Topic(topicA.id), 6).map { it.exercise.id }
        assertEquals(topicA.exercises.map { it.id } + topicA.exercises.map { it.id }, ids, "in the order of the course, round and round")
    }

    // ---- statistics

    @Test fun practiceIsActivityAndStreakButNotProgressInTheStatistics() {
        val stats = StatisticsService(InMemoryCourseRepository(course), settings, journal, clock, study)
        startTopics()
        clock.now = at(LocalDate(2026, 11, 4))
        val learnedBefore = stats.statistics().learned
        practise(PracticeScope.Course, 5)
        val s = stats.statistics()
        val today = s.days.getValue(LocalDate(2026, 11, 4))
        assertEquals(5, today.answers, "the practice answers of the day")
        assertEquals(0, today.newTopics, "practice begins no topic")
        assertEquals(2, s.streak, "yesterday the plan, today only practice: two days in a row")
        assertEquals(learnedBefore, s.learned, "learned is the plan's: practice doesn't change it")
        assertEquals(0, s.work.newBegun)
    }
}

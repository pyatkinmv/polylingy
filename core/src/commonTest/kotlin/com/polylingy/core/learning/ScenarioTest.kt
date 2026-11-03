package com.polylingy.core.learning

import com.polylingy.content.ItemResult
import com.polylingy.content.ItemResult.CORRECT
import com.polylingy.content.ItemResult.TYPO
import com.polylingy.content.ItemResult.WRONG
import com.polylingy.core.answer
import com.polylingy.core.at
import com.polylingy.core.content.PickerSpec
import com.polylingy.core.course
import com.polylingy.core.journal.EventEnvelope
import com.polylingy.core.topic
import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The worked example of docs/learning-model.md, section 6: one topic, exercises of 1, 2 and 4 blanks,
 * six answers on Monday, and the same history under four combinations of simple policies.
 */
class ScenarioTest {
    private val conveyor = PickerSpec.Conveyor().toPicker(Random(0))
    private val monday = LocalDate(2026, 11, 2)
    private val topic = topic("Present continuous")
    private val course = course(topic)

    /** Monday's answers as the checker reports them: a typo is always a typo; whether it counts is the grading's call. */
    private fun monday() = listOf(
        listOf(CORRECT),                                  // 1 blank, 's working — the second variant
        listOf(CORRECT, CORRECT, CORRECT, WRONG),         // 4 blanks, 3 right
        listOf(CORRECT, TYPO),                            // 2 blanks, one "is wroking"
        listOf(CORRECT),
        listOf(CORRECT, CORRECT, CORRECT, CORRECT),
        listOf(CORRECT, CORRECT),
    )

    private fun events(answers: List<List<ItemResult>>, day: LocalDate = monday, from: Int = 8) =
        answers.mapIndexed { i, results -> answer(course, topic, results, at(day, from + i)) }

    /** After how many of the answers the topic is first mastered, and its state after all of them. */
    private fun <M> run(rules: CourseRules<M, PassFail>, answers: List<List<ItemResult>>): Pair<Int?, TopicState<M, PassFail>> {
        val events = events(answers)
        val masteredAfter = (1..events.size).firstOrNull { n -> replay(course, events.take(n), rules).getValue(topic.id).memory != null }
        return masteredAfter to replay(course, events, rules).getValue(topic.id)
    }

    private fun rules(grading: GradingRule<PassFail>, inARow: Int) = CourseRules(grading, Consecutive(inARow, 1), Ladder(), conveyor)

    @Test fun typosYesAllCorrectOneInARow() {
        val (after, _) = run(rules(AllCorrect(typos = true), 1), monday())
        assertEquals(1, after)
        val first = replay(course, events(monday()).take(1), rules(AllCorrect(), 1)).getValue(topic.id)
        assertEquals(TopicStatus.Passed(LocalDate(2026, 11, 3)), first.status(Ladder(), monday), "step 1: tomorrow")
    }

    @Test fun typosNoAllCorrectThreeInARow() {
        val (after, state) = run(rules(AllCorrect(typos = false), 3), monday())
        assertEquals(6, after, "P F F P P P")
        assertEquals(Ladder.Memory(0, LocalDate(2026, 11, 3), 1), state.memory)
    }

    @Test fun typosYesAllCorrectThreeInARow() {
        val (after, _) = run(rules(AllCorrect(typos = true), 3), monday())
        assertEquals(5, after, "P F P P P")
    }

    @Test fun typosNoThreshold75ThreeInARow() {
        val (after, _) = run(rules(Threshold(0.75, typos = false), 3), monday())
        assertEquals(6, after, "P P F P P P: 3 of 4 is 75 % and passes, 1 of 2 doesn't")
    }

    @Test fun nextDayACleanReviewClimbsAMistakeFallsBack() {
        val rules = rules(AllCorrect(typos = false), 3)
        val history: List<EventEnvelope> = events(monday())
        val tuesday = LocalDate(2026, 11, 3)
        assertEquals(TopicStatus.Due, replay(course, history, rules).getValue(topic.id).status(rules.scheduler, tuesday))

        val clean = replay(course, history + events(listOf(listOf(CORRECT)), tuesday), rules).getValue(topic.id)
        assertEquals(TopicStatus.Passed(LocalDate(2026, 11, 6)), clean.status(rules.scheduler, tuesday), "step 2: three days")

        val mistake = replay(course, history + events(listOf(listOf(WRONG), listOf(CORRECT)), tuesday), rules).getValue(topic.id)
        assertEquals(TopicStatus.Passed(LocalDate(2026, 11, 4)), mistake.status(rules.scheduler, tuesday), "back to step 1: tomorrow")

        val midway = replay(course, history + events(listOf(listOf(WRONG)), tuesday), rules).getValue(topic.id)
        assertEquals(TopicStatus.InProgress, midway.status(rules.scheduler, tuesday))
    }

    @Test fun quickPresetLearnsAfterOneRightAnswer() {
        val rules = CourseRules(AllCorrect(), Consecutive(1, 1), Once, conveyor)
        val state = replay(course, events(listOf(listOf(WRONG), listOf(CORRECT))), rules).getValue(topic.id)
        assertEquals(TopicStatus.Learned, state.status(rules.scheduler, monday))
    }

    @Test fun answersToOtherCoursesOrRemovedExercisesAreIgnored() {
        val other = course(topic("Other"))
        val events = listOf(
            answer(other, other.topics[0], true, at(monday, 8)),
            answer(course, topic, true, at(monday, 9)).let { it.copy(event = (it.event as com.polylingy.core.journal.AnswerSubmitted).copy(exerciseId = kotlin.uuid.Uuid.random())) },
        )
        assertEquals(TopicState<Ladder.Memory, PassFail>(null, emptyList()), replay(course, events, rules(AllCorrect(), 1)).getValue(topic.id))
    }

    @Test fun answersThatDontCountAreSkipped() {
        val nothingCounts = CourseRules(GradingRule<PassFail> { null }, Consecutive(1, 1), Once, conveyor)
        assertEquals(TopicStatus.New, replay(course, events(listOf(listOf(CORRECT))), nothingCounts).getValue(topic.id).status(Once, monday))
    }

    @Test fun eventsAreReplayedInTimeOrder() {
        val rules = rules(AllCorrect(), 2)
        val late = answer(course, topic, false, at(monday, 20))
        val early = events(listOf(listOf(CORRECT), listOf(CORRECT)))
        assertEquals(listOf(PassFail.FAIL), replay(course, listOf(late) + early, rules).getValue(topic.id).round.map { it.grade })
    }
}

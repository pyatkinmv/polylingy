package com.polylingy.core.learning

import com.polylingy.content.Exercise
import com.polylingy.core.exercise
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The ways to choose the exercise of a topic, on a topic of five exercises A–E (docs/learning-model.md, section 7).
 * A history is written as in the journal, oldest first: a letter and ✓ or ✗; a `|` before the answers of the current round.
 */
class ExerciseChoiceTest {
    private val letters = "ABCDE".toList()
    private val exercises: List<Exercise> = letters.map { exercise("task $it {{x}}") }
    private fun id(letter: Char) = exercises[letters.indexOf(letter)].id
    private fun name(e: Exercise) = letters[exercises.indexOf(e)]

    /** "A✓ B✗ | C✓ D✗": the first two answers are of an earlier round, the last two of the current one. */
    private fun history(text: String): List<TopicAnswer> {
        var inRound = false
        val out = mutableListOf<TopicAnswer>()
        for (token in text.split(" ").filter { it.isNotEmpty() }) {
            if (token == "|") { inRound = true; continue }
            out += TopicAnswer(id(token[0]), token[1] == '✓', inRound)
        }
        return out
    }

    private fun missed(text: String, offered: List<Exercise> = exercises) = name(MissedFirst.choose(offered, history(text)))
    private fun order(text: String) = name(InCourseOrder.choose(exercises, history(text)))

    // ---- in course order

    @Test fun inCourseOrderGoesRoundAndRound() {
        assertEquals('A', order(""), "nothing answered: the first")
        assertEquals('B', order("A✓"))
        assertEquals('E', order("D✗"), "right or wrong, it goes on")
        assertEquals('A', order("E✓"), "after the last, the first again")
    }

    @Test fun inCourseOrderStartsFromTheFirstWhenTheLastAnsweredIsNotOffered() {
        val withoutC = exercises - exercises[2]
        assertEquals('A', name(InCourseOrder.choose(withoutC, history("C✓"))), "C was answered but is not among the offered ones")
        assertEquals('D', name(InCourseOrder.choose(withoutC, history("B✓"))))
    }

    // ---- missed first

    @Test fun anEarlierMissComesBeforeAnythingElseTheOldestFirst() {
        assertEquals('D', missed("A✓ D✗ B✗ C✓ | E✓"), "D was missed before B")
        assertEquals('B', missed("A✓ D✗ B✗ D✓ C✓ | E✓"), "D was answered right since, so only B is left")
    }

    @Test fun aMissIsBroughtBackOnlyWhenNoRightAnswerCameAfterIt() {
        assertEquals('D', missed("B✗ B✓ D✗ | A✓"), "B was missed and then answered right: not B")
        assertEquals('B', missed("B✗ D✗ B✗ | A✓"), "the run of misses began with the first B miss, before D's")
    }

    @Test fun whatWasMissedInThisRoundWaitsForTheNextRound() {
        // B was missed in the current round; nothing from earlier rounds is open: the next in order after the last, but not B
        assertEquals('C', missed("A✓ | A✓ B✗"), "B is missed this round: C, not B again")
        assertEquals('A', missed("| D✗ E✓"), "D is missed this round, E was just shown: A")
    }

    @Test fun anExerciseMissedAgainInThisRoundWaitsToo() {
        // D was missed in an earlier round and is open, but it was missed again in this round: it waits for the next round
        assertEquals('A', missed("D✗ | D✗ E✓"), "A, the next after E; D is missed this round")
    }

    @Test fun whenEveryExerciseWasMissedInThisRoundTheyAreAllStillThere() {
        assertEquals('A', missed("| A✗ B✗ C✗ D✗ E✗"), "the next after the last answered, wrapping round")
        assertEquals('B', missed("| B✗ C✗ D✗ E✗ A✗"), "never the one shown last while there are others")
    }

    @Test fun mixedMissesInThisRoundSkipOnlyThoseMissedAndTheLastShown() {
        assertEquals('D', missed("| A✗ B✗ C✓"), "A and B are missed this round and C was just shown: D")
    }

    @Test fun withoutHistoryItStartsFromTheFirstExercise() {
        assertEquals('A', missed(""))
    }

    @Test fun aSingleExerciseIsAlwaysTheOne() {
        val one = listOf(exercises[0])
        assertSame(one[0], MissedFirst.choose(one, history("A✗ | A✗")))
        assertSame(one[0], MissedFirst.choose(one, emptyList()))
        assertSame(one[0], InCourseOrder.choose(one, history("A✓")))
        assertSame(one[0], RandomNotLast(Random(1)).choose(one, history("A✓")))
    }

    @Test fun historyOfExercisesThatAreNotOfferedIsLeftOut() {
        val offered = exercises.take(3)         // A, B, C
        assertEquals('C', name(MissedFirst.choose(offered, history("E✗ D✗ | B✓"))), "E and D are not offered, so their misses do not count: after B comes C")
        assertEquals('C', name(MissedFirst.choose(offered, history("E✗ | A✓ B✗"))), "B is missed this round and shown last: C")
    }

    // ---- random not the last

    @Test fun randomNotLastReadsTheLastFromTheHistory() {
        repeat(20) { assertSame(exercises[1], RandomNotLast(Random(it)).choose(exercises.take(2), history("A✓"))) }
        assertEquals(exercises.take(2).toSet(), (1..30).map { RandomNotLast(Random(it)).choose(exercises.take(2), emptyList()) }.toSet())
    }
}

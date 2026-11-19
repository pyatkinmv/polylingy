package com.polylingy.core.learning

import com.polylingy.content.Exercise
import com.polylingy.content.ItemResult
import com.polylingy.core.journal.AnswerSubmitted
import com.polylingy.core.journal.ZonedInstant
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

/** Which round of a topic this is: before it is passed for the first time, or a review after that. */
enum class Phase { LEARNING, REVIEW }

/** The simplest grade: the answer counts or it doesn't. Other grade types (four buttons like Anki) can come later. */
enum class PassFail { PASS, FAIL }

/** One counted answer of a round: its grade plus the facts, for policies that need more than the grade. */
data class Attempt<G>(val grade: G, val results: List<ItemResult>, val at: ZonedInstant)

/** One pass through a topic: the attempts from the first answer until it is mastered. */
typealias Round<G> = List<Attempt<G>>

/** Turns an answer into a grade; null means the answer doesn't count at all. */
fun interface GradingRule<G> {
    fun grade(answer: AnswerSubmitted): G?
}

/** Looks at the whole round so far: is the topic mastered now? It knows the phase, so learning and review may differ. */
fun interface MasteryPolicy<G> {
    fun isMastered(round: Round<G>, phase: Phase): Boolean
}

/**
 * Remembers what it needs about a topic between rounds ([M], its own shape) and says when to review it.
 * [schedule] gets the finished round; memory is null the first time the topic is mastered.
 */
interface Scheduler<M, G> {
    fun schedule(memory: M?, round: Round<G>, today: LocalDate): M

    /** When to review; null means the topic is learned for good and isn't offered by itself any more. */
    fun dueDate(memory: M): LocalDate?
}

/** Where a topic needed today stands, as the picker sees it. */
enum class Group {
    /** A round is under way: begun today or earlier and not over. */
    STARTED,

    /** Its review date has come. */
    DUE,

    /** Never answered. */
    NEW,
}

/**
 * A topic needed today. Topics resting until a later date and topics learned for good are not candidates —
 * the scheduler decided that. [due] — the review date, for orders that look at how overdue a topic is.
 */
data class Candidate(val topicId: Uuid, val group: Group, val due: LocalDate? = null)

/**
 * The queue answers two questions, and only these (docs/learning-model.md, section 7, «Пикер»):
 *  1. which candidates may be shown now (e.g. no more than N new topics a day);
 *  2. in what order — the first in the list is the one to show first.
 * It doesn't decide whether to stay on the current topic ([Focus]) or which exercise to show ([ExerciseChoice]).
 */
fun interface Queue {
    /** [candidates] come in course order; [newBegunToday] — how many new topics of the course were begun today. */
    fun build(candidates: List<Candidate>, newBegunToday: Int): List<Candidate>
}

/**
 * The focus answers one question, and only this:
 *  3. after an answer, stay on the same topic or move to another.
 * It picks from the queue as it is: it neither reorders nor drops anyone. An empty queue — null: nothing left today.
 */
fun interface Focus {
    /** [current] — the topic just answered; null when the session has just begun. */
    fun pick(queue: List<Candidate>, current: Uuid?): Uuid?
}

/**
 * One earlier answer to an exercise of a topic, as [ExerciseChoice] sees it: [passed] by the course's grading rule, and whether it
 * was given in the topic's current round ([inCurrentRound]; when no round is under way, none is).
 */
data class TopicAnswer(val exerciseId: Uuid, val passed: Boolean, val inCurrentRound: Boolean)

/**
 * Answers one question, and only this:
 *  4. which exercise of the chosen topic to show.
 * [history] — the earlier answers to exercises of this topic, oldest first (derived from the journal; empty for a topic never answered).
 */
fun interface ExerciseChoice {
    fun choose(exercises: List<Exercise>, history: List<TopicAnswer>): Exercise
}

/** What comes next in a session: the three parts, each answering its own questions. */
class Picker(val queue: Queue, val focus: Focus, val exercise: ExerciseChoice)

/** A course's policies. The grade type [G] ties them together, so a mismatched set doesn't compile. */
class CourseRules<M, G>(
    val grading: GradingRule<G>,
    val mastery: MasteryPolicy<G>,
    val scheduler: Scheduler<M, G>,
    val picker: Picker,
)

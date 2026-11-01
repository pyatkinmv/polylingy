package com.polylingy.core.learning

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

/** A topic that may be shown now, in course order. [started] — a round is under way. */
data class Candidate(val topicId: Uuid, val phase: Phase, val started: Boolean)

/** Which topic to show next; [current] is the one just shown, if any. Null when there is nothing to pick. */
fun interface TopicPicker {
    fun pick(candidates: List<Candidate>, current: Uuid?): Uuid?
}

/** A course's policies. The grade type [G] ties them together, so a mismatched set doesn't compile. */
class CourseRules<M, G>(
    val grading: GradingRule<G>,
    val mastery: MasteryPolicy<G>,
    val scheduler: Scheduler<M, G>,
    val picker: TopicPicker,
)

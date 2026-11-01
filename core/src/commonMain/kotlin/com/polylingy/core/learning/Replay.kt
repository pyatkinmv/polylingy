package com.polylingy.core.learning

import com.polylingy.core.content.Course
import com.polylingy.core.journal.AnswerSubmitted
import com.polylingy.core.journal.EventEnvelope
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

/** What replay() knows about a topic: the scheduler's memory (null until first mastered) and the round under way. */
data class TopicState<M, G>(val memory: M?, val round: Round<G>) {
    val phase: Phase get() = if (memory == null) Phase.LEARNING else Phase.REVIEW
}

/** Where a topic stands today, for the screens. */
sealed interface TopicStatus {
    /** Never answered. */
    data object New : TopicStatus

    /** A round is under way. */
    data object InProgress : TopicStatus

    /** Its review date has come. */
    data object Due : TopicStatus

    /** Mastered; comes back on [due]. */
    data class Passed(val due: LocalDate) : TopicStatus

    /** Learned for good: not offered by itself any more. */
    data object Learned : TopicStatus

    /** Offered by the picker today. Any topic can still be practised by choice. */
    val available: Boolean get() = this == New || this == InProgress || this == Due
}

fun <M, G> TopicState<M, G>.status(scheduler: Scheduler<M, G>, today: LocalDate): TopicStatus {
    if (round.isNotEmpty()) return TopicStatus.InProgress
    val memory = memory ?: return TopicStatus.New
    val due = scheduler.dueDate(memory) ?: return TopicStatus.Learned
    return if (due <= today) TopicStatus.Due else TopicStatus.Passed(due)
}

/**
 * Derives every topic's state from the journal with the course's current policies: each answer is graded, added to its
 * topic's round, and a mastered round is scheduled. Answers to topics or exercises no longer in the course are ignored;
 * answers are never checked again — their saved results are used. Answers given ahead of the review date count too:
 * practising early is a round like any other.
 *
 * TODO(perf): replays the whole journal on every call. Simple and always right, but slow once there are tens of
 *  thousands of events; replace with a projection that is updated as events come and can be rebuilt from the journal.
 */
fun <M, G> replay(course: Course, events: List<EventEnvelope>, rules: CourseRules<M, G>): Map<Uuid, TopicState<M, G>> {
    val exercises = course.allTopics.associate { topic -> topic.id to topic.exercises.map { it.id }.toSet() }
    val states = exercises.keys.associateWith { TopicState<M, G>(null, emptyList()) }.toMutableMap()

    // Stable sort: events with the same instant keep their journal (append) order.
    for (envelope in events.sortedBy { it.occurredAt.instant }) {
        val answer = envelope.event as? AnswerSubmitted ?: continue
        if (answer.courseId != course.id || answer.exerciseId !in exercises[answer.topicId].orEmpty()) continue
        val grade = rules.grading.grade(answer) ?: continue
        val state = states.getValue(answer.topicId)
        val round: Round<G> = state.round + Attempt<G>(grade, answer.results, envelope.occurredAt)
        states[answer.topicId] = if (rules.mastery.isMastered(round, state.phase)) {
            TopicState(rules.scheduler.schedule(state.memory, round, envelope.occurredAt.localDate), emptyList())
        } else {
            state.copy(round = round)
        }
    }
    return states
}

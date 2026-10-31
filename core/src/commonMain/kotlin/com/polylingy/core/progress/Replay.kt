package com.polylingy.core.progress

import com.polylingy.core.content.Course
import com.polylingy.core.journal.AnswerSubmitted
import com.polylingy.core.journal.EventEnvelope
import com.polylingy.core.rules.ConsecutiveCorrectPolicy
import com.polylingy.core.rules.CorrectnessRule
import com.polylingy.core.rules.GraduationPolicy
import com.polylingy.core.rules.RatingThenAutoCheck
import com.polylingy.core.rules.RepetitionPolicy
import com.polylingy.core.rules.Sm2Policy
import com.polylingy.core.rules.Streak
import com.polylingy.core.rules.StreakOutcome
import kotlin.uuid.Uuid

/** The rules a journal is replayed with. Changing them recomputes the whole history. */
data class LearningRules(
    val repetition: RepetitionPolicy = Sm2Policy(),
    val graduation: GraduationPolicy = ConsecutiveCorrectPolicy(),
    val correctness: CorrectnessRule = RatingThenAutoCheck,
)

data class TopicState(val progress: TopicProgress, val streak: Streak = Streak())

/**
 * Derives the state of every topic in [courses] from the journal. Events for topics that
 * no longer exist in the courses are ignored.
 *
 * TODO(perf): replays the entire journal on every call. Simple and always correct, but slow
 *  once there are tens of thousands of events; replace with a projection that is updated
 *  incrementally and can be rebuilt from the journal at any time.
 */
fun replay(
    courses: List<Course>,
    events: List<EventEnvelope>,
    rules: LearningRules = LearningRules(),
): Map<Uuid, TopicState> {
    val courseOfTopic = courses.flatMap { course -> course.topics.map { it.id to course.id } }.toMap()
    val states = courseOfTopic.keys.associateWith { TopicState(TopicProgress(it)) }.toMutableMap()

    val ordered = events.sortedWith(compareBy({ it.occurredAt.instant }, { it.eventId.toString() }))
    for (envelope in ordered) {
        val answer = envelope.event as? AnswerSubmitted ?: continue
        if (courseOfTopic[answer.topicId] != answer.courseId) continue

        val state = states.getValue(answer.topicId)
        states[answer.topicId] = when (val outcome = rules.graduation.record(state.streak, rules.correctness.isCorrect(answer))) {
            is StreakOutcome.InProgress -> state.copy(streak = outcome.streak)
            is StreakOutcome.Graduated -> TopicState(
                rules.repetition.advance(state.progress, outcome.mistakes, envelope.occurredAt.localDate),
            )
        }
    }
    return states
}

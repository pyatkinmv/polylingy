package com.polylingy.core.journal

import com.polylingy.content.ItemResult
import com.polylingy.content.Response
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlin.uuid.Uuid

/**
 * One entry of the learning journal. Entries are immutable and only ever appended;
 * format changes bump [schemaVersion], and older versions must stay readable.
 */
@Serializable
data class EventEnvelope(
    val eventId: Uuid,
    val deviceId: Uuid,
    val occurredAt: ZonedInstant,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val event: Event,
) {
    companion object {
        /** 1: the course's version was called `courseRevision`; 2: `courseVersion` (old entries are read as they are, by that alias). */
        const val CURRENT_SCHEMA_VERSION = 2
    }
}

@Serializable
sealed interface Event

/**
 * The learner answered one exercise. It keeps the facts: what was entered and what checking said about each item at
 * that moment. Grades, rounds and schedules are derived from these by replay() with the course's current policies,
 * and old answers are never checked again, so editing an exercise doesn't rewrite the past.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@SerialName("AnswerSubmitted")
data class AnswerSubmitted(
    val courseId: Uuid,
    val topicId: Uuid,
    val exerciseId: Uuid,
    /** The course's [com.polylingy.core.content.Course.version] when the answer was given: which edition of the exercise was on screen. */
    @JsonNames("courseRevision") val courseVersion: Int,
    val response: Response,
    /** One per item of the exercise: blank, choice, pair or sentence. */
    val results: List<ItemResult>,
) : Event

/**
 * The learner answered one exercise in extra practice: the same facts as [AnswerSubmitted], but a fact of its own kind. Practice
 * stands apart from the plan: progress, schedules and the choice of what comes next are derived only from [AnswerSubmitted],
 * so a [PracticeAnswered] can't change them. Statistics count it as activity.
 */
@Serializable
@SerialName("PracticeAnswered")
data class PracticeAnswered(
    val courseId: Uuid,
    val topicId: Uuid,
    val exerciseId: Uuid,
    val courseVersion: Int,
    val response: Response,
    val results: List<ItemResult>,
) : Event {
    /** The same answer as an [AnswerSubmitted], for what only reads the facts of an answer (grading, counting). */
    fun asAnswer() = AnswerSubmitted(courseId, topicId, exerciseId, courseVersion, response, results)
}

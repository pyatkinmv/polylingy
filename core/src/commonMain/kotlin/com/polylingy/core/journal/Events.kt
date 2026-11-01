package com.polylingy.core.journal

import com.polylingy.content.ItemResult
import com.polylingy.content.Response
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
sealed interface Event

/**
 * The learner answered one exercise. It keeps the facts: what was entered and what checking said about each item at
 * that moment. Grades, rounds and schedules are derived from these by replay() with the course's current policies,
 * and old answers are never checked again, so editing an exercise doesn't rewrite the past.
 */
@Serializable
@SerialName("AnswerSubmitted")
data class AnswerSubmitted(
    val courseId: Uuid,
    val topicId: Uuid,
    val exerciseId: Uuid,
    /** The course's revision when the answer was given: which version of the exercise was on screen. */
    val courseRevision: Int,
    val response: Response,
    /** One per item of the exercise: blank, choice, pair or sentence. */
    val results: List<ItemResult>,
) : Event

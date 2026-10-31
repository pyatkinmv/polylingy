package com.polylingy.core.journal

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

/** The user answered one exercise and (optionally) rated the answer. */
@Serializable
@SerialName("AnswerSubmitted")
data class AnswerSubmitted(
    val courseId: Uuid,
    val topicId: Uuid,
    val exerciseId: Uuid,
    val input: String,
    /** Null when the exercise has no automatic check. */
    val autoCheck: AutoCheck? = null,
    /** Null when the course has no self-rating. */
    val rating: Rating? = null,
) : Event

@Serializable
enum class AutoCheck { CORRECT, PARTIAL, INCORRECT }

/** A self-rating on a course-defined [scale], e.g. `("binary", "correct")`. */
@Serializable
data class Rating(val scale: String, val value: String) {
    companion object {
        const val BINARY = "binary"
        val CORRECT = Rating(BINARY, "correct")
        val INCORRECT = Rating(BINARY, "incorrect")
    }
}

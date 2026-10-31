package com.polylingy.core.content

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A course is a versioned document: it is always saved and synced as a whole.
 * On conflict the higher [revision] wins; [updatedAt] breaks ties.
 */
@Serializable
data class Course(
    val id: Uuid,
    val name: String,
    val createdAt: Instant,
    val revision: Int,
    val updatedAt: Instant,
    val topics: List<Topic>,
)

/** The unit of spaced repetition; each time it is shown, one of its [exercises] is picked. */
@Serializable
data class Topic(
    val id: Uuid,
    val subject: String,
    val explanation: Explanation? = null,
    val exercises: List<Exercise>,
)

/** A task with blanks `{0}`, `{1}`, … whose expected values are in [answers], keyed by blank number. */
@Serializable
data class Exercise(
    val id: Uuid,
    val task: String,
    val answers: Map<String, String>,
    val explanation: String? = null,
)

@Serializable
data class Explanation(
    val text: String,
    val format: ExplanationFormat = ExplanationFormat.TEXT,
)

@Serializable
enum class ExplanationFormat { TEXT, HTML }

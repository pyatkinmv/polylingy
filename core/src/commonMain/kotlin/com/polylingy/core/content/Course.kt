package com.polylingy.core.content

import com.polylingy.content.Content
import com.polylingy.content.Exercise
import com.polylingy.content.Inline
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** The course file format this app reads and writes; see docs/content-format.md, section 9. */
const val FORMAT_VERSION = 13

/** At most this many extra keys, each at most this long (docs/content-format.md). */
const val MAX_EXTRA_KEYS = 12
const val MAX_EXTRA_KEY_LENGTH = 8

/**
 * A course is a versioned document: it is always saved and synced as a whole, in the same JSON as the course file.
 * On conflict the higher [version] wins; [updatedAt] breaks ties. A course has either [units] or [topics].
 */
@Serializable
data class Course(
    @EncodeDefault val formatVersion: Int = FORMAT_VERSION,
    val id: Uuid,
    val name: String,
    val description: Inline? = null,
    val createdAt: Instant,
    /**
     * The version of the course's content: 1 for the first, and one more with every change of the document, whether made in the app
     * (a unit or topic edited, the name or description) or by a newer file of the course ([CourseService.update]). It is kept as it is
     * on import, so an exported file carries it; a file of a course already in the app updates it only when its version is higher.
     * Every answer in the journal remembers the version it was given on. On conflict the higher version wins.
     */
    @EncodeDefault val version: Int = 1,
    val updatedAt: Instant,
    val theme: Theme? = null,
    /**
     * Keys the course adds to every text field because the keyboard has none for them (the letters of its language, say):
     * the field shows them above itself while it has focus, and a key puts its text where the cursor is.
     */
    val extraKeys: List<String> = emptyList(),
    /**
     * Course policies as the file gives them; without them, the ladder preset. They are copied into the course's settings
     * on import and not used after that: the settings are what counts (docs/architecture.md, section 5).
     */
    val rules: RulesSpec? = null,
    val units: List<CourseUnit> = emptyList(),
    val topics: List<Topic> = emptyList(),
    /**
     * The document as read, for a course of a newer format: it holds content this app can't write,
     * so it is saved from this text, and the course can be studied but not exported.
     */
    @Transient val sourceJson: String? = null,
) {
    val studyOnly: Boolean get() = formatVersion > FORMAT_VERSION

    /** Topics in course order: unit by unit, or the course's own topics. */
    val allTopics: List<Topic> get() = units.flatMap { it.topics } + topics
}

/** A part of a course: a page of theory and the topics that practise it. */
@Serializable
data class CourseUnit(
    val id: Uuid,
    val title: String,
    val subtitle: Inline? = null,
    val theory: Content? = null,
    val topics: List<Topic>,
)

/** The unit of spaced repetition; each time it is shown, one of its [exercises] is picked. */
@Serializable
data class Topic(
    val id: Uuid,
    val title: String,
    /** Shown after an exercise is checked when the exercise has no explanation of its own. */
    val explanation: Content? = null,
    val exercises: List<Exercise>,
)

/** [accent] is a colour like `#2F6B5E`; without it the app's own colour is used. */
@Serializable
data class Theme(val accent: String? = null)

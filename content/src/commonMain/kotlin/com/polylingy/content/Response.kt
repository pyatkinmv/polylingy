package com.polylingy.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What the learner entered for one exercise. It is stored in the journal as is. */
@Serializable
sealed interface Response {
    /** For gaps, pictures and table: what was typed into each blank, in reading order. */
    @Serializable
    @SerialName("blanks")
    data class Blanks(val values: List<String>) : Response

    /** For choice: the picked option of each item, or null if none. */
    @Serializable
    @SerialName("choice")
    data class Choice(val picked: List<String?>) : Response

    /** For match: for each pair, the index in [Exercise.Match.rightSides] matched to its left side, or null. */
    @Serializable
    @SerialName("match")
    data class Match(val matched: List<Int?>) : Response

    /** For order: the words of each sentence in the order the learner put them. */
    @Serializable
    @SerialName("order")
    data class Order(val sentences: List<List<String>>) : Response
}

/** The result of checking one item. */
@Serializable
enum class ItemResult { CORRECT, TYPO, WRONG }

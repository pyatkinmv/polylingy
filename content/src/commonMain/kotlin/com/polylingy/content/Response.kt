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

/** The result of one item together with the right answer to show next to it (for a typo, the variant it was close to). */
data class ItemCheck(val result: ItemResult, val rightAnswer: String)

/** What the learner has entered before touching anything: empty blanks, nothing picked, matched or placed. */
fun Exercise.emptyResponse(): Response = when (this) {
    is Exercise.Gaps, is Exercise.Pictures, is Exercise.Table -> Response.Blanks(List(itemCount) { "" })
    is Exercise.Choice -> Response.Choice(List(items.size) { null })
    is Exercise.Match -> Response.Match(List(pairs.size) { null })
    is Exercise.Order -> Response.Order(List(items.size) { emptyList() })
    is Exercise.Unknown -> throw IllegalArgumentException("an exercise of unknown type \"$type\" can't be answered")
}

/** All blanks of a gaps, pictures or table exercise in reading order; the `values` of [Response.Blanks] follow it. */
val Exercise.blanks: List<InlineNode.Blank>
    get() = when (this) {
        is Exercise.Gaps -> items.flatMap { it.text.blanks }
        is Exercise.Pictures -> items.flatMap { it.text.blanks }
        is Exercise.Table -> rows.flatten().flatMap { it.blanks }
        is Exercise.Choice, is Exercise.Match, is Exercise.Order, is Exercise.Unknown -> emptyList()
    }

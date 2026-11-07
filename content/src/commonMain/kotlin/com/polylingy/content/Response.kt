package com.polylingy.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What the learner entered for one exercise. It is stored in the journal as is. */
@Serializable
sealed interface Response {
    /**
     * For gaps, pictures and table: what was typed into each blank, in reading order. For expand: the whole answer of
     * each item. For fix: the fix typed for each mark, "" for a mark kept as it is.
     */
    @Serializable
    @SerialName("blanks")
    data class Blanks(val values: List<String>) : Response

    /** For choice: the picked option of each place ([Exercise.Choice.places]), or null if none. */
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

/**
 * The result of one item together with the right answer to show next to it (for a typo, the variant it was close to).
 * [reason] is a short line on what is wrong, where the checker can tell (expand: a missing key word, a wrong order).
 */
data class ItemCheck(val result: ItemResult, val rightAnswer: String, val reason: String? = null)

/** What the learner has entered before touching anything: empty blanks, nothing picked, matched or placed. */
fun Exercise.emptyResponse(): Response = when (this) {
    is Exercise.Gaps, is Exercise.Pictures, is Exercise.Table, is Exercise.Expand, is Exercise.Fix -> Response.Blanks(List(itemCount) { "" })
    is Exercise.Choice -> Response.Choice(List(itemCount) { null })
    is Exercise.Match -> Response.Match(List(pairs.size) { null })
    is Exercise.Order -> Response.Order(List(items.size) { emptyList() })
    is Exercise.Unknown -> throw IllegalArgumentException("an exercise of unknown type \"$type\" can't be answered")
}

/** All blanks of an exercise answered with [Response.Blanks], in reading order; its `values` follow it. */
val Exercise.blanks: List<InlineNode.Blank>
    get() = when (this) {
        is Exercise.Gaps -> items.flatMap { it.text.blanks }
        is Exercise.Pictures -> items.flatMap { it.text.blanks }
        is Exercise.Table -> rows.flatten().flatMap { it.blanks }
        is Exercise.Expand -> items.flatMap { it.text.blanks }
        is Exercise.Fix -> items.flatMap { it.text.blanks }
        is Exercise.Choice, is Exercise.Match, is Exercise.Order, is Exercise.Unknown -> emptyList()
    }

/**
 * Whether [response] has an answer for every item, so that "Check" makes sense: every blank typed, every place picked,
 * every pair matched (sides that fit nothing stay free), every sentence put together. A fix keeps a mark as it is with an
 * empty answer, so it is always complete.
 */
fun Exercise.isComplete(response: Response): Boolean = when (this) {
    is Exercise.Fix -> true
    is Exercise.Gaps, is Exercise.Pictures, is Exercise.Table, is Exercise.Expand ->
        (response as? Response.Blanks)?.values?.all { it.isNotBlank() } == true
    is Exercise.Choice -> (response as? Response.Choice)?.picked?.all { it != null } == true
    is Exercise.Match -> (response as? Response.Match)?.matched?.all { it != null } == true
    is Exercise.Order -> (response as? Response.Order)?.sentences?.let { placed ->
        placed.size == items.size && items.indices.all { placed[it].size == items[it].words.size }
    } == true
    is Exercise.Unknown -> false
}

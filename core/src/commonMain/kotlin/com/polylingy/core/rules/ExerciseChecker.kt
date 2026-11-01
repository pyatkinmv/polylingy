package com.polylingy.core.rules

import com.polylingy.content.Exercise
import com.polylingy.content.ItemCheck
import com.polylingy.content.ItemResult
import com.polylingy.content.Response
import com.polylingy.content.Side
import com.polylingy.content.blanks

/** How strict checking is; set per course (docs/content-format.md, section 2). */
data class CheckSettings(val allowTypos: Boolean = true)

/** Checks the answer to one exercise type: one [ItemCheck] per blank, choice, pair or sentence. It knows nothing of how the exercise looks. */
fun interface ExerciseChecker<E : Exercise, R : Response> {
    fun check(exercise: E, response: R, settings: CheckSettings): List<ItemCheck>
}

/** Text typed into blanks (gaps, pictures, table): each blank against its variants by [TextMatcher]. */
object BlanksChecker : ExerciseChecker<Exercise, Response.Blanks> {
    override fun check(exercise: Exercise, response: Response.Blanks, settings: CheckSettings): List<ItemCheck> {
        val blanks = exercise.blanks
        require(response.values.size == blanks.size) { "expected ${blanks.size} answers, got ${response.values.size}" }
        return blanks.zip(response.values) { blank, given ->
            val match = TextMatcher.match(given, blank.variants, settings.allowTypos)
            ItemCheck(match.result, match.variant)
        }
    }
}

/** The picked option must be the right one; no typos when nothing is typed. */
object ChoiceChecker : ExerciseChecker<Exercise.Choice, Response.Choice> {
    override fun check(exercise: Exercise.Choice, response: Response.Choice, settings: CheckSettings): List<ItemCheck> {
        require(response.picked.size == exercise.items.size) { "expected ${exercise.items.size} picks, got ${response.picked.size}" }
        return exercise.items.zip(response.picked) { item, picked ->
            val answer = item.text.blanks.single().answer
            ItemCheck(if (picked == answer) ItemResult.CORRECT else ItemResult.WRONG, answer)
        }
    }
}

/** Each pair's left side must be matched with its own right side; [Exercise.Match.rightSides] lists them first, in pair order. */
object MatchChecker : ExerciseChecker<Exercise.Match, Response.Match> {
    override fun check(exercise: Exercise.Match, response: Response.Match, settings: CheckSettings): List<ItemCheck> {
        require(response.matched.size == exercise.pairs.size) { "expected ${exercise.pairs.size} matches, got ${response.matched.size}" }
        return response.matched.mapIndexed { i, matched ->
            ItemCheck(if (matched == i) ItemResult.CORRECT else ItemResult.WRONG, exercise.pairs[i][1].label())
        }
    }

    private fun Side.label() = when (this) {
        is Side.Text -> text.plainText()
        is Side.Picture -> image.alt
    }
}

/** The words must be in the answer's order or in one of the accepted orders. */
object OrderChecker : ExerciseChecker<Exercise.Order, Response.Order> {
    override fun check(exercise: Exercise.Order, response: Response.Order, settings: CheckSettings): List<ItemCheck> {
        require(response.sentences.size == exercise.items.size) { "expected ${exercise.items.size} sentences, got ${response.sentences.size}" }
        return exercise.items.zip(response.sentences) { item, words ->
            val right = listOf(item.words) + item.accept.map { Exercise.OrderItem(it).words }
            ItemCheck(if (words in right) ItemResult.CORRECT else ItemResult.WRONG, item.answer)
        }
    }
}

/** Checks any exercise with the checker of its type. */
object Checking {
    fun check(exercise: Exercise, response: Response, settings: CheckSettings): List<ItemCheck> = when (exercise) {
        is Exercise.Gaps, is Exercise.Pictures, is Exercise.Table -> BlanksChecker.check(exercise, response.expect(), settings)
        is Exercise.Choice -> ChoiceChecker.check(exercise, response.expect(), settings)
        is Exercise.Match -> MatchChecker.check(exercise, response.expect(), settings)
        is Exercise.Order -> OrderChecker.check(exercise, response.expect(), settings)
        is Exercise.Unknown -> throw IllegalArgumentException("an exercise of unknown type \"${exercise.type}\" can't be checked")
    }

    private inline fun <reified R : Response> Response.expect(): R =
        this as? R ?: throw IllegalArgumentException("expected a ${R::class.simpleName} response, got ${this::class.simpleName}")
}

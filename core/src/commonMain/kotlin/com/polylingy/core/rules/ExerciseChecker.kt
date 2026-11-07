package com.polylingy.core.rules

import com.polylingy.content.Exercise
import com.polylingy.content.ItemCheck
import com.polylingy.content.ItemResult
import com.polylingy.content.Response
import com.polylingy.content.Side
import com.polylingy.content.blanks

/**
 * Checks the answer to one exercise type: one [ItemCheck] per blank, choice, pair or sentence. It knows nothing of how
 * the exercise looks. A typo is always reported as [ItemResult.TYPO]; whether it counts is the course's grading.
 */
fun interface ExerciseChecker<E : Exercise, R : Response> {
    fun check(exercise: E, response: R): List<ItemCheck>
}

/** Text typed into blanks (gaps, pictures, table): each blank against its variants by [TextMatcher]. */
object BlanksChecker : ExerciseChecker<Exercise, Response.Blanks> {
    override fun check(exercise: Exercise, response: Response.Blanks): List<ItemCheck> {
        val blanks = exercise.blanks
        require(response.values.size == blanks.size) { "expected ${blanks.size} answers, got ${response.values.size}" }
        return blanks.zip(response.values) { blank, given ->
            val match = TextMatcher.match(given, blank.variants)
            ItemCheck(match.result, match.variant)
        }
    }
}

/** The picked option of each place must be the right one; no typos when nothing is typed. */
object ChoiceChecker : ExerciseChecker<Exercise.Choice, Response.Choice> {
    override fun check(exercise: Exercise.Choice, response: Response.Choice): List<ItemCheck> {
        val places = exercise.places
        require(response.picked.size == places.size) { "expected ${places.size} picks, got ${response.picked.size}" }
        return places.zip(response.picked) { place, picked ->
            ItemCheck(if (picked == place.answer) ItemResult.CORRECT else ItemResult.WRONG, place.answer)
        }
    }
}

/**
 * A typed answer is right when it is a model answer, or has the key words in one of the allowed orders
 * ([TextMatcher.keys]); a wrong one gets a one-line reason. The model answer is shown next to it.
 */
object ExpandChecker : ExerciseChecker<Exercise.Expand, Response.Blanks> {
    override fun check(exercise: Exercise.Expand, response: Response.Blanks): List<ItemCheck> {
        require(response.values.size == exercise.items.size) { "expected ${exercise.items.size} answers, got ${response.values.size}" }
        return exercise.items.zip(response.values) { item, given ->
            val variants = item.text.blanks.single().variants
            val model = TextMatcher.match(given, variants)
            if (model.result == ItemResult.CORRECT) return@zip ItemCheck(ItemResult.CORRECT, model.variant)
            val keys = TextMatcher.keys(given, item.keys)
            ItemCheck(keys.result, variants.first(), keys.reason)
        }
    }
}

/**
 * Each mark: a right word must be kept (an empty value, or the word itself typed again); a wrong one must be fixed
 * with one of its fixes, by [TextMatcher]. The right answer shown is the word itself for a right one, the first fix
 * for a wrong one.
 */
object FixChecker : ExerciseChecker<Exercise.Fix, Response.Blanks> {
    override fun check(exercise: Exercise.Fix, response: Response.Blanks): List<ItemCheck> {
        val marks = exercise.marks
        require(response.values.size == marks.size) { "expected ${marks.size} answers, got ${response.values.size}" }
        return marks.zip(response.values) { mark, given ->
            val kept = given.isBlank() || TextMatcher.normalize(given) == TextMatcher.normalize(mark.shown)
            when {
                !mark.wrong -> ItemCheck(if (kept) ItemResult.CORRECT else ItemResult.WRONG, mark.shown)
                kept -> ItemCheck(ItemResult.WRONG, mark.fixes.first())
                else -> TextMatcher.match(given, mark.fixes).let { ItemCheck(it.result, it.variant) }
            }
        }
    }
}

/** Each pair's left side must be matched with its own right side; [Exercise.Match.rightSides] lists them first, in pair order. */
object MatchChecker : ExerciseChecker<Exercise.Match, Response.Match> {
    override fun check(exercise: Exercise.Match, response: Response.Match): List<ItemCheck> {
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
    override fun check(exercise: Exercise.Order, response: Response.Order): List<ItemCheck> {
        require(response.sentences.size == exercise.items.size) { "expected ${exercise.items.size} sentences, got ${response.sentences.size}" }
        return exercise.items.zip(response.sentences) { item, words ->
            val right = listOf(item.words) + item.accept.map { Exercise.OrderItem(it).words }
            ItemCheck(if (words in right) ItemResult.CORRECT else ItemResult.WRONG, item.shown)
        }
    }
}

/** Checks any exercise with the checker of its type. */
object Checking {
    fun check(exercise: Exercise, response: Response): List<ItemCheck> = when (exercise) {
        is Exercise.Gaps, is Exercise.Pictures, is Exercise.Table -> BlanksChecker.check(exercise, response.expect())
        is Exercise.Choice -> ChoiceChecker.check(exercise, response.expect())
        is Exercise.Match -> MatchChecker.check(exercise, response.expect())
        is Exercise.Order -> OrderChecker.check(exercise, response.expect())
        is Exercise.Expand -> ExpandChecker.check(exercise, response.expect())
        is Exercise.Fix -> FixChecker.check(exercise, response.expect())
        is Exercise.Unknown -> throw IllegalArgumentException("an exercise of unknown type \"${exercise.type}\" can't be checked")
    }

    private inline fun <reified R : Response> Response.expect(): R =
        this as? R ?: throw IllegalArgumentException("expected a ${R::class.simpleName} response, got ${this::class.simpleName}")
}

/** The checks as the learner sees them: a typo the course doesn't forgive is shown as wrong; the journal keeps the typo. */
fun List<ItemCheck>.forgiving(typos: Boolean): List<ItemCheck> =
    if (typos) this else map { if (it.result == ItemResult.TYPO) it.copy(result = ItemResult.WRONG) else it }

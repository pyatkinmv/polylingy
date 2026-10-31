package com.polylingy.app.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test

// The study part of the parity checklist in docs/migration-plan.md.
class StudyUiTest {
    /** One topic with two blanks and both kinds of explanation, so every screen is deterministic. */
    private val colors = """
        {"name": "Spanish", "topics": [{
          "subject": "Colors",
          "generalExplanation": "Adjectives <b>agree</b> in gender:<ul><li>rojo</li><li>roja</li></ul>",
          "generalExplanationFormat": "HTML",
          "exercises": [{"task": "red: {0} / {1}", "answer": {"0": "rojo", "1": "roja"},
                         "exampleExplanation": "el coche rojo, la manzana roja"}]
        }]}
    """.trimIndent()

    private val translate = """
        {"name": "Words", "topics": [{"subject": "Greetings", "exercises": [{"task": "Translate: hello", "answer": "hola"}]}]}
    """.trimIndent()

    private val answerField = "Your answers (comma-separated)"

    private fun AppHarness.answer(input: String) {
        text(answerField).performTextInput(input)
        text(answerField).performKeyInput { pressKey(Key.Enter) }
        test.waitForIdle()
    }

    private fun AppHarness.rateWithKey(key: Key) {
        test.onRoot().performKeyInput { pressKey(key) }
        test.waitForIdle()
    }

    @Test fun answeringScreenMasksBlanks() = appTest(seed = { it.seedCourse(colors) }) {
        click("Start")
        text("Colors").assertIsDisplayed()
        text("red: ___ / ___").assertIsDisplayed()
        text(answerField).assertIsDisplayed()
        screenshot("study-answering")
    }

    @Test fun enterSubmitsAndTheResultShowsWhatWasWrong() = appTest(seed = { it.seedCourse(colors) }) {
        click("Start")
        answer("Rojo, rosa")
        text("red: rojo / rosa → roja").assertIsDisplayed()
        text("Specific explanation").assertIsDisplayed()
        text("el coche rojo, la manzana roja").assertIsDisplayed()
        // HTML is rendered, not shown as tags.
        text("Adjectives agree in gender:\n• rojo\n• roja").assertIsDisplayed()
        screenshot("study-result-partial")
    }

    @Test fun threeCorrectAnswersByKeyboardFinishTheDay() = appTest(seed = { it.seedCourse(colors) }) {
        click("Start")
        repeat(3) {
            answer("rojo, roja")
            rateWithKey(Key.Two)
        }
        text("All done for today!").assertIsDisplayed()
        screenshot("study-done")

        click("Back to home")
        text("0 new · 0 to review · 1 done today").assertIsDisplayed()
    }

    @Test fun ratingButtonsWorkWithTheMouse() = appTest(seed = { it.seedCourse(colors) }) {
        click("Start")
        answer("ROJO , roja")                 // case and spaces don't matter
        text("red: rojo / roja").assertIsDisplayed()
        screenshot("study-result-correct")
        click("Incorrect")
        repeat(3) {
            answer("rojo, roja")
            click("Correct")
        }
        text("All done for today!").assertIsDisplayed()
    }

    @Test fun keyOneRatesIncorrectAndKeepsTheTopic() = appTest(seed = { it.seedCourse(colors) }) {
        click("Start")
        answer("rojo, roja")
        rateWithKey(Key.One)
        repeat(2) {
            answer("rojo, roja")
            rateWithKey(Key.Two)
        }
        text("red: ___ / ___").assertIsDisplayed()
    }

    @Test fun streakSurvivesLeavingTheScreen() = appTest(seed = { it.seedCourse(colors) }) {
        click("Start")
        repeat(2) {
            answer("rojo, roja")
            rateWithKey(Key.Two)
        }
        click("←")
        click("Start")
        answer("rojo, roja")
        rateWithKey(Key.Two)
        text("All done for today!").assertIsDisplayed()
    }

    @Test fun topicComesBackOnItsReviewDate() = appTest(seed = { it.seedCourse(colors) }) {
        click("Start")
        repeat(3) {
            answer("rojo, roja")
            rateWithKey(Key.Two)
        }
        clock.now = LocalDateTime(2026, 1, 18, 9, 0)
        click("Back to home")
        text("0 new · 1 to review · 0 done today").assertIsDisplayed()
    }

    @Test fun removeTopicFromTheStudyScreen() = appTest(seed = { it.seedCourse(colors) }) {
        click("Start")
        menu("Remove topic")
        text("Are you sure you want to remove \"Colors\"? This cannot be undone.").assertIsDisplayed()
        click("OK")
        text("All done for today!").assertIsDisplayed()
        click("Back to home")
        text("0 new · 0 to review · 0 done today").assertIsDisplayed()
    }

    @Test fun taskWithoutBlanksShowsTheAnswerOnItsOwnLine() = appTest(seed = { it.seedCourse(translate) }) {
        click("Start")
        text("Your answer").performTextInput("adios")
        click("Submit")
        text("Translate: hello\nadios → hola").assertIsDisplayed()
        screenshot("study-result-no-blanks")
    }
}

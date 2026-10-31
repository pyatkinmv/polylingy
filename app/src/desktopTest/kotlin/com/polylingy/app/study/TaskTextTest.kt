package com.polylingy.app.study

import com.polylingy.core.content.Exercise
import com.polylingy.core.rules.AnswerChecker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class TaskTextTest {
    private fun exercise(task: String, vararg answers: String) =
        Exercise(Uuid.random(), task, answers.withIndex().associate { (i, a) -> "$i" to a })

    private fun annotate(task: String, input: String, vararg answers: String): List<TaskPart> {
        val exercise = exercise(task, *answers)
        return annotateTask(exercise.task, AnswerChecker.check(exercise, input))
    }

    @Test fun blanksAreMasked() = assertEquals("el coche ___, la manzana ___.", maskBlanks("el coche {0}, la manzana {1}."))

    @Test fun correctBlankShowsTheAnswer() = assertEquals(
        listOf(TaskPart.Plain("red: "), TaskPart.Filled("rojo", true), TaskPart.Plain("!")),
        annotate("red: {0}!", "ROJO", "rojo"),
    )

    @Test fun wrongBlankShowsGivenThenExpected() = assertEquals(
        listOf(TaskPart.Filled("rojo", true), TaskPart.Plain(" / "), TaskPart.Filled("rosa → roja", false)),
        annotate("{0} / {1}", "rojo, rosa ", "rojo", "roja"),
    )

    @Test fun emptyBlankShowsOnlyTheExpectedAnswerInRed() =
        assertEquals(listOf(TaskPart.Filled("rojo", false)), annotate("{0}", "", "rojo"))

    @Test fun taskWithoutPlaceholdersGetsTheAnswerOnANewLine() = assertEquals(
        listOf(TaskPart.Plain("Translate: hello"), TaskPart.Plain("\n"), TaskPart.Filled("adios → hola", false)),
        annotate("Translate: hello", "adios", "hola"),
    )
}

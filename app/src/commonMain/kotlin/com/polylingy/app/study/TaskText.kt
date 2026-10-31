package com.polylingy.app.study

import com.polylingy.core.rules.AnswerCheck

private val Blank = Regex("""\{(\d+)\}""")

/** The task as shown while answering: every `{n}` becomes `___`. */
fun maskBlanks(task: String): String = task.replace(Blank, "___")

sealed interface TaskPart {
    data class Plain(val text: String) : TaskPart

    /** A filled-in blank: the expected answer, or `given → expected` when the user got it wrong. */
    data class Filled(val text: String, val correct: Boolean) : TaskPart
}

/**
 * The task as shown on the result screen, with each blank filled in and marked right or wrong.
 * Tasks without `{n}` placeholders (e.g. "Translate: hello") get the answer appended on its own
 * line — the Flutter version showed no answer at all for them.
 */
fun annotateTask(task: String, check: AnswerCheck): List<TaskPart> {
    val byKey = check.blanks.associateBy { it.key }
    val parts = mutableListOf<TaskPart>()
    var last = 0
    for (match in Blank.findAll(task)) {
        if (match.range.first > last) parts += TaskPart.Plain(task.substring(last, match.range.first))
        val blank = byKey[match.groupValues[1]]
        parts += filled(given = blank?.given.orEmpty(), expected = blank?.expected.orEmpty(), correct = blank?.correct ?: true)
        last = match.range.last + 1
    }
    if (last < task.length) parts += TaskPart.Plain(task.substring(last))

    if (!Blank.containsMatchIn(task)) {
        parts += TaskPart.Plain("\n")
        check.blanks.forEachIndexed { i, blank ->
            if (i > 0) parts += TaskPart.Plain(", ")
            parts += filled(blank.given, blank.expected, blank.correct)
        }
    }
    return parts
}

private fun filled(given: String, expected: String, correct: Boolean): TaskPart.Filled = when {
    correct -> TaskPart.Filled(expected, true)
    given.isBlank() -> TaskPart.Filled(expected, false)
    else -> TaskPart.Filled("${given.trim()} → $expected", false)
}

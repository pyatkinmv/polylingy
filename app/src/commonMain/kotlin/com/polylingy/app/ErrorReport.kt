package com.polylingy.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the user is told when something unexpected goes wrong: a short text; the details (the stack trace) go to the log. */
data class UnexpectedError(val message: String, val count: Int = 1)

/**
 * Unexpected errors that nobody handled, for the window to show instead of the app dying or going quiet. Each platform's
 * last-resort handler calls [report]; [App] shows [latest] in a dialog until it is dismissed.
 */
object ErrorReport {
    private val _latest = MutableStateFlow<UnexpectedError?>(null)

    /** The error being shown; further errors while it is up only raise its [UnexpectedError.count]. */
    val latest: StateFlow<UnexpectedError?> = _latest.asStateFlow()

    fun report(error: Throwable) {
        val text = describe(error)
        _latest.value = _latest.value?.let { it.copy(count = it.count + 1) } ?: UnexpectedError(text)
    }

    fun dismiss() {
        _latest.value = null
    }

    private fun describe(error: Throwable): String {
        val root = generateSequence(error) { it.cause?.takeIf { cause -> cause !== it } }.last()
        val detail = root.message?.lineSequence()?.firstOrNull()?.take(200)
        return if (detail.isNullOrBlank()) root::class.simpleName ?: "Unknown error" else "${root::class.simpleName}: $detail"
    }
}

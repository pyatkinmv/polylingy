package com.polylingy.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import com.polylingy.app.ErrorReport
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// An error nobody handled is shown in a dialog instead of the app going quiet; see docs/testing.md.
class ErrorUiTest {
    @AfterTest fun reset() = ErrorReport.dismiss()

    @Test fun anUnexpectedErrorIsShownAndCanBeDismissed() = appTest(seed = { it.seedSample() }) {
        ErrorReport.report(IllegalStateException("wrapper", java.io.IOException("The disk is full")))
        test.waitForIdle()
        text("Something went wrong").assertIsDisplayed()
        text("IOException: The disk is full").assertIsDisplayed()
        screenshot("error-dialog")
        click("OK")
        text("Something went wrong").assertDoesNotExist()
        assertNull(ErrorReport.latest.value)
    }

    @Test fun furtherErrorsWhileTheDialogIsUpAreCounted() = appTest(seed = { it.seedSample() }) {
        repeat(3) { ErrorReport.report(RuntimeException("again")) }
        test.waitForIdle()
        text("It happened 3 times.").assertIsDisplayed()
        assertEquals("RuntimeException: again", ErrorReport.latest.value!!.message)
    }
}

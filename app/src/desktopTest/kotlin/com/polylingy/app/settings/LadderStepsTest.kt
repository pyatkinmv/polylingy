package com.polylingy.app.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The ladder is a fixed round series; a course only chooses where it ends, and nothing is longer than two years.
class LadderStepsTest {
    @Test fun aLadderEndsWhereItIsChosen() {
        assertEquals(listOf(1, 3, 7, 14, 30), LadderSteps.upTo(30))
        assertEquals(listOf(1, 3, 7, 14, 30, 60, 90, 180, 365), LadderSteps.upTo(365))
        assertEquals(LadderSteps.SERIES, LadderSteps.upTo(730))
    }

    @Test fun everyChoiceOfTheLastStepIsAStepOfTheSeriesAndNothingIsLongerThanTwoYears() {
        assertTrue(LadderSteps.LAST_STEPS.all { it in LadderSteps.SERIES })
        assertEquals(730, LadderSteps.SERIES.max())
        assertEquals(LadderSteps.STANDARD, LadderSteps.upTo(LadderSteps.LAST_STEPS.first()), "the first choice is the standard ladder")
    }

    @Test fun theLastStepsAreNamed() {
        assertEquals("30 days", LadderSteps.title(30))
        assertEquals("1 year", LadderSteps.title(365))
        assertEquals("2 years", LadderSteps.title(730))
    }
}

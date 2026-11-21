package com.polylingy.app

import java.awt.Dimension
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WindowSizeTest {
    private val bigScreen = Rectangle(0, 0, 3840, 2100)

    @Test fun onWindowsTheMinimumIsScaledToPhysicalPixels() {
        assertEquals(Dimension(900, 640), minimumWindowSize(900, 640, 1.0, isMac = false, bigScreen))
        assertEquals(Dimension(1575, 1120), minimumWindowSize(900, 640, 1.75, isMac = false, bigScreen), "at 175 % the window must not shrink to 514")
    }

    @Test fun onMacOSTheMinimumIsInPointsWhateverTheScale() {
        // A MacBook Air 13": 2560×1664 pixels at scale 2, 1470×956 points, less the menu bar.
        val air = Rectangle(0, 33, 1470, 923)
        assertEquals(Dimension(900, 640), minimumWindowSize(900, 640, 2.0, isMac = true, air), "not 1800×1280, bigger than the screen")
    }

    @Test fun theMinimumIsNeverBiggerThanTheFreePartOfTheScreen() {
        val small = Rectangle(0, 0, 1366, 728)
        assertEquals(Dimension(1350, 728), minimumWindowSize(900, 640, 1.5, isMac = false, small), "960 pixels high would not fit in 728")
    }

    @Test fun aWindowThatFitsIsLeftAlone() {
        assertNull(fittedWindowBounds(Rectangle(100, 100, 1000, 720), Rectangle(0, 25, 1470, 931)))
    }

    @Test fun aWindowBiggerThanTheScreenIsShrunkAndMovedOntoIt() {
        assertEquals(Rectangle(0, 25, 1470, 931), fittedWindowBounds(Rectangle(200, 100, 1800, 1280), Rectangle(0, 25, 1470, 931)))
        assertEquals(Rectangle(470, 25, 1000, 931), fittedWindowBounds(Rectangle(600, 0, 1000, 1280), Rectangle(0, 25, 1470, 931)), "the width that fits is kept")
    }
}

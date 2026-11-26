package com.rinzaki.babylog

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionRowLayoutTest {
    @Test
    fun compactWidthsUseTwoColumnsForLongerFilterLabels() {
        assertEquals(2, selectionRowColumnCount(3, 292f))
        assertEquals(2, selectionRowColumnCount(4, 332f))
        assertEquals(2, selectionRowColumnCount(6, 359.9f))
    }

    @Test
    fun regularWidthsPreserveExistingThreeAndFourColumnLayouts() {
        assertEquals(3, selectionRowColumnCount(3, 360f))
        assertEquals(4, selectionRowColumnCount(4, 392f))
        assertEquals(3, selectionRowColumnCount(6, 392f))
    }

    @Test
    fun oneOrTwoOptionsDoNotCreateEmptyColumns() {
        assertEquals(1, selectionRowColumnCount(1, 280f))
        assertEquals(2, selectionRowColumnCount(2, 280f))
        assertEquals(1, selectionRowColumnCount(0, 280f))
    }
}

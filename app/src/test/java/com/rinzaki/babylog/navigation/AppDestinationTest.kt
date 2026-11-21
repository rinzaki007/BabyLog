package com.rinzaki.babylog.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDestinationTest {
    @Test
    fun bottomNavigationHasFiveStableDestinations() {
        assertEquals(listOf("首页", "记录", "成长", "相册", "疫苗"), BabyLogDestination.entries.map { it.label })
        assertEquals(5, BabyLogDestination.entries.distinct().size)
    }

    @Test
    fun vaccineIsAnIndependentPrimaryDestination() {
        assertTrue(BabyLogDestination.entries.contains(BabyLogDestination.VACCINE))
        assertEquals(4, BabyLogDestination.entries.indexOf(BabyLogDestination.VACCINE))
    }

    @Test
    fun settingsAreNotRepresentedAsABottomNavigationDestination() {
        assertFalse(BabyLogDestination.entries.any { it.label == "更多" || it.label == "设置" })
    }
}

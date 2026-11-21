package com.rinzaki.babylog.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDestinationTest {
    @Test
    fun bottomNavigationHasSixStableDestinations() {
        assertEquals(listOf("首页", "记录", "成长", "相册", "疫苗", "更多"), BabyLogDestination.entries.map { it.label })
        assertEquals(6, BabyLogDestination.entries.distinct().size)
    }

    @Test
    fun vaccineIsAnIndependentPrimaryDestinationBeforeMoreSettings() {
        assertTrue(BabyLogDestination.entries.contains(BabyLogDestination.VACCINE))
        assertTrue(BabyLogDestination.entries.indexOf(BabyLogDestination.VACCINE) < BabyLogDestination.entries.indexOf(BabyLogDestination.MORE))
    }

    @Test
    fun moreRemainsTheSettingsDestination() {
        assertEquals("更多", BabyLogDestination.MORE.label)
    }
}

package com.rinzaki.babylog.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDestinationTest {
    @Test
    fun bottomNavigationHasFiveStableDestinations() {
        assertEquals(
            listOf("首页", "记录", "成长", "相册", "更多"),
            BabyLogDestination.entries.map { it.label }
        )
        assertEquals(5, BabyLogDestination.entries.distinct().size)
    }

    @Test
    fun vaccineManagementIsGroupedUnderMoreRatherThanPrimaryNavigation() {
        assertTrue(BabyLogDestination.entries.contains(BabyLogDestination.MORE))
        assertTrue(BabyLogDestination.entries.none { it.label == "疫苗" })
    }
}

package com.rinzaki.babylog.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncConflictDisplayTest {
    @Test
    fun loadsTheNextPageWithoutExceedingTotalConflictCount() {
        assertEquals(40, nextSyncConflictDisplayLimit(20, 45))
        assertEquals(45, nextSyncConflictDisplayLimit(40, 45))
    }

    @Test
    fun doesNotIncreaseLimitPastTotalOrForAnEmptyQueue() {
        assertEquals(12, nextSyncConflictDisplayLimit(20, 12))
        assertEquals(0, nextSyncConflictDisplayLimit(0, 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeDisplayLimits() {
        nextSyncConflictDisplayLimit(-1, 10)
    }
}

package com.rinzaki.babylog

import org.junit.Assert.assertEquals
import org.junit.Test

class FamilySyncStatusTest {
    @Test
    fun unconfiguredSyncTakesPriority() {
        assertEquals("未配置", familySyncStatusLabel(false, false, false, 3, 1, 100L))
    }

    @Test
    fun activeSyncIsShownBeforePreviousError() {
        assertEquals("同步中", familySyncStatusLabel(true, true, true, 2, 0, 100L))
    }

    @Test
    fun errorAndConflictAreNotReportedAsHealthy() {
        assertEquals("需检查", familySyncStatusLabel(true, false, true, 0, 1, 100L))
        assertEquals("有冲突", familySyncStatusLabel(true, false, false, 0, 1, 100L))
    }

    @Test
    fun pendingQueueIsNotReportedAsHealthyAfterPreviousSuccess() {
        assertEquals("待同步", familySyncStatusLabel(true, false, false, 2, 0, 100L))
    }

    @Test
    fun emptyQueueUsesLastSuccessfulSyncState() {
        assertEquals("正常", familySyncStatusLabel(true, false, false, 0, 0, 100L))
        assertEquals("待首次同步", familySyncStatusLabel(true, false, false, 0, 0, 0L))
    }
}

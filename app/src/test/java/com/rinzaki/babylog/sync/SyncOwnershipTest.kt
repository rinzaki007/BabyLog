package com.rinzaki.babylog.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncOwnershipTest {
    @Test
    fun allowsOnlyOperationsOwnedByTheActiveFamily() {
        assertTrue(syncOperationBelongsToFamily("family-a", "family-a"))
        assertFalse(syncOperationBelongsToFamily("family-a", "family-b"))
        assertFalse(syncOperationBelongsToFamily(null, "family-a"))
        assertFalse(syncOperationBelongsToFamily("family-a", ""))
    }

    @Test
    fun onlyFamilyOwnershipConflictsMayRemainQueuedAcrossAccountSwitches() {
        assertTrue(canSwitchAccountWithConflicts(0, emptyList()))
        assertTrue(canSwitchAccountWithConflicts(0, listOf(FAMILY_OWNERSHIP_CONFLICT_REASON)))
        assertFalse(canSwitchAccountWithConflicts(1, listOf(FAMILY_OWNERSHIP_CONFLICT_REASON)))
        assertFalse(canSwitchAccountWithConflicts(0, listOf("HTTP 409: version conflict")))
        assertFalse(
            canSwitchAccountWithConflicts(
                0,
                listOf(FAMILY_OWNERSHIP_CONFLICT_REASON, "HTTP 409: version conflict")
            )
        )
    }

    @Test
    fun familyOwnershipConflictsCannotBeRetriedUntilTheMatchingFamilyIsActive() {
        assertFalse(canRetrySyncConflict(FAMILY_OWNERSHIP_CONFLICT_REASON))
        assertTrue(canRetrySyncConflict("HTTP 409: version conflict"))
        assertTrue(canRetrySyncConflict("HTTP 408: request timeout"))
        assertTrue(canRetrySyncConflict("HTTP 429: too many requests"))
        assertFalse(canRetrySyncConflict("HTTP 400: invalid payload"))
        assertFalse(canRetrySyncConflict("HTTP 401: unauthorized"))
        assertFalse(canRetrySyncConflict("HTTP 403: forbidden"))
        assertFalse(canRetrySyncConflict("HTTP 404: not found"))
        assertFalse(canRetrySyncConflict("照片上传失败 HTTP 413: file too large"))
        assertTrue(canRetrySyncConflict("本地照片暂时无法读取"))
        assertTrue(canRetrySyncConflict(null))
    }

    @Test
    fun authoritativeSnapshotRequiresEveryBabyToBelongToTheActiveFamily() {
        assertTrue(canSubmitAuthoritativeSnapshot(listOf("family-a"), "family-a"))
        assertTrue(canSubmitAuthoritativeSnapshot(listOf("family-a", "family-a"), "family-a"))
        assertFalse(canSubmitAuthoritativeSnapshot(listOf("family-a", "family-b"), "family-a"))
        assertFalse(canSubmitAuthoritativeSnapshot(listOf("local-only"), "family-a"))
        assertFalse(canSubmitAuthoritativeSnapshot(emptyList(), "family-a"))
        assertFalse(canSubmitAuthoritativeSnapshot(listOf("family-a"), ""))
    }

    @Test
    fun retriesTransientHttpStatusesButStopsOnPermanentClientErrors() {
        assertTrue(shouldRetrySyncStatus(408))
        assertTrue(shouldRetrySyncStatus(425))
        assertTrue(shouldRetrySyncStatus(429))
        assertTrue(shouldRetrySyncStatus(500))
        assertTrue(shouldRetrySyncStatus(503))
        assertTrue(shouldRetrySyncStatus(409))
        assertFalse(shouldRetrySyncStatus(400))
        assertFalse(shouldRetrySyncStatus(401))
        assertFalse(shouldRetrySyncStatus(403))
        assertFalse(shouldRetrySyncStatus(404))
    }
}

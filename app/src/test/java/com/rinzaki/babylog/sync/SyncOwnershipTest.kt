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
        assertTrue(canRetrySyncConflict(null))
    }

    @Test
    fun retriesTransientHttpStatusesButStopsOnPermanentClientErrors() {
        assertTrue(shouldRetrySyncStatus(408))
        assertTrue(shouldRetrySyncStatus(425))
        assertTrue(shouldRetrySyncStatus(429))
        assertTrue(shouldRetrySyncStatus(500))
        assertTrue(shouldRetrySyncStatus(503))
        assertFalse(shouldRetrySyncStatus(400))
        assertFalse(shouldRetrySyncStatus(401))
        assertFalse(shouldRetrySyncStatus(403))
        assertFalse(shouldRetrySyncStatus(404))
    }
}

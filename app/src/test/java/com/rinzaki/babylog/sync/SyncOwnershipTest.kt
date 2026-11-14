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
}

package com.rinzaki.babylog.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncExecutionLockTest {
    @Test
    fun accountTransitionWaitsForAnInFlightSyncRun() = runBlocking {
        val syncEntered = CompletableDeferred<Unit>()
        val finishSync = CompletableDeferred<Unit>()
        val transitionEntered = CompletableDeferred<Unit>()

        val syncJob = launch {
            withSyncExecutionLock {
                syncEntered.complete(Unit)
                finishSync.await()
            }
        }

        syncEntered.await()
        val transitionJob = launch {
            withSyncExecutionLock {
                transitionEntered.complete(Unit)
            }
        }

        try {
            yield()
            assertFalse(transitionEntered.isCompleted)
        } finally {
            finishSync.complete(Unit)
        }

        syncJob.join()
        transitionJob.join()
        assertTrue(transitionEntered.isCompleted)
    }
}

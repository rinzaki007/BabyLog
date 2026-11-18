package com.rinzaki.babylog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerTest {
    @Test
    fun runningTimerMeasuresElapsedTimeFromPersistedStart() {
        val state = startSleepTimer(1_000L)
        assertTrue(state.isActive)
        assertTrue(state.isRunning)
        assertEquals(90_000L, sleepTimerElapsedMs(state, 91_000L))
    }

    @Test
    fun pauseAndResumePreserveElapsedTimeWithoutCountingThePause() {
        val started = startSleepTimer(1_000L)
        val paused = pauseSleepTimer(started, 31_000L)
        assertFalse(paused.isRunning)
        assertEquals(30_000L, sleepTimerElapsedMs(paused, 90_000L))

        val resumed = resumeSleepTimer(paused, 90_000L)
        assertTrue(resumed.isRunning)
        assertEquals(50_000L, sleepTimerElapsedMs(resumed, 110_000L))
    }

    @Test
    fun completedTimerUsesOriginalStartAndRoundedDuration() {
        val started = startSleepTimer(10_000L)
        val completed = completeSleepTimer(started, 100_000L)
        assertEquals(10_000L, completed?.startedAtEpochMs)
        assertEquals(100_000L, completed?.endedAtEpochMs)
        assertEquals(2, completed?.durationMinutes)
    }

    @Test
    fun inactiveTimerCannotBeCompletedAndElapsedNeverGoesNegative() {
        assertNull(completeSleepTimer(SleepTimerState(), 10_000L))
        val state = startSleepTimer(20_000L)
        assertEquals(0L, sleepTimerElapsedMs(state, 10_000L))
    }
}

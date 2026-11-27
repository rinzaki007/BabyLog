package com.rinzaki.babylog

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordSliderStepTest {
    @Test
    fun feedingSliderUsesFiveMilliliterIntervals() {
        assertEquals(198, discreteSliderStepCount(5, 1000, 5))
        assertEquals(5, snapSliderValue(6f, 5, 1000, 5))
        assertEquals(10, snapSliderValue(7.6f, 5, 1000, 5))
        assertEquals(1000, snapSliderValue(1000f, 5, 1000, 5))
    }

    @Test
    fun sleepSliderUsesFiveMinuteIntervals() {
        assertEquals(141, discreteSliderStepCount(10, 720, 5))
        assertEquals(10, snapSliderValue(11f, 10, 720, 5))
        assertEquals(15, snapSliderValue(13f, 10, 720, 5))
        assertEquals(720, snapSliderValue(720f, 10, 720, 5))
    }
}

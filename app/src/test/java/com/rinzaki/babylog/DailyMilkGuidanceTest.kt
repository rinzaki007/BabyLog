package com.rinzaki.babylog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class DailyMilkGuidanceTest {
    private val zone = ZoneOffset.UTC
    private val birthDate = LocalDate.of(2026, 1, 15)
    private val birthTime = birthDate.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun exactAssessmentMonthReturnsOfficialRange() {
        val guidance = dailyMilkGuidanceForDate(birthTime, LocalDate.of(2026, 2, 15), zone)

        assertEquals(1, guidance?.assessmentAgeMonths)
        assertEquals(600, guidance?.minimumMl)
        assertEquals(700, guidance?.maximumMl)
        assertTrue(guidance?.isExactAssessmentAge == true)
    }

    @Test
    fun betweenAssessmentMonthsUsesNextCheckpointWithoutInterpolation() {
        val guidance = dailyMilkGuidanceForDate(birthTime, LocalDate.of(2026, 5, 15), zone)

        assertEquals(6, guidance?.assessmentAgeMonths)
        assertEquals(800, guidance?.minimumMl)
        assertEquals(1000, guidance?.maximumMl)
        assertFalse(guidance?.isExactAssessmentAge ?: true)
    }

    @Test
    fun ageBeyondPublishedCheckpointsHasNoGuidance() {
        assertNull(dailyMilkGuidanceForDate(birthTime, LocalDate.of(2028, 2, 15), zone))
    }

    @Test
    fun dateBeforeBirthHasNoGuidance() {
        assertNull(dailyMilkGuidanceForDate(birthTime, LocalDate.of(2026, 1, 14), zone))
    }
}

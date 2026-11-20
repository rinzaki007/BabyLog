package com.rinzaki.babylog

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class GrowthMeasurementAgeTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun at(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()
    private val birth = at(LocalDate.of(2026, 1, 15))

    @Test
    fun formatsAgeUsingCompletedCalendarMonths() {
        assertEquals("出生当天", growthMeasurementAgeLabel(birth, birth, zone))
        assertEquals("14天", growthMeasurementAgeLabel(birth, at(LocalDate.of(2026, 1, 29)), zone))
        assertEquals("1个月", growthMeasurementAgeLabel(birth, at(LocalDate.of(2026, 2, 15)), zone))
        assertEquals("1个月3天", growthMeasurementAgeLabel(birth, at(LocalDate.of(2026, 2, 18)), zone))
    }

    @Test
    fun handlesMonthEndBirthdaysWithoutUsingThirtyDayApproximation() {
        val monthEndBirth = at(LocalDate.of(2026, 1, 31))
        assertEquals("27天", growthMeasurementAgeLabel(monthEndBirth, at(LocalDate.of(2026, 2, 27)), zone))
        assertEquals("1个月", growthMeasurementAgeLabel(monthEndBirth, at(LocalDate.of(2026, 2, 28)), zone))
        assertEquals("2个月", growthMeasurementAgeLabel(monthEndBirth, at(LocalDate.of(2026, 3, 31)), zone))
    }

    @Test
    fun identifiesMeasurementsBeforeBirth() {
        assertEquals("出生前", growthMeasurementAgeLabel(birth, at(LocalDate.of(2026, 1, 14)), zone))
    }
}

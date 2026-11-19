package com.rinzaki.babylog

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthLongTermDataTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun at(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun groupsMeasurementsByCalendarMonthAndKeepsActualChanges() {
        val points = listOf(
            at(LocalDate.of(2026, 1, 2)) to 3.2f,
            at(LocalDate.of(2026, 1, 20)) to 3.8f,
            at(LocalDate.of(2026, 3, 4)) to 4.5f
        )
        val summaries = growthMonthlySummaries(points, zone)
        assertEquals(2, summaries.size)
        assertEquals(2, summaries[0].measurementCount)
        assertEquals(0.6f, summaries[0].change, 0.001f)
        assertEquals(18L, summaries[0].spanDays)
        assertEquals(4.5f, summaries[1].latestValue, 0.001f)
    }

    @Test
    fun omitsEmptyMonthsAndLimitsHistoryToMostRecentMonths() {
        val points = (1..4).map { month ->
            at(LocalDate.of(2026, month, 1)) to month.toFloat()
        }
        val summaries = growthMonthlySummaries(points, zone, maxMonths = 2)
        assertEquals(2, summaries.size)
        assertEquals(3, summaries.first().month.monthValue)
        assertEquals(4, summaries.last().month.monthValue)
    }

    @Test
    fun ignoresInvalidMeasurementsAndRejectsInvalidMonthLimit() {
        val summaries = growthMonthlySummaries(
            listOf(at(LocalDate.of(2026, 1, 1)) to Float.NaN, at(LocalDate.of(2026, 1, 2)) to 0f),
            zone
        )
        assertTrue(summaries.isEmpty())
    }

    @Test
    fun calculatesObservedChangeAndThirtyDayRateFromActualInterval() {
        val interval = growthMeasurementInterval(
            listOf(
                at(LocalDate.of(2026, 1, 1)) to 3.0f,
                at(LocalDate.of(2026, 1, 31)) to 3.6f
            ),
            zone
        )!!
        assertEquals(30L, interval.elapsedDays)
        assertEquals(0.6f, interval.change, 0.001f)
        assertEquals(0.6f, interval.changePer30Days!!, 0.001f)
    }

    @Test
    fun doesNotInventRateForSameDayOrIntervalForSingleMeasurement() {
        assertNull(growthMeasurementInterval(listOf(at(LocalDate.of(2026, 1, 1)) to 3f), zone))
        val sameDay = growthMeasurementInterval(
            listOf(at(LocalDate.of(2026, 1, 1)) to 3f, at(LocalDate.of(2026, 1, 1)) to 3.2f),
            zone
        )!!
        assertEquals(0L, sameDay.elapsedDays)
        assertNull(sameDay.changePer30Days)
    }
}

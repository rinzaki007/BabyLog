package com.rinzaki.babylog

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodTrendDataTest {
    private val start = LocalDate.of(2026, 11, 2) // Monday

    @Test
    fun aggregatesWeeklyValuesAndPreservesZeroDays() {
        val daily = (0..9).map { offset ->
            DailyTrendPoint(start.plusDays(offset.toLong()), if (offset == 1 || offset == 8) 2f else 0f)
        }
        val periods = aggregateTrendPeriods(daily, "周")
        assertEquals(2, periods.size)
        assertEquals(2f, periods[0].value)
        assertEquals(1, periods[0].activeDays)
        assertEquals(7, periods[0].coveredDays)
        assertEquals(start.plusDays(7), periods[1].startDate)
        assertEquals(2f, periods[1].value)
        assertEquals(1, periods[1].activeDays)
        assertEquals(3, periods[1].coveredDays)
    }

    @Test
    fun aggregatesCalendarMonthsAcrossYearBoundary() {
        val daily = listOf(
            DailyTrendPoint(LocalDate.of(2026, 12, 31), 3f),
            DailyTrendPoint(LocalDate.of(2027, 1, 1), 4f),
            DailyTrendPoint(LocalDate.of(2027, 1, 2), 0f)
        )
        val periods = aggregateTrendPeriods(daily, "月")
        assertEquals(2, periods.size)
        assertEquals(LocalDate.of(2026, 12, 31), periods[0].startDate)
        assertEquals(LocalDate.of(2026, 12, 31), periods[0].endDate)
        assertEquals(3f, periods[0].value)
        assertEquals(LocalDate.of(2027, 1, 1), periods[1].startDate)
        assertEquals(4f, periods[1].value)
        assertEquals(1, periods[1].activeDays)
    }

    @Test
    fun keepsPartialPeriodsAndEmptyBucketsBetweenObservations() {
        val daily = listOf(
            DailyTrendPoint(LocalDate.of(2026, 11, 2), 1f),
            DailyTrendPoint(LocalDate.of(2026, 11, 16), 0f)
        )
        val periods = aggregateTrendPeriods(daily, "周")
        assertEquals(3, periods.size)
        assertEquals(1f, periods.first().value)
        assertEquals(0f, periods[1].value)
        assertTrue(periods[1].coveredDays > 0)
        assertEquals(0f, periods.last().value)
    }

    @Test
    fun invalidDailyValuesDoNotPoisonPeriodTotalsOrActiveDayCounts() {
        val daily = listOf(
            DailyTrendPoint(start, 2f),
            DailyTrendPoint(start, Float.NaN),
            DailyTrendPoint(start, Float.POSITIVE_INFINITY),
            DailyTrendPoint(start, -5f),
            DailyTrendPoint(start.plusDays(1), 3f)
        )

        val periods = aggregateTrendPeriods(daily, "周")

        assertEquals(5f, periods.single().value)
        assertEquals(2, periods.single().activeDays)
        assertEquals(2, periods.single().coveredDays)
        assertTrue(periods.single().value.isFinite())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownPeriod() {
        aggregateTrendPeriods(emptyList(), "日")
    }
}

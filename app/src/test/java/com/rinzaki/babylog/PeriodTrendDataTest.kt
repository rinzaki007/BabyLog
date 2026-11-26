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
        assertEquals(1, periods[0].nonZeroDays)
        assertEquals(7, periods[0].coveredDays)
        assertEquals(start.plusDays(7), periods[1].startDate)
        assertEquals(2f, periods[1].value)
        assertEquals(1, periods[1].nonZeroDays)
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
        assertEquals(1, periods[1].nonZeroDays)
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
        assertEquals(2, periods.single().nonZeroDays)
        assertEquals(2, periods.single().coveredDays)
        assertTrue(periods.single().value.isFinite())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownPeriod() {
        aggregateTrendPeriods(emptyList(), "日")
    }

    @Test
    fun selectedPeriodComparesWithImmediatelyPreviousCalendarBucket() {
        val periods = aggregateTrendPeriods(
            (0..13).map { offset ->
                DailyTrendPoint(start.plusDays(offset.toLong()), if (offset < 7) 2f else 3f)
            },
            "周"
        )

        assertEquals(null, periodTrendComparison(periods, 0))
        val comparison = periodTrendComparison(periods, 1)
        assertEquals(7f, comparison?.change ?: Float.NaN, 0.001f)
        assertEquals(0, comparison?.nonZeroDaysChange)
        assertEquals(null, periodTrendComparison(periods, 2))
    }

    @Test
    fun selectedPeriodDoesNotComparePartialAndCompleteBuckets() {
        val daily = (0..9).map { offset ->
            DailyTrendPoint(start.plusDays(offset.toLong()), 1f)
        }
        val weekly = aggregateTrendPeriods(daily, "周")
        assertEquals(7, weekly[0].coveredDays)
        assertEquals(3, weekly[1].coveredDays)
        assertEquals(null, periodTrendComparison(weekly, 1))

        val monthly = aggregateTrendPeriods(
            listOf(
                DailyTrendPoint(LocalDate.of(2026, 11, 30), 1f),
                DailyTrendPoint(LocalDate.of(2026, 12, 1), 1f),
                DailyTrendPoint(LocalDate.of(2026, 12, 2), 1f)
            ),
            "月"
        )
        assertEquals(1, monthly[0].coveredDays)
        assertEquals(2, monthly[1].coveredDays)
        assertEquals(null, periodTrendComparison(monthly, 1))
    }

    @Test
    fun selectedPeriodRejectsNonFiniteComparisonResult() {
        val periods = listOf(
            PeriodTrendPoint(start, start.plusDays(6), Float.MAX_VALUE, 1, 7),
            PeriodTrendPoint(start.plusDays(7), start.plusDays(13), -Float.MAX_VALUE, 1, 7)
        )
        assertEquals(null, periodTrendComparison(periods, 1))
    }

}

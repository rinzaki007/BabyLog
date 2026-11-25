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
    fun singleMeasurementMonthComparesWithPreviousActualObservationAcrossEmptyMonths() {
        val points = listOf(
            at(LocalDate.of(2026, 1, 10)) to 3.2f,
            at(LocalDate.of(2026, 4, 5)) to 4.1f
        )
        val summaries = growthMonthlySummaries(points, zone)
        val april = summaries.last()
        assertEquals(1, april.measurementCount)
        assertEquals(3.2f, april.previousValue!!, 0.001f)
        assertEquals(85L, april.daysSincePreviousMeasurement)
    }

    @Test
    fun firstRecordedMonthHasNoPreviousObservationComparison() {
        val summary = growthMonthlySummaries(
            listOf(at(LocalDate.of(2026, 4, 5)) to 4.1f),
            zone
        ).single()
        assertNull(summary.previousValue)
        assertNull(summary.daysSincePreviousMeasurement)
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

    @Test
    fun measurementHistoryShowsLatestFiveByDefaultAndAllWhenExpanded() {
        val points = (1L..8L).map { it to it.toFloat() }.reversed()
        assertEquals((4L..8L).toList(), visibleGrowthMeasurements(points, expanded = false).map { it.first })
        assertEquals((1L..8L).toList(), visibleGrowthMeasurements(points, expanded = true).map { it.first })
    }

    @Test
    fun measurementHistoryIndicesKeepIdenticalMeasurementsDistinct() {
        val duplicate = 1_000L to 3.5f
        val points = listOf(
            500L to 3.0f,
            duplicate,
            duplicate,
            1_500L to 4.0f
        )
        assertEquals(listOf(0, 1, 2, 3), visibleGrowthMeasurementIndices(points, expanded = true))
        assertEquals(listOf(1, 2, 3), visibleGrowthMeasurementIndices(points, expanded = false, limit = 3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun measurementHistoryIndicesRejectNegativeLimit() {
        visibleGrowthMeasurementIndices(emptyList(), expanded = false, limit = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun measurementHistoryRejectsNegativeLimit() {
        visibleGrowthMeasurements(emptyList(), expanded = false, limit = -1)
    }

    @Test
    fun growthChartHalfYearRangeUsesCalendarMonthsAtMonthEnd() {
        val now = LocalDate.of(2026, 8, 31).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val expected = LocalDate.of(2026, 2, 28).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, growthRangeStartMillis("近半年", now, zone))
    }

    @Test
    fun growthChartNinetyDayRangeIncludesNinetyLocalCalendarDates() {
        val now = LocalDate.of(2026, 8, 31).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val expected = LocalDate.of(2026, 6, 3).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(expected, growthRangeStartMillis("近 90 天", now, zone))
    }

    @Test
    fun growthChartThirtyDayRangeIncludesWholeFirstAndCurrentDays() {
        val now = LocalDate.of(2026, 8, 31).atTime(18, 45).atZone(zone).toInstant().toEpochMilli()
        val expected = LocalDate.of(2026, 8, 2).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(expected, growthRangeStartMillis("近 30 天", now, zone))
    }

    @Test
    fun growthChartYearRangeHandlesLeapDayAndShortRangesUseCalendarDates() {
        val leapDay = LocalDate.of(2024, 2, 29).atTime(9, 30).atZone(zone).toInstant().toEpochMilli()
        val yearExpected = LocalDate.of(2023, 2, 28).atTime(9, 30).atZone(zone).toInstant().toEpochMilli()
        assertEquals(yearExpected, growthRangeStartMillis("近一年", leapDay, zone))

        // The 30-day preset includes 30 local calendar dates, including leap day.
        val thirtyDayExpected = LocalDate.of(2024, 1, 31).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(thirtyDayExpected, growthRangeStartMillis("近 30 天", leapDay, zone))
        assertNull(growthRangeStartMillis("全部", leapDay, zone))
    }
}

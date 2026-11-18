package com.rinzaki.babylog

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatisticsPeriodTest {
    private val today = LocalDate.of(2026, 11, 18)

    @Test
    fun weekStartsOnMonday() {
        assertEquals(LocalDate.of(2026, 11, 16), statisticsDateRange("本周", today).startDate)
        assertEquals(3, statisticsDateRange("本周", today).dayCount)
    }

    @Test
    fun monthAndQuarterUseCalendarBoundaries() {
        assertEquals(LocalDate.of(2026, 11, 1), statisticsDateRange("本月", today).startDate)
        assertEquals(LocalDate.of(2026, 10, 1), statisticsDateRange("本季度", today).startDate)
    }

    @Test
    fun rollingRangesIncludeToday() {
        assertEquals(LocalDate.of(2026, 11, 12), statisticsDateRange("近7天", today).startDate)
        assertEquals(7, statisticsDateRange("近7天", today).dayCount)
        assertEquals(30, statisticsDateRange("近30天", today).dayCount)
        assertEquals(90, statisticsDateRange("近90天", today).dayCount)
    }

    @Test
    fun customRangeNormalizesReversedDates() {
        val range = statisticsDateRange(
            "自定义", today,
            customStart = LocalDate.of(2026, 11, 15),
            customEnd = LocalDate.of(2026, 11, 10)
        )
        assertEquals(LocalDate.of(2026, 11, 10), range.startDate)
        assertEquals(LocalDate.of(2026, 11, 15), range.endDate)
        assertEquals(6, range.dayCount)
    }

    @Test
    fun customRangeDoesNotIncludeFutureDates() {
        val range = statisticsDateRange(
            "自定义", today,
            customStart = LocalDate.of(2026, 11, 20),
            customEnd = LocalDate.of(2026, 11, 25)
        )
        assertEquals(today, range.startDate)
        assertEquals(today, range.endDate)
    }

    @Test
    fun pickerDateConversionIsTimezoneIndependent() {
        val date = LocalDate.of(2026, 3, 8)
        assertEquals(date, pickerMillisToLocalDate(localDateToPickerMillis(date)))
    }
}

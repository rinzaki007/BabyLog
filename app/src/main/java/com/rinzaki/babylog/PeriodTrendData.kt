package com.rinzaki.babylog

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

internal data class PeriodTrendPoint(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val value: Float,
    val activeDays: Int,
    val coveredDays: Int
)

/**
 * Aggregates an already complete daily series into calendar weeks or months.
 * Zero-value dates remain part of the period, while activeDays counts dates with
 * an observed value. Partial first/last periods are intentionally retained.
 */
internal fun aggregateTrendPeriods(
    dailyPoints: List<DailyTrendPoint>,
    period: String
): List<PeriodTrendPoint> {
    require(period == "周" || period == "月") { "period must be 周 or 月" }
    if (dailyPoints.isEmpty()) return emptyList()

    val ordered = dailyPoints.sortedBy { it.date }
    val first = ordered.first().date
    val last = ordered.last().date
    fun bucketStart(date: LocalDate): LocalDate = when (period) {
        "周" -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        else -> date.withDayOfMonth(1)
    }
    fun nextBucket(date: LocalDate): LocalDate = when (period) {
        "周" -> date.plusWeeks(1)
        else -> YearMonth.from(date).plusMonths(1).atDay(1)
    }
    fun bucketEnd(start: LocalDate): LocalDate = when (period) {
        "周" -> start.plusDays(6)
        else -> YearMonth.from(start).atEndOfMonth()
    }

    val totals = ordered.groupBy { bucketStart(it.date) }
    val result = mutableListOf<PeriodTrendPoint>()
    var cursor = bucketStart(first)
    val lastBucket = bucketStart(last)
    while (!cursor.isAfter(lastBucket)) {
        val values = totals[cursor].orEmpty()
        result += PeriodTrendPoint(
            startDate = maxOf(cursor, first),
            endDate = minOf(bucketEnd(cursor), last),
            value = values.sumOf { it.value.toDouble() }.toFloat(),
            activeDays = values.count { it.value > 0f },
            coveredDays = values.size
        )
        cursor = nextBucket(cursor)
    }
    return result
}

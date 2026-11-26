package com.rinzaki.babylog

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

internal data class PeriodTrendPoint(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val value: Float,
    val nonZeroDays: Int,
    val coveredDays: Int,
    val isComplete: Boolean = true,
    val observationCount: Int = 0
)

/**
 * Aggregates a daily series into calendar weeks or months. Missing dates between
 * the first and last observation are treated as zero-valued calendar days; partial
 * first/last periods are retained and no values are interpolated.
 *
 * Invalid negative or non-finite values are treated as zero so one malformed input
 * cannot poison an entire period total or its chart scale.
 */
internal fun aggregateTrendPeriods(
    dailyPoints: List<DailyTrendPoint>,
    period: String
): List<PeriodTrendPoint> {
    require(period == "周" || period == "月") { "period must be 周 or 月" }
    if (dailyPoints.isEmpty()) return emptyList()

    val dailyTotals = dailyPoints.groupBy { it.date }
        .mapValues { (_, values) ->
            values.sumOf { point ->
                point.value.takeIf { it.isFinite() && it >= 0f }?.toDouble() ?: 0.0
            }.toFloat()
        }
    val dailyObservations = dailyPoints.groupBy { it.date }
        .mapValues { (_, values) -> values.sumOf { it.observationCount.coerceAtLeast(0) } }
    val first = dailyTotals.keys.minOrNull() ?: return emptyList()
    val last = dailyTotals.keys.maxOrNull() ?: return emptyList()
    val completeDailySeries = generateSequence(first) { date ->
        if (date < last) date.plusDays(1) else null
    }.map { date -> DailyTrendPoint(date, dailyTotals[date] ?: 0f, dailyObservations[date] ?: 0) }.toList()

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

    val totals = completeDailySeries.groupBy { bucketStart(it.date) }
    val result = mutableListOf<PeriodTrendPoint>()
    var cursor = bucketStart(first)
    val lastBucket = bucketStart(last)
    while (!cursor.isAfter(lastBucket)) {
        val values = totals[cursor].orEmpty()
        result += PeriodTrendPoint(
            startDate = maxOf(cursor, first),
            endDate = minOf(bucketEnd(cursor), last),
            value = values.sumOf { it.value.toDouble() }.toFloat(),
            nonZeroDays = values.count { it.value > 0f },
            coveredDays = values.size,
            observationCount = values.sumOf { it.observationCount.coerceAtLeast(0) },
            // Completeness is based on the calendar bucket boundaries, not its length:
            // a full 30-day month and a full 31-day month are both complete.
            isComplete = first <= cursor && last >= bucketEnd(cursor)
        )
        cursor = nextBucket(cursor)
    }
    return result
}

internal data class PeriodTrendComparison(
    val change: Float,
    val nonZeroDaysChange: Int
)

/** Compares a selected calendar bucket with the immediately preceding bucket. */
internal fun periodTrendComparison(
    periods: List<PeriodTrendPoint>,
    selectedIndex: Int
): PeriodTrendComparison? {
    if (selectedIndex !in periods.indices || selectedIndex == 0) return null
    val previous = periods[selectedIndex - 1]
    val current = periods[selectedIndex]
    // Compare only complete calendar buckets. Their lengths may legitimately differ
    // (for example, February and March), so covered-day counts alone are insufficient.
    if (!current.isComplete || !previous.isComplete) return null
    val change = current.value - previous.value
    if (!change.isFinite()) return null
    return PeriodTrendComparison(change, current.nonZeroDays - previous.nonZeroDays)
}


/** Average daily value within the actual calendar dates represented by this period. */
internal fun periodTrendDailyAverage(point: PeriodTrendPoint): Float? {
    if (point.coveredDays <= 0 || !point.value.isFinite() || point.value < 0f) return null
    val average = point.value / point.coveredDays
    return average.takeIf { it.isFinite() && it >= 0f }
}

package com.rinzaki.babylog

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

internal data class GrowthMonthSummary(
    val month: YearMonth,
    val measurementCount: Int,
    val firstValue: Float,
    val latestValue: Float,
    val change: Float,
    val spanDays: Long
)

/**
 * Summarizes real measurements by calendar month. Months without measurements are omitted;
 * no interpolation or estimated values are introduced.
 */
internal fun growthMonthlySummaries(
    points: List<Pair<Long, Float>>,
    zone: ZoneId = ZoneId.systemDefault(),
    maxMonths: Int = 12
): List<GrowthMonthSummary> {
    require(maxMonths in 1..120) { "maxMonths must be between 1 and 120" }
    return points.asSequence()
        .filter { it.second.isFinite() && it.second > 0f }
        .sortedBy { it.first }
        .groupBy { Instant.ofEpochMilli(it.first).atZone(zone).toLocalDate().let(YearMonth::from) }
        .toSortedMap()
        .entries
        .toList()
        .takeLast(maxMonths)
        .map { (month, measurements) ->
            val first = measurements.first()
            val latest = measurements.last()
            val firstDate = Instant.ofEpochMilli(first.first).atZone(zone).toLocalDate()
            val latestDate = Instant.ofEpochMilli(latest.first).atZone(zone).toLocalDate()
            GrowthMonthSummary(
                month = month,
                measurementCount = measurements.size,
                firstValue = first.second,
                latestValue = latest.second,
                change = latest.second - first.second,
                spanDays = ChronoUnit.DAYS.between(firstDate, latestDate)
            )
        }
}

/** Describes the interval and observed change between two actual measurements. */
internal data class GrowthMeasurementInterval(
    val previousDate: LocalDate,
    val latestDate: LocalDate,
    val elapsedDays: Long,
    val change: Float,
    val changePer30Days: Float?
)

internal fun growthMeasurementInterval(
    points: List<Pair<Long, Float>>,
    zone: ZoneId = ZoneId.systemDefault()
): GrowthMeasurementInterval? {
    val valid = points.filter { it.second.isFinite() && it.second > 0f }.sortedBy { it.first }
    if (valid.size < 2) return null
    val previous = valid[valid.lastIndex - 1]
    val latest = valid.last()
    val previousDate = Instant.ofEpochMilli(previous.first).atZone(zone).toLocalDate()
    val latestDate = Instant.ofEpochMilli(latest.first).atZone(zone).toLocalDate()
    val elapsed = ChronoUnit.DAYS.between(previousDate, latestDate)
    return GrowthMeasurementInterval(
        previousDate = previousDate,
        latestDate = latestDate,
        elapsedDays = elapsed,
        change = latest.second - previous.second,
        changePer30Days = if (elapsed > 0) (latest.second - previous.second) * 30f / elapsed else null
    )
}

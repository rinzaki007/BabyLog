package com.rinzaki.babylog

import java.time.LocalDate
import java.time.ZoneId

/**
 * One official WS/T 423—2022 percentile row positioned on the baby's calendar timeline.
 * The date is the exact calendar-month anniversary of birth, not an assumed 30-day month.
 */
internal data class ChineseGrowthReferencePoint(
    val date: LocalDate,
    val timestamp: Long,
    val percentiles: GrowthPercentiles
)

/**
 * Maps the published age rows to the same date-based horizontal axis used by growth
 * measurements. Returns null when the profile sex has no matching official table.
 */
internal fun chineseGrowthReferenceTimeline(
    birthDate: LocalDate,
    metric: ChineseGrowthMetric,
    sex: String?,
    zone: ZoneId = ZoneId.systemDefault()
): List<ChineseGrowthReferencePoint>? {
    val rows = ChineseGrowthReferenceData.rows(metric, sex) ?: return null
    return rows.map { row ->
        val date = birthDate.plusMonths(row.ageMonths.toLong())
        ChineseGrowthReferencePoint(
            date = date,
            timestamp = date.atStartOfDay(zone).toInstant().toEpochMilli(),
            percentiles = row.percentiles
        )
    }
}

/**
 * Returns the official interpolated percentiles for a measurement date.
 * Dates outside the metric's published age coverage are intentionally not extrapolated.
 */
internal fun chineseGrowthReferenceAtDate(
    birthDate: LocalDate,
    measuredDate: LocalDate,
    metric: ChineseGrowthMetric,
    sex: String?
): GrowthPercentiles? {
    val ageMonths = chineseGrowthAgeMonths(birthDate, measuredDate, metric) ?: return null
    val rows = ChineseGrowthReferenceData.rows(metric, sex) ?: return null
    return interpolateGrowthStandard(rows, ageMonths)
}

package com.rinzaki.babylog

import java.time.LocalDate
import java.time.ZoneId

/**
 * Clips the official reference timeline to the observed measurement window.
 * Exact window endpoints are interpolated so short measurement windows still have
 * a meaningful reference segment even when no published monthly row falls inside.
 */
internal fun chineseGrowthReferenceChartWindow(
    birthDate: LocalDate,
    startDate: LocalDate,
    endDate: LocalDate,
    metric: ChineseGrowthMetric,
    sex: String?,
    zone: ZoneId = ZoneId.systemDefault()
): List<ChineseGrowthReferencePoint>? {
    if (endDate.isBefore(startDate)) return emptyList()
    val timeline = chineseGrowthReferenceTimeline(birthDate, metric, sex, zone) ?: return null
    val pointsByDate = timeline
        .asSequence()
        .filter { !it.date.isBefore(startDate) && !it.date.isAfter(endDate) }
        .associateBy { it.date }
        .toMutableMap()

    listOf(startDate, endDate).forEach { date ->
        if (date !in pointsByDate) {
            chineseGrowthReferenceAtDate(birthDate, date, metric, sex)?.let { percentiles ->
                pointsByDate[date] = ChineseGrowthReferencePoint(
                    date = date,
                    timestamp = date.atStartOfDay(zone).toInstant().toEpochMilli(),
                    percentiles = percentiles
                )
            }
        }
    }
    return pointsByDate.values.sortedBy { it.date }
}

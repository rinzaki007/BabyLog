package com.rinzaki.babylog

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Formats the baby's completed age on the date of a growth measurement.
 * Uses calendar months (not an average 30-day month) so birthday boundaries
 * remain consistent with the rest of the app.
 */
internal fun growthMeasurementAgeLabel(
    birthTime: Long,
    measuredAt: Long,
    zone: ZoneId = ZoneId.systemDefault()
): String {
    val birthDate = Instant.ofEpochMilli(birthTime).atZone(zone).toLocalDate()
    val measuredDate = Instant.ofEpochMilli(measuredAt).atZone(zone).toLocalDate()
    if (measuredDate.isBefore(birthDate)) return "出生前"

    val months = ChronoUnit.MONTHS.between(birthDate, measuredDate)
    val monthAnchor = birthDate.plusMonths(months)
    val days = ChronoUnit.DAYS.between(monthAnchor, measuredDate)

    return when {
        months == 0L && days == 0L -> "出生当天"
        months == 0L -> "${days}天"
        days == 0L -> "${months}个月"
        else -> "${months}个月${days}天"
    }
}

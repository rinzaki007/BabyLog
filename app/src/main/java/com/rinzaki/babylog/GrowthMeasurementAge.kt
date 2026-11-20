package com.rinzaki.babylog

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Counts completed calendar months, using the same month-end clamping as LocalDate.plusMonths.
 * Dates before birth are clamped to zero for age-stage filtering.
 */
internal fun growthCompletedMonths(birthDate: LocalDate, measuredDate: LocalDate): Long {
    if (measuredDate.isBefore(birthDate)) return 0L
    val calendarMonthDifference = (measuredDate.year - birthDate.year) * 12L +
        (measuredDate.monthValue - birthDate.monthValue)
    val candidateAnchor = birthDate.plusMonths(calendarMonthDifference)
    return if (measuredDate.isBefore(candidateAnchor)) {
        (calendarMonthDifference - 1).coerceAtLeast(0L)
    } else {
        calendarMonthDifference
    }
}

/**
 * Formats the baby's completed age on a measurement date.
 * Uses calendar months rather than an average 30-day month.
 */
internal fun growthMeasurementAgeLabel(
    birthTime: Long,
    measuredAt: Long,
    zone: ZoneId = ZoneId.systemDefault()
): String {
    val birthDate = Instant.ofEpochMilli(birthTime).atZone(zone).toLocalDate()
    val measuredDate = Instant.ofEpochMilli(measuredAt).atZone(zone).toLocalDate()
    if (measuredDate.isBefore(birthDate)) return "出生前"

    val months = growthCompletedMonths(birthDate, measuredDate)
    val monthAnchor = birthDate.plusMonths(months)
    val days = ChronoUnit.DAYS.between(monthAnchor, measuredDate)

    return when {
        months == 0L && days == 0L -> "出生当天"
        months == 0L -> "${days}天"
        days == 0L -> "${months}个月"
        else -> "${months}个月${days}天"
    }
}

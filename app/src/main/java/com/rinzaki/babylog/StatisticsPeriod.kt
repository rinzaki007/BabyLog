package com.rinzaki.babylog

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

internal data class StatisticsDateRange(
    val startDate: LocalDate?,
    val endDate: LocalDate?
) {
    val dayCount: Int?
        get() = if (startDate == null || endDate == null) null
        else (ChronoUnit.DAYS.between(startDate, endDate) + 1).toInt().coerceAtLeast(1)

    val label: String
        get() = when {
            startDate == null || endDate == null -> "全部记录"
            startDate == endDate -> startDate.toString()
            else -> "${startDate} 至 ${endDate}"
        }
}

internal fun statisticsDateRange(
    preset: String,
    today: LocalDate,
    customStart: LocalDate? = null,
    customEnd: LocalDate? = null
): StatisticsDateRange {
    val start = when (preset) {
        "近7天" -> today.minusDays(6)
        "近30天" -> today.minusDays(29)
        "近90天" -> today.minusDays(89)
        "本周" -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        "本月" -> today.withDayOfMonth(1)
        "本季度" -> today.withMonth(((today.monthValue - 1) / 3) * 3 + 1).withDayOfMonth(1)
        "自定义" -> customStart
        else -> null
    }
    val end = when (preset) {
        "自定义" -> customEnd
        "全部" -> null
        else -> today
    }
    if (start == null || end == null) return StatisticsDateRange(null, null)
    return if (start.isAfter(end)) StatisticsDateRange(end, start)
    else StatisticsDateRange(start, end)
}

internal fun localDateToPickerMillis(date: LocalDate): Long =
    date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun pickerMillisToLocalDate(millis: Long): LocalDate =
    java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate()

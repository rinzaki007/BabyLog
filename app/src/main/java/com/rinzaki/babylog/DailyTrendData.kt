package com.rinzaki.babylog

import com.rinzaki.babylog.data.BabyEventEntity
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

data class DailyTrendPoint(val date: LocalDate, val value: Float)

/**
 * Builds a complete daily series for the selected metric. Missing dates are kept as zero
 * so charts never silently skip days. Timestamps are interpreted in the supplied zone.
 */
fun dailyTrendPoints(
    events: List<BabyEventEntity>,
    metric: String,
    days: Int,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault()
): List<DailyTrendPoint> {
    require(days in 1..366) { "days must be between 1 and 366" }
    val firstDay = today.minusDays((days - 1).toLong())
    val startMillis = firstDay.atStartOfDay(zone).toInstant().toEpochMilli()
    val endMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val totals = mutableMapOf<LocalDate, Float>()

    events.asSequence()
        .filter { it.deletedAt == null && it.occurredAt >= startMillis && it.occurredAt < endMillis }
        .forEach { event ->
            val date = java.time.Instant.ofEpochMilli(event.occurredAt).atZone(zone).toLocalDate()
            val payload = runCatching { JSONObject(event.payloadJson) }.getOrNull() ?: JSONObject()
            val value = when (metric) {
                "喂养奶量" -> if (event.eventType == "FEEDING") payload.optInt("amountMl").coerceAtLeast(0).toFloat() else 0f
                "喂养次数" -> if (event.eventType == "FEEDING") 1f else 0f
                "补充次数" -> if (event.eventType == "SUPPLEMENT") 1f else 0f
                "小便次数" -> if (event.eventType == "DIAPER" && payload.optBoolean("urine")) 1f else 0f
                "大便次数" -> if (event.eventType == "DIAPER" && payload.optBoolean("stool")) 1f else 0f
                "睡眠小时" -> if (event.eventType == "SLEEP") payload.optInt("durationMinutes").coerceAtLeast(0) / 60f else 0f
                "记录总数" -> 1f
                else -> 0f
            }
            totals[date] = (totals[date] ?: 0f) + value
        }

    return (0 until days).map { offset ->
        val date = firstDay.plusDays(offset.toLong())
        DailyTrendPoint(date, totals[date] ?: 0f)
    }
}


/**
 * Counts calendar dates rather than elapsed 24-hour blocks. This keeps averages stable
 * across daylight-saving changes and local midnight boundaries.
 */
fun calendarDaysCovered(
    events: List<BabyEventEntity>,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault()
): Int {
    val firstDate = events.asSequence()
        .filter { it.deletedAt == null }
        .map { java.time.Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }
        .filter { !it.isAfter(today) }
        .minOrNull() ?: return 1
    return (java.time.temporal.ChronoUnit.DAYS.between(firstDate, today) + 1)
        .toInt()
        .coerceAtLeast(1)
}

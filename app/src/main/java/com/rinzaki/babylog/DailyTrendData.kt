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
                "辅食次数" -> if (event.eventType == "SOLID_FOOD") 1f else 0f
                "小便次数" -> if (event.eventType == "DIAPER" && payload.optBoolean("urine")) 1f else 0f
                "大便次数" -> if (event.eventType == "DIAPER" && payload.optBoolean("stool")) 1f else 0f
                "睡眠小时" -> if (event.eventType == "SLEEP") payload.optInt("durationMinutes").coerceAtLeast(0) / 60f else 0f
                "体温记录次数" -> if (event.eventType == "TEMPERATURE") 1f else 0f
                "里程碑次数" -> if (event.eventType == "MILESTONE") 1f else 0f
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
 * Resolves a tap to a daily bar only when it lands inside the chart's horizontal plot area.
 * Returning null for touches in the outer padding avoids accidentally selecting the first
 * or last day when a user taps beside the plotted bars.
 */
internal fun dailyTrendTappedIndex(
    tapX: Float,
    chartWidth: Float,
    horizontalPadding: Float,
    pointCount: Int
): Int? {
    if (!tapX.isFinite() || !chartWidth.isFinite() || !horizontalPadding.isFinite() ||
        chartWidth <= 0f || horizontalPadding < 0f ||
        chartWidth <= horizontalPadding * 2f || pointCount <= 0 ||
        tapX < horizontalPadding || tapX > chartWidth - horizontalPadding
    ) return null

    val plotWidth = chartWidth - horizontalPadding * 2f
    val slotWidth = plotWidth / pointCount
    if (!slotWidth.isFinite() || slotWidth <= 0f) return null
    return (((tapX - horizontalPadding) / slotWidth).toInt())
        .coerceIn(0, pointCount - 1)
}


/**
 * Keeps the all-time chart window anchored to the baby's complete history, even when
 * the displayed metric is filtered to a category whose first record is more recent.
 */
fun dailyTrendWindowDays(
    range: String,
    allEvents: List<BabyEventEntity>,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault()
): Int = when (range) {
    "近30天" -> 30
    "近90天" -> 90
    "全部" -> calendarDaysCovered(allEvents, today, zone).coerceAtMost(366)
    else -> 7
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

/**
 * Uses only days in which the baby could have generated records. For a finite range,
 * the window starts at the later of the range boundary and the birth date. When the
 * birth date is unavailable, the selected range (or first recorded day) remains the fallback.
 */
internal fun statisticsDaysCovered(
    events: List<BabyEventEntity>,
    rangeDays: Int?,
    birthTimeMillis: Long?,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault()
): Int {
    require(rangeDays == null || rangeDays in 1..3660) { "rangeDays must be between 1 and 3660" }
    val rangeStart = rangeDays?.let { today.minusDays((it - 1).toLong()) }
    val birthDate = birthTimeMillis
        ?.takeIf { it > 0L }
        ?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    val firstDate = when {
        birthDate != null && rangeStart != null -> maxOf(birthDate, rangeStart)
        birthDate != null -> birthDate
        rangeStart != null -> rangeStart
        else -> events.asSequence()
            .filter { it.deletedAt == null }
            .map { java.time.Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }
            .filter { !it.isAfter(today) }
            .minOrNull() ?: today
    }.coerceAtMost(today)
    return (java.time.temporal.ChronoUnit.DAYS.between(firstDate, today) + 1)
        .toInt()
        .coerceAtLeast(1)
}

/** Metrics shown by the daily chart follow the currently selected statistics category. */
fun trendMetricsForCategory(category: String): List<String> = when (category) {
    "喂养" -> listOf("喂养奶量", "喂养次数")
    "辅食" -> listOf("辅食次数")
    "补充" -> listOf("补充次数")
    "护理" -> listOf("小便次数", "大便次数")
    "睡眠" -> listOf("睡眠小时")
    "成长" -> listOf("记录总数")
    "体温" -> listOf("体温记录次数")
    "里程碑" -> listOf("里程碑次数")
    else -> listOf("喂养奶量", "喂养次数", "辅食次数", "补充次数", "小便次数", "大便次数", "睡眠小时", "体温记录次数", "里程碑次数", "记录总数")
}

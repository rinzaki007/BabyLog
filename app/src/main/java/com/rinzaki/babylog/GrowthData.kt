package com.rinzaki.babylog

import com.rinzaki.babylog.data.BabyEntity
import com.rinzaki.babylog.data.BabyEventEntity
import org.json.JSONObject

/**
 * Returns chart-ready measurements in chronological order.
 * Birth measurements are included as the first data point when available.
 * Future-dated records are excluded because they cannot represent an observed measurement yet.
 */
internal fun growthMetricPoints(
    baby: BabyEntity,
    events: List<BabyEventEntity>,
    metricKey: String,
    asOfMillis: Long = System.currentTimeMillis()
): List<Pair<Long, Float>> {
    require(metricKey in setOf("weightGrams", "lengthCm", "headCircumferenceCm")) {
        "Unsupported growth metric: $metricKey"
    }

    val birthValue = when (metricKey) {
        "weightGrams" -> baby.birthWeightGrams?.toDouble()?.div(1000.0)
        "lengthCm" -> baby.birthLengthCm
        else -> baby.birthHeadCircumferenceCm
    }

    val validRange = when (metricKey) {
        "weightGrams" -> 0.1..80.0
        "lengthCm" -> 20.0..120.0
        else -> 20.0..70.0
    }

    return buildList {
        if (baby.birthTime <= asOfMillis) {
            birthValue?.takeIf { it.isFinite() && it in validRange }
                ?.toFloat()
                ?.takeIf { it.isFinite() && it > 0f }
                ?.let { add(baby.birthTime to it) }
        }
        events.asSequence()
            .filter {
                it.babyId == baby.id &&
                    it.deletedAt == null &&
                    it.eventType == "GROWTH" &&
                    // Growth observations cannot precede the baby's recorded birth date.
                    it.occurredAt >= baby.birthTime &&
                    it.occurredAt <= asOfMillis
            }
            .forEach { event ->
                val value = runCatching {
                    JSONObject(event.payloadJson).optDouble(metricKey).takeIf { it.isFinite() && it > 0.0 }
                }.getOrNull()
                if (value != null) {
                    val chartValue = if (metricKey == "weightGrams") value / 1000.0 else value
                    val floatValue = chartValue.toFloat()
                    if (floatValue.isFinite() && floatValue.toDouble() in validRange) {
                        add(event.occurredAt to floatValue)
                    }
                }
            }
    }.sortedBy { it.first }
}

/**
 * Finds the nearest plotted measurement in two dimensions. This matters when multiple
 * observations share a timestamp: their x-coordinates overlap, but distinct values have
 * distinct y-coordinates and should remain individually selectable.
 */
internal fun growthChartNearestPointIndex(
    points: List<Pair<Long, Float>>,
    tapX: Float,
    tapY: Float,
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    minValue: Float,
    maxValue: Float,
    maxDistancePx: Float = Float.POSITIVE_INFINITY
): Int? {
    if (points.isEmpty() ||
        !tapX.isFinite() || !tapY.isFinite() ||
        !left.isFinite() || !right.isFinite() || !top.isFinite() || !bottom.isFinite() ||
        !minValue.isFinite() || !maxValue.isFinite() ||
        right <= left || bottom <= top || maxValue <= minValue ||
        maxDistancePx.isNaN() || maxDistancePx < 0f
    ) return null

    val valueSpan = maxValue - minValue
    // Finite endpoints can still produce an infinite span (for example, -MAX to MAX).
    if (!valueSpan.isFinite() || valueSpan <= 0f) return null

    val firstTimestamp = points.first().first
    val lastTimestamp = points.last().first
    fun squaredDistance(index: Int): Double? {
        val point = points[index]
        if (!point.second.isFinite()) return null
        val fraction = growthChartTimeFraction(point.first, firstTimestamp, lastTimestamp, points.size)
        val x = left + (right - left) * fraction
        val y = bottom - (point.second - minValue) / valueSpan * (bottom - top)
        if (!x.isFinite() || !y.isFinite()) return null
        val dx = (x - tapX).toDouble()
        val dy = (y - tapY).toDouble()
        return (dx * dx + dy * dy).takeIf { it.isFinite() }
    }

    val nearest = points.indices
        .mapNotNull { index -> squaredDistance(index)?.let { index to it } }
        .minByOrNull { it.second }
        ?: return null
    val maxDistanceSquared = maxDistancePx.toDouble() * maxDistancePx.toDouble()
    return nearest.first.takeIf { nearest.second <= maxDistanceSquared }
}

/**
 * Maps a measurement timestamp to the chart's horizontal fraction.
 * A single point (or a set of same-timestamp points) is centered instead of pinned to an edge.
 */
internal fun growthChartTimeFraction(
    timestamp: Long,
    firstTimestamp: Long,
    lastTimestamp: Long,
    pointCount: Int
): Float {
    require(pointCount > 0) { "pointCount must be positive" }
    if (pointCount == 1 || lastTimestamp <= firstTimestamp) return 0.5f
    return ((timestamp - firstTimestamp).toDouble() / (lastTimestamp - firstTimestamp).toDouble())
        .toFloat()
        .coerceIn(0f, 1f)
}

/** Comparison with the immediately preceding actual observation in the selected series. */
internal data class GrowthMeasurementComparison(
    val elapsedDays: Long,
    val change: Float
)

internal fun growthMeasurementComparison(
    points: List<Pair<Long, Float>>,
    selectedIndex: Int,
    zone: java.time.ZoneId = java.time.ZoneId.systemDefault()
): GrowthMeasurementComparison? {
    if (selectedIndex !in points.indices || selectedIndex == 0) return null
    val previous = points[selectedIndex - 1]
    val current = points[selectedIndex]
    if (!previous.second.isFinite() || !current.second.isFinite()) return null
    val previousDate = java.time.Instant.ofEpochMilli(previous.first).atZone(zone).toLocalDate()
    val currentDate = java.time.Instant.ofEpochMilli(current.first).atZone(zone).toLocalDate()
    if (currentDate.isBefore(previousDate)) return null
    val elapsedDays = java.time.temporal.ChronoUnit.DAYS.between(previousDate, currentDate)
    val change = current.second - previous.second
    return GrowthMeasurementComparison(elapsedDays, change).takeIf { change.isFinite() }
}

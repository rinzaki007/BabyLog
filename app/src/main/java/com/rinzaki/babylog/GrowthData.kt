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

    return buildList {
        if (baby.birthTime <= asOfMillis) {
            birthValue?.takeIf { it.isFinite() && it > 0.0 }?.let {
                add(baby.birthTime to it.toFloat())
            }
        }
        events.asSequence()
            .filter {
                it.babyId == baby.id &&
                    it.deletedAt == null &&
                    it.eventType == "GROWTH" &&
                    it.occurredAt <= asOfMillis
            }
            .forEach { event ->
                val value = runCatching {
                    JSONObject(event.payloadJson).optDouble(metricKey).takeIf { it.isFinite() && it > 0.0 }
                }.getOrNull()
                if (value != null) {
                    val chartValue = if (metricKey == "weightGrams") value / 1000.0 else value
                    add(event.occurredAt to chartValue.toFloat())
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
    if (points.isEmpty() || right <= left || bottom <= top || maxValue <= minValue ||
        maxDistancePx.isNaN() || maxDistancePx < 0f
    ) return null
    val firstTimestamp = points.first().first
    val lastTimestamp = points.last().first
    val valueSpan = maxValue - minValue
    val nearest = points.indices.minByOrNull { index ->
        val point = points[index]
        val fraction = growthChartTimeFraction(point.first, firstTimestamp, lastTimestamp, points.size)
        val x = left + (right - left) * fraction
        val y = bottom - (point.second - minValue) / valueSpan * (bottom - top)
        val dx = x - tapX
        val dy = y - tapY
        dx * dx + dy * dy
    } ?: return null
    val point = points[nearest]
    val fraction = growthChartTimeFraction(point.first, firstTimestamp, lastTimestamp, points.size)
    val x = left + (right - left) * fraction
    val y = bottom - (point.second - minValue) / valueSpan * (bottom - top)
    val dx = x - tapX
    val dy = y - tapY
    return nearest.takeIf { dx * dx + dy * dy <= maxDistancePx * maxDistancePx }
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

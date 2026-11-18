package com.rinzaki.babylog

import com.rinzaki.babylog.data.BabyEntity
import com.rinzaki.babylog.data.BabyEventEntity
import org.json.JSONObject

/**
 * Returns chart-ready measurements in chronological order.
 * Birth measurements are included as the first data point when available.
 */
internal fun growthMetricPoints(
    baby: BabyEntity,
    events: List<BabyEventEntity>,
    metricKey: String
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
        birthValue?.takeIf { it.isFinite() && it > 0.0 }?.let {
            add(baby.birthTime to it.toFloat())
        }
        events.asSequence()
            .filter { it.eventType == "GROWTH" }
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

internal data class GrowthRateSummary(
    val intervalDays: Int,
    val changePer30Days: Float
)

/** Calculates a descriptive rate from the two latest measurements, without clinical interpretation. */
internal fun growthRateSummary(points: List<Pair<Long, Float>>): GrowthRateSummary? {
    if (points.size < 2) return null
    val ordered = points.sortedBy { it.first }
    val previous = ordered[ordered.lastIndex - 1]
    val latest = ordered.last()
    val elapsedMillis = latest.first - previous.first
    if (elapsedMillis <= 0L) return null
    val elapsedDays = elapsedMillis.toDouble() / 86_400_000.0
    val rate = (latest.second - previous.second) * 30.0 / elapsedDays
    if (!rate.isFinite()) return null
    return GrowthRateSummary(elapsedDays.toInt().coerceAtLeast(1), rate.toFloat())
}

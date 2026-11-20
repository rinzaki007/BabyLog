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

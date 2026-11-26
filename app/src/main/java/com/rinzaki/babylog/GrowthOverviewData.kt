package com.rinzaki.babylog

import com.rinzaki.babylog.data.BabyEntity
import com.rinzaki.babylog.data.BabyEventEntity
import java.time.Instant
import java.time.ZoneId

/**
 * A compact snapshot of the newest real observation for one growth metric.
 * Percentile information is descriptive context from WS/T 423—2022, never a diagnosis.
 */
internal data class GrowthMetricOverview(
    val measuredAt: Long?,
    val value: Float?,
    val comparison: GrowthMeasurementComparison?,
    val percentileBand: String?,
    val referenceHint: String?,
    /** Latest actual measurements, oldest to newest, for a compact independent sparkline. */
    val recentValues: List<Float>
)

internal fun growthMetricOverview(
    baby: BabyEntity,
    events: List<BabyEventEntity>,
    metricKey: String,
    asOfMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): GrowthMetricOverview {
    val points = growthMetricPoints(baby, events, metricKey, asOfMillis)
    val latestIndex = points.lastIndex
    val latest = points.getOrNull(latestIndex)
        ?: return GrowthMetricOverview(null, null, null, null, null, emptyList())

    val standardMetric = when (metricKey) {
        "weightGrams" -> ChineseGrowthMetric.WEIGHT_FOR_AGE
        "lengthCm" -> ChineseGrowthMetric.LENGTH_HEIGHT_FOR_AGE
        "headCircumferenceCm" -> ChineseGrowthMetric.HEAD_CIRCUMFERENCE_FOR_AGE
        else -> error("Unsupported growth metric: $metricKey")
    }
    val measuredDate = Instant.ofEpochMilli(latest.first).atZone(zone).toLocalDate()
    val birthDate = Instant.ofEpochMilli(baby.birthTime).atZone(zone).toLocalDate()
    val sex = chineseGrowthStandardSex(baby.sex)
    val assessment = chineseGrowthReferenceAtDate(
        birthDate, measuredDate, standardMetric, sex
    )?.let { assessChineseGrowthPercentile(latest.second, it) }

    return GrowthMetricOverview(
        measuredAt = latest.first,
        value = latest.second,
        comparison = growthMeasurementComparison(points, latestIndex, zone),
        percentileBand = assessment?.bandLabel,
        referenceHint = if (assessment == null) {
            growthReferenceUnavailableHint(birthDate, measuredDate, standardMetric, sex)
        } else {
            null
        },
        recentValues = points.takeLast(5).map { it.second }
    )
}

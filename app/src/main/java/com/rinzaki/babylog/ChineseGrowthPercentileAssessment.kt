package com.rinzaki.babylog

/**
 * A compact, non-diagnostic comparison of one observed measurement with WS/T 423—2022.
 */
internal data class ChineseGrowthPercentileAssessment(
    val band: GrowthPercentileBand,
    val bandLabel: String,
    val medianValue: Float
)

internal fun assessChineseGrowthPercentile(
    measuredValue: Float,
    reference: GrowthPercentiles
): ChineseGrowthPercentileAssessment? {
    val band = classifyGrowthPercentile(measuredValue, reference) ?: return null
    val label = when (band) {
        GrowthPercentileBand.BELOW_P3 -> "低于 P3"
        GrowthPercentileBand.P3_TO_P25 -> "P3–P25"
        GrowthPercentileBand.P25_TO_P75 -> "P25–P75"
        GrowthPercentileBand.P75_TO_P97 -> "P75–P97"
        GrowthPercentileBand.AT_OR_ABOVE_P97 -> "达到或高于 P97"
    }
    return ChineseGrowthPercentileAssessment(
        band = band,
        bandLabel = label,
        medianValue = reference.p50
    )
}

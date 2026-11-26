package com.rinzaki.babylog

/**
 * One actual measurement positioned in its official percentile band.
 * visibleIndex links the point to the selected measurement in the value chart.
 */
internal data class GrowthPercentileTrendPoint(
    val visibleIndex: Int,
    val sourceIndex: Int,
    val timestamp: Long,
    val band: GrowthPercentileBand,
    val bandLabel: String
)

/**
 * Builds a sparse history from real observations with an available WS/T 423—2022
 * comparison. Missing references stay absent; no values are interpolated between
 * measurements and no line is implied between percentile bands.
 */
internal fun growthPercentileTrendPoints(
    visiblePoints: List<Pair<Long, Float>>,
    sourceIndices: List<Int>,
    allAssessments: List<ChineseGrowthPercentileAssessment?>
): List<GrowthPercentileTrendPoint> = visiblePoints.mapIndexedNotNull { visibleIndex, point ->
    val sourceIndex = sourceIndices.getOrNull(visibleIndex) ?: return@mapIndexedNotNull null
    val assessment = allAssessments.getOrNull(sourceIndex) ?: return@mapIndexedNotNull null
    GrowthPercentileTrendPoint(
        visibleIndex = visibleIndex,
        sourceIndex = sourceIndex,
        timestamp = point.first,
        band = assessment.band,
        bandLabel = assessment.bandLabel
    )
}

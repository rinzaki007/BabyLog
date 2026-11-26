package com.rinzaki.babylog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GrowthPercentileTrendTest {
    private val reference = GrowthPercentiles(
        p3 = 2f, p10 = 2.5f, p25 = 3f, p50 = 4f,
        p75 = 5f, p90 = 5.5f, p97 = 6f
    )

    @Test
    fun trendContainsOnlyMeasurementsWithAvailableOfficialReferences() {
        val assessments = listOf(
            assessChineseGrowthPercentile(2.2f, reference),
            null,
            assessChineseGrowthPercentile(4.5f, reference),
            assessChineseGrowthPercentile(6.2f, reference)
        )
        val points = listOf(
            1_000L to 2.2f,
            2_000L to 3.0f,
            3_000L to 4.5f,
            4_000L to 6.2f
        )

        val trend = growthPercentileTrendPoints(
            visiblePoints = points,
            sourceIndices = listOf(0, 1, 2, 3),
            allAssessments = assessments
        )

        assertEquals(listOf(0, 2, 3), trend.map { it.visibleIndex })
        assertEquals(
            listOf(
                GrowthPercentileBand.P3_TO_P25,
                GrowthPercentileBand.P25_TO_P75,
                GrowthPercentileBand.AT_OR_ABOVE_P97
            ),
            trend.map { it.band }
        )
    }

    @Test
    fun trendUsesOriginalIndicesAfterDateRangeFiltering() {
        val assessments = listOf(
            assessChineseGrowthPercentile(2.2f, reference),
            assessChineseGrowthPercentile(3.5f, reference),
            assessChineseGrowthPercentile(5.2f, reference)
        )
        val trend = growthPercentileTrendPoints(
            visiblePoints = listOf(2_000L to 3.5f, 3_000L to 5.2f),
            sourceIndices = listOf(1, 2),
            allAssessments = assessments
        )

        assertEquals(listOf(1, 2), trend.map { it.sourceIndex })
        assertEquals(
            listOf(GrowthPercentileBand.P25_TO_P75, GrowthPercentileBand.P75_TO_P97),
            trend.map { it.band }
        )
    }

    @Test
    fun trendSkipsInvalidSourceIndicesAndDoesNotInventAssessment() {
        val trend = growthPercentileTrendPoints(
            visiblePoints = listOf(1_000L to 3f, 2_000L to 4f),
            sourceIndices = listOf(0, 8),
            allAssessments = listOf(null)
        )

        assertEquals(emptyList<GrowthPercentileTrendPoint>(), trend)
        assertNull(assessChineseGrowthPercentile(Float.NaN, reference))
    }
}

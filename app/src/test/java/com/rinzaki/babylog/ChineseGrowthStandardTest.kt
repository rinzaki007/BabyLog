package com.rinzaki.babylog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ChineseGrowthStandardTest {
    private val birth = GrowthPercentiles(2f, 3f, 4f, 5f, 6f, 7f, 8f)
    private val oneMonth = GrowthPercentiles(4f, 5f, 6f, 7f, 8f, 9f, 10f)
    private val fourMonths = GrowthPercentiles(7f, 8f, 9f, 10f, 11f, 12f, 13f)

    @Test
    fun returnsExactPublishedRowWithoutChangingValues() {
        val rows = listOf(
            GrowthStandardRow(0, birth),
            GrowthStandardRow(1, oneMonth),
            GrowthStandardRow(4, fourMonths)
        )

        assertEquals(oneMonth, interpolateGrowthStandard(rows, 1.0))
    }

    @Test
    fun interpolatesEveryPercentileAcrossNonUniformSourceIntervals() {
        val rows = listOf(
            GrowthStandardRow(0, birth),
            GrowthStandardRow(1, oneMonth),
            GrowthStandardRow(4, fourMonths)
        )

        // Halfway between month 1 and month 4, not a fixed monthly-step assumption.
        assertEquals(
            GrowthPercentiles(5.5f, 6.5f, 7.5f, 8.5f, 9.5f, 10.5f, 11.5f),
            interpolateGrowthStandard(rows, 2.5)
        )
    }

    @Test
    fun doesNotExtrapolateOutsidePublishedAgeCoverage() {
        val rows = listOf(GrowthStandardRow(1, oneMonth), GrowthStandardRow(4, fourMonths))

        assertNull(interpolateGrowthStandard(rows, 0.99))
        assertNull(interpolateGrowthStandard(rows, 4.01))
        assertNull(interpolateGrowthStandard(emptyList(), 2.0))
        assertNull(interpolateGrowthStandard(rows, Double.NaN))
    }

    @Test
    fun rejectsDuplicateOrUnorderedSourceAges() {
        val duplicate = listOf(
            GrowthStandardRow(0, birth),
            GrowthStandardRow(1, oneMonth),
            GrowthStandardRow(1, fourMonths)
        )

        assertNull(interpolateGrowthStandard(duplicate, 0.5))
    }

    @Test
    fun rejectsInvalidPercentileRowsInsteadOfDrawingCorruptReferenceData() {
        val nonFinite = birth.copy(p50 = Float.NaN)
        val descending = birth.copy(p25 = 2.5f)

        assertNull(interpolateGrowthStandard(listOf(GrowthStandardRow(0, nonFinite)), 0.0))
        assertNull(interpolateGrowthStandard(listOf(GrowthStandardRow(0, descending)), 0.0))
        assertNull(interpolateGrowthStandard(listOf(GrowthStandardRow(-1, birth)), 0.0))
    }

    @Test
    fun classifiesValuesUsingTheExactOfficialPercentileBoundaries() {
        val reference = GrowthPercentiles(3f, 4f, 5f, 6f, 7f, 8f, 9f)

        assertEquals(GrowthPercentileBand.BELOW_P3, classifyGrowthPercentile(2.99f, reference))
        assertEquals(GrowthPercentileBand.P3_TO_P25, classifyGrowthPercentile(3f, reference))
        assertEquals(GrowthPercentileBand.P3_TO_P25, classifyGrowthPercentile(4.99f, reference))
        assertEquals(GrowthPercentileBand.P25_TO_P75, classifyGrowthPercentile(5f, reference))
        assertEquals(GrowthPercentileBand.P25_TO_P75, classifyGrowthPercentile(6.99f, reference))
        assertEquals(GrowthPercentileBand.P75_TO_P97, classifyGrowthPercentile(7f, reference))
        assertEquals(GrowthPercentileBand.P75_TO_P97, classifyGrowthPercentile(8.99f, reference))
        assertEquals(GrowthPercentileBand.AT_OR_ABOVE_P97, classifyGrowthPercentile(9f, reference))
        assertNull(classifyGrowthPercentile(Float.NaN, reference))
        assertNull(classifyGrowthPercentile(5f, reference.copy(p50 = Float.POSITIVE_INFINITY)))
    }

    @Test
    fun mapsOnlyKnownProfileSexLabels() {
        assertEquals("BOY", chineseGrowthStandardSex("男宝"))
        assertEquals("GIRL", chineseGrowthStandardSex("女宝"))
        assertNull(chineseGrowthStandardSex(null))
        assertNull(chineseGrowthStandardSex("未设置"))
    }

    @Test
    fun calculatesFractionalCalendarAgeAcrossMonthEndsAndLeapDay() {
        val birthDate = LocalDate.of(2024, 1, 31)

        assertEquals(1.0, chineseGrowthAgeMonths(
            birthDate, LocalDate.of(2024, 2, 29), ChineseGrowthMetric.WEIGHT_FOR_AGE
        )!!, 0.0001)
        assertEquals(1.5, chineseGrowthAgeMonths(
            birthDate, LocalDate.of(2024, 3, 15), ChineseGrowthMetric.WEIGHT_FOR_AGE
        )!!, 0.04)
    }

    @Test
    fun loadsCompleteOfficialWeightForAgeTablesForBothSexes() {
        val boys = ChineseGrowthStandardData.rows(ChineseGrowthMetric.WEIGHT_FOR_AGE, "BOY")!!
        val girls = ChineseGrowthStandardData.rows(ChineseGrowthMetric.WEIGHT_FOR_AGE, "GIRL")!!

        assertEquals(44, boys.size)
        assertEquals(44, girls.size)
        assertEquals((0..24).toList() + (27..81 step 3).toList(), boys.map { it.ageMonths })
        assertEquals(boys.map { it.ageMonths }, girls.map { it.ageMonths })
        assertEquals(GrowthPercentiles(2.8f, 3.0f, 3.2f, 3.5f, 3.7f, 4.0f, 4.2f), boys.first().percentiles)
        assertEquals(GrowthPercentiles(2.7f, 2.9f, 3.1f, 3.3f, 3.6f, 3.8f, 4.1f), girls.first().percentiles)
        assertEquals(23.4f, boys.last().percentiles.p50)
        assertEquals(22.4f, girls.last().percentiles.p50)
        assertNull(ChineseGrowthStandardData.rows(ChineseGrowthMetric.WEIGHT_FOR_AGE, "UNKNOWN"))
        assertNull(ChineseGrowthStandardData.rows(ChineseGrowthMetric.HEAD_CIRCUMFERENCE_FOR_AGE, "BOY"))
    }

    @Test
    fun officialWeightRowsFeedTheSharedInterpolationEngine() {
        val boys = ChineseGrowthStandardData.rows(ChineseGrowthMetric.WEIGHT_FOR_AGE, "BOY")!!

        assertEquals(
            boys[24].percentiles.interpolate(boys[25].percentiles, 0.5f),
            interpolateGrowthStandard(boys, 25.5)
        )
        assertEquals(boys.last().percentiles, interpolateGrowthStandard(boys, 81.0))
        assertNull(interpolateGrowthStandard(boys, 81.01))
    }

    @Test
    fun loadsCompleteOfficialLengthHeightTablesForBothSexes() {
        val boys = ChineseGrowthHeightData.rows("BOY")!!
        val girls = ChineseGrowthHeightData.rows("GIRL")!!
        val expectedAges = (0..24).toList() + (27..81 step 3).toList()

        assertEquals(44, boys.size)
        assertEquals(44, girls.size)
        assertEquals(expectedAges, boys.map { it.ageMonths })
        assertEquals(expectedAges, girls.map { it.ageMonths })
        assertEquals(GrowthPercentiles(47.6f, 48.7f, 49.9f, 51.2f, 52.5f, 53.6f, 54.8f), boys.first().percentiles)
        assertEquals(GrowthPercentiles(46.8f, 47.9f, 49.1f, 50.3f, 51.6f, 52.7f, 53.8f), girls.first().percentiles)
        assertEquals(123.5f, boys.last().percentiles.p50)
        assertEquals(122.1f, girls.last().percentiles.p50)
        assertNull(ChineseGrowthHeightData.rows("UNKNOWN"))
    }

    @Test
    fun officialLengthHeightRowsUseTheSharedInterpolationEngineWithoutExtrapolation() {
        val girls = ChineseGrowthHeightData.rows("GIRL")!!
        assertEquals(
            girls[24].percentiles.interpolate(girls[25].percentiles, 0.5f),
            interpolateGrowthStandard(girls, 25.5)
        )
        assertEquals(girls.last().percentiles, interpolateGrowthStandard(girls, 81.0))
        assertNull(interpolateGrowthStandard(girls, 81.01))
    }

    @Test
    fun enforcesMetricSpecificAgeLimitsAndRejectsDatesBeforeBirth() {
        val birthDate = LocalDate.of(2020, 1, 1)

        assertNull(chineseGrowthAgeMonths(
            birthDate, birthDate.minusDays(1), ChineseGrowthMetric.WEIGHT_FOR_AGE
        ))
        assertEquals(35.0, chineseGrowthAgeMonths(
            birthDate, birthDate.plusMonths(35), ChineseGrowthMetric.HEAD_CIRCUMFERENCE_FOR_AGE
        )!!, 0.0001)
        assertNull(chineseGrowthAgeMonths(
            birthDate, birthDate.plusMonths(36), ChineseGrowthMetric.HEAD_CIRCUMFERENCE_FOR_AGE
        ))
        assertNull(chineseGrowthAgeMonths(
            birthDate, birthDate.plusMonths(84), ChineseGrowthMetric.WEIGHT_FOR_AGE
        ))
    }

    @Test
    fun loadsCompleteOfficialHeadCircumferenceTablesForBothSexes() {
        val boys = ChineseGrowthHeadCircumferenceData.rows("BOY")!!
        val girls = ChineseGrowthHeadCircumferenceData.rows("GIRL")!!
        val expectedAges = (0..24).toList() + listOf(27, 30, 33, 36)

        assertEquals(29, boys.size)
        assertEquals(29, girls.size)
        assertEquals(expectedAges, boys.map { it.ageMonths })
        assertEquals(expectedAges, girls.map { it.ageMonths })
        assertEquals(
            GrowthPercentiles(31.9f, 32.7f, 33.4f, 34.3f, 35.2f, 36.0f, 36.8f),
            boys.first().percentiles
        )
        assertEquals(
            GrowthPercentiles(31.5f, 32.3f, 33.1f, 33.9f, 34.8f, 35.6f, 36.3f),
            girls.first().percentiles
        )
        assertEquals(49.3f, boys.last().percentiles.p50)
        assertEquals(48.5f, girls.last().percentiles.p50)
        assertNull(ChineseGrowthHeadCircumferenceData.rows("UNKNOWN"))
    }

    @Test
    fun officialHeadCircumferenceRowsUseSharedInterpolationWithoutExtrapolation() {
        val boys = ChineseGrowthHeadCircumferenceData.rows("BOY")!!
        val girls = ChineseGrowthHeadCircumferenceData.rows("GIRL")!!

        assertEquals(
            boys[24].percentiles.interpolate(boys[25].percentiles, 0.5f),
            interpolateGrowthStandard(boys, 25.5)
        )
        assertEquals(boys.last().percentiles, interpolateGrowthStandard(boys, 36.0))
        assertNull(interpolateGrowthStandard(boys, 36.01))
        assertEquals(girls.last().percentiles, interpolateGrowthStandard(girls, 36.0))
        assertNull(interpolateGrowthStandard(girls, 36.01))
    }

    @Test
    fun unifiedReferenceAccessorDispatchesAllOfficialMetricsForBothSexes() {
        val metrics = listOf(
            ChineseGrowthMetric.WEIGHT_FOR_AGE,
            ChineseGrowthMetric.LENGTH_HEIGHT_FOR_AGE,
            ChineseGrowthMetric.HEAD_CIRCUMFERENCE_FOR_AGE
        )

        metrics.forEach { metric ->
            val boys = ChineseGrowthReferenceData.rows(metric, "BOY")!!
            val girls = ChineseGrowthReferenceData.rows(metric, "GIRL")!!
            val expectedLastAge = if (metric == ChineseGrowthMetric.HEAD_CIRCUMFERENCE_FOR_AGE) 36 else 81
            assertEquals(expectedLastAge, boys.last().ageMonths)
            assertEquals(expectedLastAge, girls.last().ageMonths)
            assertEquals(boys.map { it.ageMonths }, girls.map { it.ageMonths })
            assertEquals(44.takeIf { metric != ChineseGrowthMetric.HEAD_CIRCUMFERENCE_FOR_AGE } ?: 29, boys.size)
            assertEquals(boys.size, girls.size)
        }

        assertNull(ChineseGrowthReferenceData.rows(ChineseGrowthMetric.WEIGHT_FOR_AGE, null))
        assertNull(ChineseGrowthReferenceData.rows(ChineseGrowthMetric.LENGTH_HEIGHT_FOR_AGE, "UNKNOWN"))
    }

    @Test
    fun mapsOfficialReferenceRowsToCalendarMonthAnniversariesOnTheMeasurementTimeline() {
        val birthDate = LocalDate.of(2024, 1, 31)
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        val timeline = chineseGrowthReferenceTimeline(
            birthDate, ChineseGrowthMetric.WEIGHT_FOR_AGE, "BOY", zone
        )!!

        assertEquals(44, timeline.size)
        assertEquals(birthDate, timeline.first().date)
        assertEquals(LocalDate.of(2024, 2, 29), timeline[1].date)
        assertEquals(LocalDate.of(2024, 3, 31), timeline[2].date)
        assertEquals(birthDate.plusMonths(81), timeline.last().date)
        assertEquals(
            timeline[1].date.atStartOfDay(zone).toInstant().toEpochMilli(),
            timeline[1].timestamp
        )
        assertEquals(
            ChineseGrowthStandardData.rows(ChineseGrowthMetric.WEIGHT_FOR_AGE, "BOY")!![1].percentiles,
            timeline[1].percentiles
        )
    }

    @Test
    fun headCircumferenceTimelineStopsAtThirtySixMonthsAndUnknownSexHasNoTimeline() {
        val birthDate = LocalDate.of(2024, 5, 10)
        val timeline = chineseGrowthReferenceTimeline(
            birthDate, ChineseGrowthMetric.HEAD_CIRCUMFERENCE_FOR_AGE, "GIRL"
        )!!

        assertEquals(29, timeline.size)
        assertEquals(birthDate.plusMonths(36), timeline.last().date)
        assertNull(chineseGrowthReferenceTimeline(
            birthDate, ChineseGrowthMetric.WEIGHT_FOR_AGE, null
        ))
    }

    @Test
    fun interpolatesOfficialReferenceAtMeasurementDateAndDoesNotExtrapolate() {
        val birthDate = LocalDate.of(2024, 1, 31)
        val rows = ChineseGrowthReferenceData.rows(ChineseGrowthMetric.WEIGHT_FOR_AGE, "BOY")!!
        val expected = rows[0].percentiles.interpolate(rows[1].percentiles, 14f / 29f)

        assertEquals(
            expected,
            chineseGrowthReferenceAtDate(
                birthDate, LocalDate.of(2024, 2, 14),
                ChineseGrowthMetric.WEIGHT_FOR_AGE, "BOY"
            )
        )
        assertNull(chineseGrowthReferenceAtDate(
            birthDate, birthDate.minusDays(1), ChineseGrowthMetric.WEIGHT_FOR_AGE, "BOY"
        ))
        assertNull(chineseGrowthReferenceAtDate(
            birthDate, birthDate.plusMonths(36), ChineseGrowthMetric.HEAD_CIRCUMFERENCE_FOR_AGE, "GIRL"
        ))
        assertNull(chineseGrowthReferenceAtDate(
            birthDate, birthDate.plusMonths(4), ChineseGrowthMetric.WEIGHT_FOR_AGE, "UNKNOWN"
        ))
    }


    @Test
    fun clipsOfficialReferenceToMeasurementWindowAndInterpolatesWindowEndpoints() {
        val birthDate = LocalDate.of(2024, 1, 31)
        val startDate = LocalDate.of(2024, 2, 10)
        val endDate = LocalDate.of(2024, 3, 10)
        val window = chineseGrowthReferenceChartWindow(
            birthDate, startDate, endDate,
            ChineseGrowthMetric.WEIGHT_FOR_AGE, "BOY",
            java.time.ZoneId.of("Asia/Shanghai")
        )!!

        assertEquals(startDate, window.first().date)
        assertEquals(endDate, window.last().date)
        assertEquals(startDate.atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli(), window.first().timestamp)
        assertEquals(endDate.atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli(), window.last().timestamp)
        assertEquals(
            chineseGrowthReferenceAtDate(birthDate, startDate, ChineseGrowthMetric.WEIGHT_FOR_AGE, "BOY"),
            window.first().percentiles
        )
        assertEquals(
            chineseGrowthReferenceAtDate(birthDate, endDate, ChineseGrowthMetric.WEIGHT_FOR_AGE, "BOY"),
            window.last().percentiles
        )
        assertTrue(window.zipWithNext().all { (left, right) -> left.date.isBefore(right.date) })
    }

    @Test
    fun chartWindowRespectsMetricAgeLimitAndUnknownSex() {
        val birthDate = LocalDate.of(2024, 1, 1)
        val headWindow = chineseGrowthReferenceChartWindow(
            birthDate, birthDate.plusMonths(35), birthDate.plusMonths(40),
            ChineseGrowthMetric.HEAD_CIRCUMFERENCE_FOR_AGE, "GIRL"
        )!!

        assertEquals(listOf(birthDate.plusMonths(35), birthDate.plusMonths(36)), headWindow.map { it.date })
        assertNull(chineseGrowthReferenceChartWindow(
            birthDate, birthDate, birthDate.plusMonths(2),
            ChineseGrowthMetric.WEIGHT_FOR_AGE, null
        ))
        assertTrue(chineseGrowthReferenceChartWindow(
            birthDate, birthDate.plusMonths(85), birthDate.plusMonths(86),
            ChineseGrowthMetric.WEIGHT_FOR_AGE, "BOY"
        )!!.isEmpty())
    }

}

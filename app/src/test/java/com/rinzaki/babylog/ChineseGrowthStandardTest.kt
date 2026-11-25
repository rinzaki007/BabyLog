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
}

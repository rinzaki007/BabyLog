package com.rinzaki.babylog

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Percentile values published by WS/T 423—2022 Appendix A.
 * Values are ordered P3, P10, P25, P50, P75, P90, P97.
 */
internal data class GrowthPercentiles(
    val p3: Float,
    val p10: Float,
    val p25: Float,
    val p50: Float,
    val p75: Float,
    val p90: Float,
    val p97: Float
) {
    internal fun interpolate(other: GrowthPercentiles, fraction: Float): GrowthPercentiles {
        fun lerp(start: Float, end: Float) = start + (end - start) * fraction
        return GrowthPercentiles(
            lerp(p3, other.p3),
            lerp(p10, other.p10),
            lerp(p25, other.p25),
            lerp(p50, other.p50),
            lerp(p75, other.p75),
            lerp(p90, other.p90),
            lerp(p97, other.p97)
        )
    }
}

internal data class GrowthStandardRow(
    /** Exact age in completed calendar months as printed in the standard. */
    val ageMonths: Int,
    val percentiles: GrowthPercentiles
)

internal enum class ChineseGrowthMetric(val maximumAgeMonths: Int) {
    WEIGHT_FOR_AGE(84),
    LENGTH_HEIGHT_FOR_AGE(84),
    HEAD_CIRCUMFERENCE_FOR_AGE(36)
}

/**
 * Interpolates between adjacent published rows only. It never extrapolates beyond the
 * first or last source row, and preserves the standard's non-uniform source intervals.
 */
internal fun interpolateGrowthStandard(
    rows: List<GrowthStandardRow>,
    ageMonths: Double
): GrowthPercentiles? {
    if (!ageMonths.isFinite() || rows.isEmpty()) return null
    val ordered = rows.sortedBy { it.ageMonths }
    if (ordered.zipWithNext().any { (left, right) -> left.ageMonths >= right.ageMonths }) {
        return null
    }
    if (ageMonths < ordered.first().ageMonths || ageMonths > ordered.last().ageMonths) return null
    val exact = ordered.firstOrNull { ageMonths == it.ageMonths.toDouble() }
    if (exact != null) return exact.percentiles
    val upperIndex = ordered.indexOfFirst { it.ageMonths > ageMonths }
    if (upperIndex <= 0) return null
    val lower = ordered[upperIndex - 1]
    val upper = ordered[upperIndex]
    val fraction = ((ageMonths - lower.ageMonths) / (upper.ageMonths - lower.ageMonths)).toFloat()
    return lower.percentiles.interpolate(upper.percentiles, fraction)
}

/**
 * Converts a calendar date to fractional completed months, using the baby's actual
 * month boundaries (including short months and leap years). Returns null before birth
 * or outside the selected metric's published age range.
 */
internal fun chineseGrowthAgeMonths(
    birthDate: LocalDate,
    measuredDate: LocalDate,
    metric: ChineseGrowthMetric
): Double? {
    if (measuredDate.isBefore(birthDate)) return null
    val completedMonths = growthCompletedMonths(birthDate, measuredDate)
    if (completedMonths >= metric.maximumAgeMonths) return null
    val monthStart = birthDate.plusMonths(completedMonths)
    val nextMonth = birthDate.plusMonths(completedMonths + 1)
    val elapsedDays = ChronoUnit.DAYS.between(monthStart, measuredDate)
    val monthLengthDays = ChronoUnit.DAYS.between(monthStart, nextMonth)
    return completedMonths + elapsedDays.toDouble() / monthLengthDays.toDouble()
}

/** Maps the profile's stored labels to the sex-specific tables in WS/T 423—2022. */
internal fun chineseGrowthStandardSex(profileSex: String?): String? = when (profileSex) {
    "男宝" -> "BOY"
    "女宝" -> "GIRL"
    else -> null
}

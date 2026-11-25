package com.rinzaki.babylog

/**
 * Single entry point for the age-specific reference tables in WS/T 423—2022 Appendix A.
 *
 * Values retain the source unit for each metric: kg for weight and cm for length/height
 * and head circumference. The caller remains responsible for using the matching metric
 * and age axis; this accessor does not convert or extrapolate values.
 */
internal object ChineseGrowthReferenceData {
    fun rows(metric: ChineseGrowthMetric, sex: String?): List<GrowthStandardRow>? =
        when (sex) {
            "BOY" -> rowsForSex(metric, "BOY")
            "GIRL" -> rowsForSex(metric, "GIRL")
            else -> null
        }

    private fun rowsForSex(metric: ChineseGrowthMetric, sex: String): List<GrowthStandardRow>? =
        when (metric) {
            ChineseGrowthMetric.WEIGHT_FOR_AGE -> ChineseGrowthStandardData.rows(metric, sex)
            ChineseGrowthMetric.LENGTH_HEIGHT_FOR_AGE -> ChineseGrowthHeightData.rows(sex)
            ChineseGrowthMetric.HEAD_CIRCUMFERENCE_FOR_AGE -> ChineseGrowthHeadCircumferenceData.rows(sex)
        }
}

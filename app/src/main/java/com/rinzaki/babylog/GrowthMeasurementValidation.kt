package com.rinzaki.babylog

internal const val MAX_LENGTH_HEIGHT_CM = 150.0

/**
 * Parses an optional numeric measurement and rejects malformed or out-of-range input.
 * A blank field is intentionally omitted from the saved measurement.
 */
internal fun parseOptionalMeasurement(
    raw: String,
    label: String,
    minimum: Double,
    maximum: Double
): Double? {
    if (raw.isBlank()) return null
    val value = raw.trim().toDoubleOrNull()
        ?: throw IllegalArgumentException("${label}请输入有效数字")
    if (value !in minimum..maximum) {
        throw IllegalArgumentException("请检查${label}是否在合理范围内")
    }
    return value
}

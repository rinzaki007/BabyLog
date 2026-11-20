package com.rinzaki.babylog

/** Parses an optional positive quantity while keeping blank values optional. */
internal fun parseOptionalRecordAmount(raw: String, label: String, max: Double): Double? {
    if (raw.isBlank()) return null
    val amount = raw.trim().toDoubleOrNull()
    require(amount != null && amount.isFinite() && amount > 0.0 && amount <= max) {
        "请检查$label"
    }
    return amount
}

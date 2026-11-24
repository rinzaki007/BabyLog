package com.rinzaki.babylog

internal fun hasDiaperRecordSelection(urine: Boolean, stool: Boolean): Boolean =
    urine || stool

internal fun parseTemperatureC(raw: String): Double {
    val value = raw.trim().replace(',', '.').toDoubleOrNull()
        ?: throw IllegalArgumentException("请输入有效的体温数值")
    require(value in 30.0..45.0) { "体温数值请填写 30.0–45.0 ℃" }
    return value
}

internal fun parseSleepDuration(raw: String): Int {
    val duration = raw.trim().toIntOrNull()
    require(duration != null && duration in 10..720) {
        "睡眠时长请输入 10–720 分钟"
    }
    return duration
}

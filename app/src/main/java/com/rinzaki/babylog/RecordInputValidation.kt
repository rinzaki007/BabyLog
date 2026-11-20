package com.rinzaki.babylog

internal fun hasDiaperRecordSelection(urine: Boolean, stool: Boolean): Boolean =
    urine || stool

internal fun parseSleepDuration(raw: String): Int {
    val duration = raw.trim().toIntOrNull()
    require(duration != null && duration in 10..720) {
        "睡眠时长请输入 10–720 分钟"
    }
    return duration
}

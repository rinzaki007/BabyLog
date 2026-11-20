package com.rinzaki.babylog

internal fun familySyncStatusLabel(
    configured: Boolean,
    syncing: Boolean,
    hasError: Boolean,
    pendingCount: Int,
    conflictCount: Int,
    lastSyncAt: Long
): String = when {
    !configured -> "未配置"
    syncing -> "同步中"
    hasError -> "需检查"
    conflictCount > 0 -> "有冲突"
    pendingCount > 0 -> "待同步"
    lastSyncAt > 0L -> "正常"
    else -> "待首次同步"
}

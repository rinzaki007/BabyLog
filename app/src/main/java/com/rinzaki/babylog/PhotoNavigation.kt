package com.rinzaki.babylog

internal fun photoIndexAfterHorizontalSwipe(
    currentIndex: Int,
    horizontalDragPx: Float,
    thresholdPx: Float,
    photoCount: Int
): Int? {
    if (
        photoCount <= 0 ||
        currentIndex !in 0 until photoCount ||
        kotlin.math.abs(horizontalDragPx) <= thresholdPx.coerceAtLeast(0f)
    ) {
        return null
    }
    val targetIndex = if (horizontalDragPx < 0f) currentIndex + 1 else currentIndex - 1
    return targetIndex.takeIf { it in 0 until photoCount }
}

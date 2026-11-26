package com.rinzaki.babylog

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeAccessibilityTest {
    @Test
    fun summaryPillLabelCombinesMetricAndValueWithoutDecorativeEmoji() {
        assertEquals("喂养，3 次", summaryPillAccessibilityLabel("喂养", "3 次"))
        assertEquals("记录奶量，520 ml", summaryPillAccessibilityLabel("记录奶量", "520 ml"))
    }

    @Test
    fun syncDetailsStateExplainsWhetherDetailsAreExpanded() {
        assertEquals("已展开", syncDetailsAccessibilityState(expanded = true))
        assertEquals("已收起", syncDetailsAccessibilityState(expanded = false))
    }
}

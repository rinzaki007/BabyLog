package com.rinzaki.babylog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineFilterTest {
    @Test
    fun mapsVisibleTimelineFiltersToStoredEventTypes() {
        assertEquals("FEEDING", timelineEventTypeForFilter("喂养"))
        assertEquals("DIAPER", timelineEventTypeForFilter("大小便"))
        assertEquals("SLEEP", timelineEventTypeForFilter("睡眠"))
        assertEquals("TEMPERATURE", timelineEventTypeForFilter("体温记录"))
        assertEquals("SUPPLEMENT", timelineEventTypeForFilter("补充记录"))
        assertEquals("SOLID_FOOD", timelineEventTypeForFilter("辅食记录"))
        assertEquals("GROWTH", timelineEventTypeForFilter("成长测量"))
        assertEquals("MILESTONE", timelineEventTypeForFilter("成长里程碑"))
        assertEquals("GROWTH_JOURNEY", timelineEventTypeForFilter("成长足迹"))
        assertEquals("OTHER", timelineEventTypeForFilter("其他"))
    }

    @Test
    fun unknownOrAllFilterUsesUnrestrictedTimelineQuery() {
        assertNull(timelineEventTypeForFilter("全部"))
        assertNull(timelineEventTypeForFilter("未知分类"))
    }
}

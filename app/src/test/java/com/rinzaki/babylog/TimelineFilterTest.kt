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

    @Test
    fun datePresetsProduceInclusiveLocalDateRanges() {
        val today = java.time.LocalDate.of(2026, 11, 24)

        assertEquals(today to today, timelineDateRangeForPreset("今天", today))
        assertEquals(today.minusDays(1) to today.minusDays(1), timelineDateRangeForPreset("昨天", today))
        assertEquals(java.time.LocalDate.of(2026, 11, 23) to today, timelineDateRangeForPreset("本周", today))
        assertEquals(today.minusDays(6) to today, timelineDateRangeForPreset("近7天", today))
        assertEquals(today.minusDays(29) to today, timelineDateRangeForPreset("近30天", today))
        assertEquals(null to null, timelineDateRangeForPreset("全部日期", today))
    }

    @Test
    fun unknownDatePresetClearsOnlyDateRange() {
        val today = java.time.LocalDate.of(2026, 11, 24)
        assertEquals(null to null, timelineDateRangeForPreset("未知", today))
    }

    @Test
    fun datePresetSelectionReflectsCurrentRange() {
        val today = java.time.LocalDate.of(2026, 11, 24)

        assertEquals(true, timelineDatePresetIsSelected("昨天", today, today.minusDays(1), today.minusDays(1)))
        assertEquals(false, timelineDatePresetIsSelected("今天", today, today.minusDays(1), today.minusDays(1)))
        assertEquals(true, timelineDatePresetIsSelected("全部日期", today, null, null))
        assertEquals(false, timelineDatePresetIsSelected("全部日期", today, today, today))
    }
}

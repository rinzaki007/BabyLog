package com.rinzaki.babylog

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineEmptyStateTest {
    @Test
    fun explainsCombinedDateAndCategoryFilters() {
        val state = timelineEmptyState(hasDateFilter = true, selectedFilter = "喂养")

        assertEquals("这个日期范围内暂无符合条件的喂养记录", state.title)
        assertEquals("可以调整日期范围、切换分类，或清除筛选查看全部记录。", state.description)
    }

    @Test
    fun explainsDateOnlyFilter() {
        val state = timelineEmptyState(hasDateFilter = true, selectedFilter = "全部")

        assertEquals("这个日期范围内暂无记录", state.title)
        assertEquals("可以调整日期范围，或清除日期筛选查看全部记录。", state.description)
    }

    @Test
    fun explainsCategoryOnlyFilter() {
        val state = timelineEmptyState(hasDateFilter = false, selectedFilter = "睡眠")

        assertEquals("暂无睡眠记录", state.title)
        assertEquals("当前分类下暂时没有记录，可以切换其他分类查看宝宝的成长足迹。", state.description)
    }

    @Test
    fun givesFirstRecordGuidanceWithoutFilters() {
        val state = timelineEmptyState(hasDateFilter = false, selectedFilter = "全部")

        assertEquals("第一条记录，等你来写", state.title)
        assertEquals("从一次喂养、一段好觉或一次成长测量开始。", state.description)
    }
}

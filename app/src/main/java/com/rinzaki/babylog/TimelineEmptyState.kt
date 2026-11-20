package com.rinzaki.babylog

internal data class TimelineEmptyState(
    val title: String,
    val description: String
)

internal fun timelineEmptyState(
    hasDateFilter: Boolean,
    selectedFilter: String
): TimelineEmptyState = when {
    hasDateFilter && selectedFilter != "全部" -> TimelineEmptyState(
        title = "这个日期范围内暂无${selectedFilter}记录",
        description = "可以调整日期范围、切换分类，或清除筛选查看全部记录。"
    )
    hasDateFilter -> TimelineEmptyState(
        title = "这个日期范围内暂无记录",
        description = "可以调整日期范围，或清除日期筛选查看全部记录。"
    )
    selectedFilter != "全部" -> TimelineEmptyState(
        title = "还没有${selectedFilter}记录",
        description = "试试切换其他分类，查看宝宝的成长足迹。"
    )
    else -> TimelineEmptyState(
        title = "第一条记录，等你来写",
        description = "从一次喂养、一段好觉或一次成长测量开始。"
    )
}

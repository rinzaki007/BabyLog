package com.rinzaki.babylog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rinzaki.babylog.data.BabyEntity
import com.rinzaki.babylog.data.BabyEventEntity
import com.rinzaki.babylog.data.BabyLogDao
import kotlinx.coroutines.flow.flowOf
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private fun readableGrowthPosition(raw: String): String {
    val normalized = raw.uppercase(Locale.ROOT)
        .replace("—", "–")
        .replace("-", "–")
        .replace(" ", "")
    return when {
        normalized.contains("P25") && normalized.contains("P75") -> "中间位置（$raw）"
        normalized.contains("P10") && normalized.contains("P25") -> "略低位置（$raw）"
        normalized.contains("P3") && normalized.contains("P25") -> "较低位置（$raw）"
        normalized.contains("P75") && normalized.contains("P90") -> "略高位置（$raw）"
        normalized.contains("P75") && normalized.contains("P97") -> "较高位置（$raw）"
        normalized.contains("P3") && normalized.contains("P10") -> "较低位置（$raw）"
        normalized.contains("P90") && normalized.contains("P97") -> "较高位置（$raw）"
        normalized.contains("P97") || normalized.startsWith("≥") || normalized.startsWith(">") -> "高于较高参考范围（$raw）"
        normalized.contains("P3") || normalized.startsWith("<") -> "低于较低参考范围（$raw）"
        else -> raw
    }
}


@Composable
private fun GrowthPercentileBandTrendCard(
    points: List<GrowthPercentileTrendPoint>,
    selectedVisibleIndex: Int
) {
    val labels = listOf("≥ P97", "P75–P97", "P25–P75", "P3–P25", "< P3")
    val chartColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val firstTimestamp = points.minOf { it.timestamp }
    val lastTimestamp = points.maxOf { it.timestamp }
    val dateFormat = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("百分位区间变化", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "按实际测量日期展示与同性别、同月龄中国标准的区间对照",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    Modifier.width(54.dp).height(140.dp),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    labels.forEach { label ->
                        Text(label, style = MaterialTheme.typography.labelSmall, color = textColor, maxLines = 1)
                    }
                }
                Canvas(Modifier.weight(1f).height(140.dp)) {
                    val rowHeight = size.height / 5f
                    labels.forEachIndexed { index, _ ->
                        val y = (index + 0.5f) * rowHeight
                        drawLine(
                            color = gridColor.copy(alpha = 0.65f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    points.forEach { point ->
                        val x = if (lastTimestamp == firstTimestamp) {
                            size.width / 2f
                        } else {
                            ((point.timestamp - firstTimestamp).toFloat() /
                                (lastTimestamp - firstTimestamp).toFloat()).coerceIn(0f, 1f) * size.width
                        }
                        val row = 4 - point.band.ordinal
                        val center = Offset(x, (row + 0.5f) * rowHeight)
                        val selected = point.visibleIndex == selectedVisibleIndex
                        if (selected) {
                            drawCircle(
                                color = chartColor.copy(alpha = 0.18f),
                                radius = 10.dp.toPx(),
                                center = center
                            )
                        }
                        drawCircle(
                            color = chartColor,
                            radius = if (selected) 5.5.dp.toPx() else 4.dp.toPx(),
                            center = center
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    java.time.Instant.ofEpochMilli(firstTimestamp).atZone(ZoneId.systemDefault())
                        .toLocalDate().format(dateFormat),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor
                )
                Text(
                    java.time.Instant.ofEpochMilli(lastTimestamp).atZone(ZoneId.systemDefault())
                        .toLocalDate().format(dateFormat),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor
                )
            }
            val selected = points.firstOrNull { it.visibleIndex == selectedVisibleIndex }
            Text(
                selected?.let { "当前选中：${readableGrowthPosition(it.bandLabel)}" }
                    ?: "共 ${points.size} 次测量有可用的百分位区间参考",
                style = MaterialTheme.typography.labelSmall,
                color = chartColor,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "每个点代表一次真实测量；不连接测量点、不补齐缺失月份。区间变化仅作记录对照，不代表健康诊断。",
                style = MaterialTheme.typography.labelSmall,
                color = textColor
            )
        }
    }
}

@Composable
internal fun TrendsSummary(babyId: String, dao: BabyLogDao) {
    val context = LocalContext.current.applicationContext
    val visibleFeedingMethods = FeedingPreferences.enabledMethods(context)
    val baby by produceState<BabyEntity?>(initialValue = null, babyId, dao) {
        value = dao.getBaby(babyId)
    }
    var range by remember { mutableStateOf("近7天") }
    var category by remember { mutableStateOf("全部") }
    val rangeDays = when (range) { "近7天" -> 7; "近30天" -> 30; "近90天" -> 90; else -> null }
    val start = rangeDays?.let {
        LocalDate.now().minusDays((it - 1).toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } ?: 0L
    val eventsFlow = remember(babyId, start) { dao.observeNonVaccineEventsInRange(babyId, start, Long.MAX_VALUE) }
    val events by eventsFlow.collectAsState(initial = emptyList())
    val now = System.currentTimeMillis()
    val ranged = events.filter { it.deletedAt == null && it.occurredAt >= start && it.occurredAt <= now }
    val recent = ranged.filter { event ->
        when (category) {
            "喂养" -> event.eventType == "FEEDING"
            "护理" -> event.eventType == "DIAPER"
            "睡眠" -> event.eventType == "SLEEP"
            "补充" -> event.eventType == "SUPPLEMENT"
            "辅食" -> event.eventType == "SOLID_FOOD"
            "成长" -> event.eventType == "GROWTH"
            "里程碑" -> event.eventType == "MILESTONE"
            "体温" -> event.eventType == "TEMPERATURE"
            else -> true
        }
    }
    val feedingEvents = recent.filter { it.eventType == "FEEDING" }
    val milk = feedingEvents.sumOf { runCatching { JSONObject(it.payloadJson).optInt("amountMl") }.getOrDefault(0) }
    val feedCount = feedingEvents.size
    val feedingMethods = feedingEvents.groupingBy {
        runCatching { JSONObject(it.payloadJson).optString("method", "配方奶").ifBlank { "配方奶" } }.getOrDefault("配方奶")
    }.eachCount()
    val breastMinutes = feedingEvents.sumOf {
        runCatching { JSONObject(it.payloadJson).optInt("breastMinutes") }.getOrDefault(0)
    }
    val peeCount = recent.count { it.eventType == "DIAPER" && runCatching { JSONObject(it.payloadJson).optBoolean("urine") }.getOrDefault(false) }
    val stoolCount = recent.count { it.eventType == "DIAPER" && runCatching { JSONObject(it.payloadJson).optBoolean("stool") }.getOrDefault(false) }
    val sleepMinutes = recent.filter { it.eventType == "SLEEP" }.sumOf { runCatching { JSONObject(it.payloadJson).optInt("durationMinutes") }.getOrDefault(0) }
    val supplementEvents = recent.filter { it.eventType == "SUPPLEMENT" }
    val solidFoodEvents = recent.filter { it.eventType == "SOLID_FOOD" }
    val temperatureEvents = recent.filter { it.eventType == "TEMPERATURE" }
    val milestoneEvents = recent.filter { it.eventType == "MILESTONE" }
    val waterMl = supplementEvents.filter {
        runCatching {
            val payload = JSONObject(it.payloadJson)
            payload.optString("supplementName") == "水" && payload.optString("unit") == "ml"
        }.getOrDefault(false)
    }.sumOf { runCatching { JSONObject(it.payloadJson).optDouble("amount").toInt() }.getOrDefault(0) }
    // The fallback start date must come from the baby's complete record history,
    // not the currently selected category, so category averages share one denominator.
    val daysCovered = statisticsDaysCovered(
        events = ranged,
        rangeDays = rangeDays,
        birthTimeMillis = baby?.birthTime,
        today = LocalDate.now(),
        zone = ZoneId.systemDefault()
    )
    val average = recent.size.toDouble() / daysCovered.toDouble()
    var showMetricDetails by remember(range, category) { mutableStateOf(false) }
    var showTrendDetails by remember(range, category) { mutableStateOf(true) }
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("数据统计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${recent.size} 条记录", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("统计周期", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            SelectionRow(listOf("近7天", "近30天", "近90天", "全部"), range) { range = it }
            Text("记录类型", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            SelectionRow(listOf("全部", "喂养", "辅食", "补充", "护理", "睡眠", "体温", "成长", "里程碑"), category) { category = it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryPill("📈", "记录总数", "${recent.size} 条", Modifier.weight(1f))
                SummaryPill("🗓️", "日均记录", "${"%.1f".format(Locale.CHINA, average)} 条", Modifier.weight(1f))
            }
            TextButton(onClick = { showMetricDetails = !showMetricDetails }, modifier = Modifier.align(Alignment.Start)) {
                Text(if (showMetricDetails) "收起分类统计" else "查看分类统计")
            }
            if (showMetricDetails) {
            if (category == "全部" || category == "喂养") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryPill("🍼", "记录奶量", "${milk} ml", Modifier.weight(1f))
                    SummaryPill("🧴", "喂养次数", "${feedCount} 次", Modifier.weight(1f))
                    if (visibleFeedingMethods.any { it == "母乳" || it == "混合喂养" }) {
                        SummaryPill("🤱", "亲喂时长", if (breastMinutes > 0) "${breastMinutes} 分钟" else "未记录", Modifier.weight(1f))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    visibleFeedingMethods.forEach { method ->
                        val icon = when (method) { "母乳" -> "🤱"; "配方奶" -> "🍼"; else -> "🥛" }
                        SummaryPill(icon, method, "${feedingMethods[method] ?: 0} 次", Modifier.weight(1f))
                    }
                }
            }
            if (category == "全部" || category == "补充") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryPill("🧴", "补充次数", "${supplementEvents.size} 次", Modifier.weight(1f))
                SummaryPill("🚰", "饮水量", "${waterMl} ml", Modifier.weight(1f))
            }
            if (category == "全部" || category == "辅食") SummaryPill("🥣", "辅食次数", "${solidFoodEvents.size} 次", Modifier.fillMaxWidth())
            if (category == "全部" || category == "护理") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryPill("💧", "小便", "${peeCount} 次", Modifier.weight(1f))
                SummaryPill("🌿", "大便", "${stoolCount} 次", Modifier.weight(1f))
            }
            if (category == "全部" || category == "睡眠") SummaryPill("🌙", "睡眠时长", if (sleepMinutes >= 60) "${sleepMinutes / 60}小时${sleepMinutes % 60}分" else "${sleepMinutes}分钟", Modifier.fillMaxWidth())
            if (category == "全部" || category == "体温") SummaryPill("🌡️", "体温记录", "${temperatureEvents.size} 次", Modifier.fillMaxWidth())
            if (category == "全部" || category == "里程碑") SummaryPill("🌱", "成长里程碑", "${milestoneEvents.size} 次", Modifier.fillMaxWidth())
            }
            if (recent.isEmpty()) Text("这个时间范围内还没有相关记录。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
    TextButton(onClick = { showTrendDetails = !showTrendDetails }) {
        Text(if (showTrendDetails) "收起趋势分析" else "查看趋势分析")
    }
    if (showTrendDetails) {
        DailyTrendChart(recent, range, category, ranged)
        PeriodTrendSummary(recent, range, category, ranged)
    }
}

@Composable
private fun DailyTrendChart(events: List<BabyEventEntity>, range: String, category: String, allTimeEvents: List<BabyEventEntity>) {
    val availableMetrics = remember(category) { trendMetricsForCategory(category) }
    var metric by remember(category) { mutableStateOf(availableMetrics.first()) }
    val days = dailyTrendWindowDays(range, allTimeEvents)
    val unit = when (metric) {
        "喂养奶量" -> "ml"
        "睡眠小时" -> "小时"
        else -> "次"
    }
    val points = remember(events, metric, days) { dailyTrendPoints(events, metric, days) }
    var selectedDayIndex by remember(points) { mutableStateOf(points.lastIndex) }
    val selectedDay = points.getOrNull(selectedDayIndex)
    val maximum = points.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f
    val total = points.sumOf { it.value.toDouble() }.toFloat()
    val chartPrimary = MaterialTheme.colorScheme.primary
    val chartSecondary = MaterialTheme.colorScheme.secondary
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("每日记录趋势", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${range} · ${metric}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${"%.1f".format(Locale.CHINA, total)} $unit", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Text(
                if (range == "全部") "统计范围为全部记录；趋势图最多展示最近 366 天" else "与上方统计使用同一周期",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SelectionRow(availableMetrics, metric, onSelect = { metric = it })
            Canvas(
                Modifier.fillMaxWidth().height(150.dp).pointerInput(points) {
                    detectTapGestures { tap ->
                        dailyTrendTappedIndex(
                            tapX = tap.x,
                            chartWidth = size.width.toFloat(),
                            horizontalPadding = 4.dp.toPx(),
                            pointCount = points.size
                        )?.let { selectedDayIndex = it }
                    }
                }
            ) {
                val horizontalPadding = 4.dp.toPx()
                val topPadding = 8.dp.toPx()
                val bottomPadding = 8.dp.toPx()
                val chartHeight = size.height - topPadding - bottomPadding
                val slotWidth = (size.width - horizontalPadding * 2) / points.size.coerceAtLeast(1)
                val barWidth = (slotWidth * if (days == 7) 0.52f else 0.68f).coerceAtLeast(1.dp.toPx())
                points.forEachIndexed { index, point ->
                    val barHeight = chartHeight * (point.value / maximum)
                    val left = horizontalPadding + index * slotWidth + (slotWidth - barWidth) / 2f
                    drawRoundRect(
                        color = when {
                            index == selectedDayIndex -> chartPrimary
                            point.value == 0f -> chartSecondary.copy(alpha = 0.16f)
                            else -> chartSecondary.copy(alpha = 0.85f)
                        },
                        topLeft = Offset(left, topPadding + chartHeight - barHeight),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight.coerceAtLeast(2.dp.toPx())),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(points.first().date.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("最高 ${"%.1f".format(Locale.CHINA, maximum)} $unit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(points.last().date.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            selectedDay?.let { point ->
                Text(
                    point.date.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)) + " · " +
                        String.format(Locale.CHINA, "%.1f", point.value) + " " + unit + " · " +
                        if (point.observationCount == 0) "无相关记录" else "相关记录 ${point.observationCount} 条",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text("无相关记录的日期按 0 统计；有记录但指标值为 0 会单独标明记录条数。睡眠按记录的时长合计。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


@Composable
private fun PeriodTrendSummary(
    events: List<BabyEventEntity>,
    range: String,
    category: String,
    allTimeEvents: List<BabyEventEntity>
) {
    val metrics = remember(category) { trendMetricsForCategory(category) }
    var metric by remember(category) { mutableStateOf(metrics.first()) }
    var period by remember { mutableStateOf("周") }
    val today = LocalDate.now()
    val zone = remember { ZoneId.systemDefault() }
    val days = dailyTrendWindowDays(range, allTimeEvents, today, zone)
    val daily = remember(events, metric, days, today, zone) {
        dailyTrendPoints(events, metric, days, today, zone)
    }
    val allPeriods = remember(daily, period) { aggregateTrendPeriods(daily, period) }
    val periods = remember(allPeriods) { allPeriods.takeLast(8) }
    var selectedPeriodStart by remember(range, category, metric, period) { mutableStateOf<LocalDate?>(null) }
    val selectedPeriodIndex = allPeriods.indexOfFirst { it.startDate == selectedPeriodStart }
        .takeIf { it >= 0 } ?: allPeriods.lastIndex
    val selectedPeriod = allPeriods.getOrNull(selectedPeriodIndex)
    val selectedComparison = periodTrendComparison(allPeriods, selectedPeriodIndex)
    val maximum = periods.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f
    val unit = when (metric) {
        "喂养奶量" -> "ml"
        "睡眠小时" -> "小时"
        else -> "次"
    }
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("周 / 月周期趋势", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "按自然周或自然月汇总，与上方每日趋势使用相同数据范围",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SelectionRow(listOf("周", "月"), period) { period = it }
            SelectionRow(metrics, metric) { metric = it }
            if (periods.isEmpty()) {
                Text("当前范围暂无可汇总的数据。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                periods.forEach { point ->
                    val isSelected = point.startDate == selectedPeriod?.startDate
                    Column(
                        modifier = Modifier.fillMaxWidth().clickable { selectedPeriodStart = point.startDate },
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val label = if (period == "周") {
                                "${point.startDate.format(DateTimeFormatter.ofPattern("M/d", Locale.CHINA))} – ${point.endDate.format(DateTimeFormatter.ofPattern("M/d", Locale.CHINA))}"
                            } else {
                                point.startDate.format(DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA))
                            }
                            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${"%.1f".format(Locale.CHINA, point.value)} $unit",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Box(
                            Modifier.fillMaxWidth().height(8.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(99.dp))
                        ) {
                            Box(
                                Modifier.fillMaxWidth((point.value / maximum).coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                        RoundedCornerShape(99.dp)
                                    )
                            )
                        }
                        Text(
                            "${point.nonZeroDays} 个非零日 · 覆盖 ${point.coveredDays} 天" + if (isSelected) " · 已选中" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                selectedPeriod?.let { point ->
                    val label = if (period == "周") {
                        "${point.startDate.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))} – ${point.endDate.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))}"
                    } else {
                        point.startDate.format(DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA))
                    }
                    Text(
                        "$label · ${"%.1f".format(Locale.CHINA, point.value)} $unit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (point.observationCount > 0) "相关记录 ${point.observationCount} 条" else "暂无相关记录",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    periodTrendDailyAverage(point)?.let { dailyAverage ->
                        Text(
                            "日均 ${"%.1f".format(Locale.CHINA, dailyAverage)} $unit/天（按覆盖天数计算）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (
                        selectedPeriodIndex > 0 &&
                        (!point.isComplete || !allPeriods[selectedPeriodIndex - 1].isComplete)
                    ) {
                        Text(
                            "当前或上一周期不是完整自然${period}，暂不直接比较总量",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    selectedComparison?.let { comparison ->
                        val sign = if (comparison.change > 0f) "+" else ""
                        val activeSign = if (comparison.nonZeroDaysChange > 0) "+" else ""
                        Text(
                            "较上一${if (period == "周") "周" else "月"} · 变化 $sign${"%.1f".format(Locale.CHINA, comparison.change)} $unit · 非零日 $activeSign${comparison.nonZeroDaysChange} 天",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    "首尾周期可能不完整；无相关记录的日期按 0 统计，有记录但指标值为 0 会保留记录条数。日均按实际覆盖天数计算，比较不同长度的月份时可结合日均值理解总量变化。非零日按所选指标累计值大于 0 的日期统计。点击周期可查看与上一周期的变化。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GrowthOverviewSparkline(values: List<Float>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val pointColor = MaterialTheme.colorScheme.secondary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Canvas(Modifier.width(56.dp).height(30.dp)) {
            if (values.isNotEmpty()) {
                val left = 3.dp.toPx()
                val right = size.width - left
                val top = 4.dp.toPx()
                val bottom = size.height - top
                val minimum = values.minOrNull() ?: 0f
                val maximum = values.maxOrNull() ?: minimum
                val span = (maximum - minimum).takeIf { it.isFinite() && it > 0f } ?: 1f
                val coordinates = values.mapIndexed { index, value ->
                    val x = if (values.size == 1) size.width / 2f
                    else left + (right - left) * index / (values.size - 1).toFloat()
                    val y = if (maximum == minimum) size.height / 2f
                    else bottom - ((value - minimum) / span) * (bottom - top)
                    Offset(x, y)
                }
                coordinates.zipWithNext().forEach { (start, end) ->
                    drawLine(
                        color = lineColor,
                        start = start,
                        end = end,
                        strokeWidth = 2.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
                coordinates.forEach { point ->
                    drawCircle(
                        color = pointColor,
                        radius = 2.5.dp.toPx(),
                        center = point
                    )
                }
            }
        }
        Text(
            if (values.isEmpty()) "暂无走势" else "最近走势",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GrowthOverviewCard(baby: BabyEntity, events: List<BabyEventEntity>) {
    val zone = remember { ZoneId.systemDefault() }
    val asOf = remember { System.currentTimeMillis() }
    val metrics = remember(baby, events, zone, asOf) {
        listOf(
            Triple("体重", "kg", growthMetricOverview(baby, events, "weightGrams", asOf, zone)),
            Triple("身长 / 身高", "cm", growthMetricOverview(baby, events, "lengthCm", asOf, zone)),
            Triple("头围", "cm", growthMetricOverview(baby, events, "headCircumferenceCm", asOf, zone))
        )
    }
    val referenceHints = metrics.mapNotNull { it.third.referenceHint }.distinct()

    Card(shape = RoundedCornerShape(22.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("成长总览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "汇总三项指标最近一次实际测量及变化",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            metrics.forEachIndexed { index, (label, unit, overview) ->
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(
                            overview.value?.let { value ->
                                String.format(Locale.CHINA, if (unit == "kg") "%.2f %s" else "%.1f %s", value, unit)
                            } ?: "暂无数据",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            overview.measuredAt?.let { measuredAt ->
                                Text(
                                    SimpleDateFormat("M月d日", Locale.CHINA).format(Date(measuredAt)) +
                                        " · " + growthMeasurementAgeLabel(baby.birthTime, measuredAt, zone),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } ?: Text(
                                "尚无测量记录",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (overview.comparison != null) {
                                val comparison = overview.comparison
                                val sign = if (comparison.change > 0f) "+" else ""
                                Text(
                                    "较上次 $sign${String.format(Locale.CHINA, "%.2f", comparison.change)} $unit · 间隔 ${comparison.elapsedDays} 天",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (overview.value != null) {
                                Text(
                                    "首次记录 · 后续测量后显示变化",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            overview.percentileBand?.let { band ->
                                Text(
                                    "中国标准参考：${readableGrowthPosition(band)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (overview.recentValues.isNotEmpty()) GrowthOverviewSparkline(overview.recentValues)
                    }
                }
                if (index != metrics.lastIndex) {
                    Spacer(
                        Modifier.fillMaxWidth().height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    )
                }
            }
            referenceHints.forEach { hint ->
                Text(
                    hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "百分位仅用于同月龄同性别参考对照，不代表健康诊断。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


private enum class LegendMark { SOLID_LINE, DASHED_LINE, BAND }

@Composable
private fun GrowthChartLegendItem(label: String, color: androidx.compose.ui.graphics.Color, mark: LegendMark) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Canvas(Modifier.width(22.dp).height(12.dp)) {
            when (mark) {
                LegendMark.SOLID_LINE -> drawLine(
                    color, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f),
                    strokeWidth = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                LegendMark.DASHED_LINE -> {
                    val y = size.height / 2f
                    val dash = 4.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        drawLine(color, Offset(x, y), Offset((x + dash * 0.55f).coerceAtMost(size.width), y), strokeWidth = 2.dp.toPx())
                        x += dash
                    }
                }
                LegendMark.BAND -> drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, size.height * 0.2f),
                    size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.6f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                )
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GrowthPercentileGuideCard(
    reference: GrowthPercentiles,
    measuredValue: Float,
    unit: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    val markers = listOf(
        Triple("较低位置", "参考线", reference.p25),
        Triple("中间位置", "参考中间值", reference.p50),
        Triple("较高位置", "参考线", reference.p75)
    )
    val difference = measuredValue - reference.p50
    val differenceLabel = when {
        difference > 0.0001f -> "高于"
        difference < -0.0001f -> "低于"
        else -> "接近"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("和同月龄宝宝相比，怎么看？", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "这里是把宝宝和相同性别、相同月龄的参考人群作比较。它描述的是相对位置，不是成绩排名。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                markers.forEach { (code, description, value) ->
                    Column(
                        Modifier.weight(1f).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(horizontal = 7.dp, vertical = 9.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(code, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(String.format(Locale.CHINA, if (unit == "kg") "%.2f" else "%.1f", value) + " $unit", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            Text(
                "中间位置两侧的范围，覆盖参考人群中间约一半的测量值。中间值不是目标值，也不是及格线。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "本次测量 ${String.format(Locale.CHINA, if (unit == "kg") "%.2f" else "%.1f", measuredValue)} $unit，${differenceLabel} P50 ${String.format(Locale.CHINA, if (unit == "kg") "%.2f" else "%.1f", kotlin.math.abs(difference))} $unit",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onToggleExpanded, modifier = Modifier.align(Alignment.End)) {
                Text(if (expanded) "收起其他参考值" else "查看更宽的参考范围")
            }
            if (expanded) {
                Text(
                    listOf(
                        "P3 ${String.format(Locale.CHINA, if (unit == "kg") "%.2f" else "%.1f", reference.p3)}",
                        "P10 ${String.format(Locale.CHINA, if (unit == "kg") "%.2f" else "%.1f", reference.p10)}",
                        "P90 ${String.format(Locale.CHINA, if (unit == "kg") "%.2f" else "%.1f", reference.p90)}",
                        "P97 ${String.format(Locale.CHINA, if (unit == "kg") "%.2f" else "%.1f", reference.p97)}"
                    ).joinToString("   ·   ") { "$it $unit" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "这些是分布两端的参考位置，用于帮助理解曲线范围；单次百分位不能单独判断宝宝是否健康。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun GrowthSection(baby: BabyEntity, events: List<BabyEventEntity>) {
    var metric by remember { mutableStateOf("体重") }
    var range by remember { mutableStateOf("全部") }
    val key = when (metric) { "身长" -> "lengthCm"; "头围" -> "headCircumferenceCm"; else -> "weightGrams" }
    val unit = if (metric == "体重") "kg" else "cm"
    val allPoints = remember(baby, events, key) { growthMetricPoints(baby, events, key) }
    val now = remember { System.currentTimeMillis() }
    val rangeStart = remember(range, now) { growthRangeStartMillis(range, now) }
    val pointSourceIndices = remember(allPoints, rangeStart) {
        growthPointIndicesInRange(allPoints, rangeStart)
    }
    val points = remember(allPoints, pointSourceIndices) {
        pointSourceIndices.map(allPoints::get)
    }
    val chartZone = remember { ZoneId.systemDefault() }
    val birthDate = remember(baby.birthTime, chartZone) {
        java.time.Instant.ofEpochMilli(baby.birthTime).atZone(chartZone).toLocalDate()
    }
    val referenceMetric = when (metric) {
        "身长" -> ChineseGrowthMetric.LENGTH_HEIGHT_FOR_AGE
        "头围" -> ChineseGrowthMetric.HEAD_CIRCUMFERENCE_FOR_AGE
        else -> ChineseGrowthMetric.WEIGHT_FOR_AGE
    }
    val referenceSex = chineseGrowthStandardSex(baby.sex)
    val hasMultipleMeasurementDates = growthMeasurementDateCount(points, chartZone) >= 2
    val referencePoints = remember(birthDate, points, referenceMetric, referenceSex, chartZone) {
        if (!hasMultipleMeasurementDates || referenceSex == null) emptyList()
        else {
            val startDate = java.time.Instant.ofEpochMilli(points.first().first).atZone(chartZone).toLocalDate()
            val endDate = java.time.Instant.ofEpochMilli(points.last().first).atZone(chartZone).toLocalDate()
            chineseGrowthReferenceChartWindow(
                birthDate, startDate, endDate, referenceMetric, referenceSex, chartZone
            ).orEmpty()
        }
    }
    val latest = points.lastOrNull()
    val previous = points.dropLast(1).lastOrNull()
    val delta = if (latest != null && previous != null) latest.second - previous.second else null
    val referenceMin = referencePoints.minOfOrNull { it.percentiles.p3 }
    val referenceMax = referencePoints.maxOfOrNull { it.percentiles.p97 }
    val minValue = listOfNotNull(points.minOfOrNull { it.second }, referenceMin).minOrNull()
    val maxValue = listOfNotNull(points.maxOfOrNull { it.second }, referenceMax).maxOrNull()
    val min = ((minValue ?: 0f) - if (metric == "体重") 0.25f else 1f).coerceAtLeast(0f)
    val max = (maxValue ?: 1f) + if (metric == "体重") 0.25f else 1f
    val dateFormat = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
    var selectedPointIndex by remember(points) { mutableStateOf(points.lastIndex) }
    val selectedPoint = points.getOrNull(selectedPointIndex)
    val selectedReference = remember(selectedPoint, birthDate, referenceMetric, referenceSex, chartZone) {
        selectedPoint?.let { point ->
            val measuredDate = java.time.Instant.ofEpochMilli(point.first).atZone(chartZone).toLocalDate()
            chineseGrowthReferenceAtDate(birthDate, measuredDate, referenceMetric, referenceSex)
        }
    }
    val selectedAssessment = remember(selectedPoint, selectedReference) {
        selectedPoint?.let { point ->
            selectedReference?.let { assessChineseGrowthPercentile(point.second, it) }
        }
    }
    var showAllPercentileMarkers by remember(metric) { mutableStateOf(false) }
    val measurementAssessments = remember(allPoints, birthDate, referenceMetric, referenceSex, chartZone) {
        allPoints.map { point ->
            val measuredDate = java.time.Instant.ofEpochMilli(point.first).atZone(chartZone).toLocalDate()
            chineseGrowthReferenceAtDate(
                birthDate, measuredDate, referenceMetric, referenceSex
            )?.let { reference -> assessChineseGrowthPercentile(point.second, reference) }
        }
    }
    var showAllMeasurements by remember(points) { mutableStateOf(false) }
    val visibleMeasurementIndices = remember(points, showAllMeasurements) {
        visibleGrowthMeasurementIndices(points, showAllMeasurements)
    }
    val chartPrimary = MaterialTheme.colorScheme.primary
    val chartSecondary = MaterialTheme.colorScheme.secondary
    val chartGrid = MaterialTheme.colorScheme.outlineVariant
    GrowthOverviewCard(baby, events)
    var showGrowthDetails by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("成长测量与生长曲线", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("按需展开历史测量、百分位参考和曲线", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { showGrowthDetails = !showGrowthDetails }) {
                Text(if (showGrowthDetails) "收起" else "展开")
            }
        }
        if (showGrowthDetails) {
    val percentileTrendPoints = remember(points, pointSourceIndices, measurementAssessments) {
        growthPercentileTrendPoints(points, pointSourceIndices, measurementAssessments)
    }
    var showPercentileHistory by remember { mutableStateOf(false) }
    if (percentileTrendPoints.isNotEmpty()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("历史百分位区间", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "查看每次测量在中国同月龄标准中的位置变化",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { showPercentileHistory = !showPercentileHistory }) {
                        Text(if (showPercentileHistory) "收起" else "展开")
                    }
                }
                if (showPercentileHistory) {
                    GrowthPercentileBandTrendCard(
                        points = percentileTrendPoints,
                        selectedVisibleIndex = selectedPointIndex
                    )
                }
        }
    }
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("成长曲线", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("每个点都是一次真实测量；背景范围是同月龄参考值", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(latest?.let { "${"%.2f".format(Locale.CHINA, it.second)} $unit" } ?: "暂无数据", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    if (delta != null) {
                        val sign = if (delta > 0) "+" else ""
                        Text("较上次 $sign${"%.2f".format(Locale.CHINA, delta)} $unit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            SelectionRow(listOf("体重", "身长", "头围"), metric) { metric = it }
            SelectionRow(listOf("近 30 天", "近 90 天", "近半年", "近一年", "全部"), range) { range = it }
            if (points.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    Text("还没有${metric}数据，记录几次后就能看到变化", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.width(42.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Text("${"%.1f".format(Locale.CHINA, max)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(110.dp))
                        Text("${"%.1f".format(Locale.CHINA, min)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Canvas(
                        Modifier.weight(1f).height(150.dp).pointerInput(points, min, max) {
                            detectTapGestures { tap ->
                                growthChartNearestPointIndex(
                                    points = points,
                                    tapX = tap.x,
                                    tapY = tap.y,
                                    left = 8f,
                                    right = size.width - 8f,
                                    top = 10f,
                                    bottom = size.height - 12f,
                                    minValue = min,
                                    maxValue = max,
                                    maxDistancePx = 24.dp.toPx()
                                )?.let { selectedPointIndex = it }
                            }
                        }
                    ) {
                        val left = 8f
                        val right = size.width - 8f
                        val top = 10f
                        val bottom = size.height - 12f
                        val valueSpan = (max - min).coerceAtLeast(0.1f)
                        val timeStart = points.first().first
                        val timeEnd = points.last().first
                        repeat(4) { index ->
                            val y = top + (bottom - top) * index / 3f
                            drawLine(chartGrid, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
                        }
                        if (referencePoints.size >= 2 && timeEnd > timeStart) {
                            val referenceCoordinates = referencePoints.map { reference ->
                                val x = left + (right - left) * growthChartTimeFraction(
                                    reference.timestamp, timeStart, timeEnd, 2
                                )
                                fun y(value: Float) = bottom - (value - min) / valueSpan * (bottom - top)
                                listOf(
                                    Offset(x, y(reference.percentiles.p3)),
                                    Offset(x, y(reference.percentiles.p25)),
                                    Offset(x, y(reference.percentiles.p50)),
                                    Offset(x, y(reference.percentiles.p75)),
                                    Offset(x, y(reference.percentiles.p97))
                                )
                            }
                            val percentileBand = Path().apply {
                                moveTo(referenceCoordinates.first()[0].x, referenceCoordinates.first()[0].y)
                                referenceCoordinates.drop(1).forEach { lineTo(it[0].x, it[0].y) }
                                referenceCoordinates.asReversed().forEach { lineTo(it[4].x, it[4].y) }
                                close()
                            }
                            drawPath(percentileBand, chartPrimary.copy(alpha = 0.15f))
                            fun percentileLine(index: Int) = Path().apply {
                                moveTo(referenceCoordinates.first()[index].x, referenceCoordinates.first()[index].y)
                                referenceCoordinates.drop(1).forEach { lineTo(it[index].x, it[index].y) }
                            }
                            listOf(1, 3).forEach { index ->
                                drawPath(
                                    percentileLine(index),
                                    chartPrimary.copy(alpha = 0.88f),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = 1.7.dp.toPx(),
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                            floatArrayOf(6.dp.toPx(), 4.dp.toPx())
                                        )
                                    )
                                )
                            }
                            drawPath(
                                percentileLine(2),
                                chartPrimary.copy(alpha = 0.82f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 2.dp.toPx(),
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            )
                        }
                        val coords = points.map { point ->
                            val x = left + (right - left) * growthChartTimeFraction(
                                point.first, timeStart, timeEnd, points.size
                            )
                            val y = bottom - (point.second - min) / valueSpan * (bottom - top)
                            Offset(x, y)
                        }
                        if (coords.size > 1) {
                            val fillPath = Path().apply {
                                moveTo(coords.first().x, bottom)
                                lineTo(coords.first().x, coords.first().y)
                                coords.drop(1).forEach { lineTo(it.x, it.y) }
                                lineTo(coords.last().x, bottom)
                                close()
                            }
                            drawPath(fillPath, chartSecondary.copy(alpha = 0.10f))
                            val linePath = Path().apply {
                                moveTo(coords.first().x, coords.first().y)
                                // Connect actual observations directly; a smoothed curve can imply
                                // intermediate growth values that were never measured.
                                coords.drop(1).forEach { point -> lineTo(point.x, point.y) }
                            }
                            drawPath(linePath, chartSecondary, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                        }
                        coords.forEachIndexed { index, point ->
                            val isLatest = index == coords.lastIndex
                            val isSelected = index == selectedPointIndex
                            if (isSelected) drawCircle(chartPrimary.copy(alpha = 0.18f), radius = 11.dp.toPx(), center = point)
                            drawCircle(if (isLatest || isSelected) chartPrimary else chartSecondary, radius = if (isLatest || isSelected) 5.dp.toPx() else 3.5.dp.toPx(), center = point)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(points.first().first.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormat) }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(points.last().first.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormat) }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (referencePoints.size >= 2) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GrowthChartLegendItem("宝宝测量值", chartSecondary, LegendMark.SOLID_LINE)
                        GrowthChartLegendItem("同月龄中间值", chartPrimary, LegendMark.SOLID_LINE)
                        GrowthChartLegendItem("常见范围（P25–P75）", chartPrimary.copy(alpha = 0.88f), LegendMark.DASHED_LINE)
                        GrowthChartLegendItem("更宽参考范围（P3–P97）", chartPrimary.copy(alpha = 0.15f), LegendMark.BAND)
                    }
                    Text(
                        "中国标准 WS/T 423—2022。按出生日期计算月龄；早产儿矫正月龄暂未纳入。身长/身高按官方年龄段口径。仅供趋势参考，不作诊断。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (referenceSex == null) {
                    Text(
                        "完善宝宝性别档案后，可显示对应的中国生长标准曲线",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (metric == "头围") {
                    Text(
                        if (!hasMultipleMeasurementDates) {
                            "已有一次头围测量，可查看下方同月龄百分位参考；增加不同日期的测量后即可叠加标准曲线。中国头围标准覆盖 0–36 月龄。"
                        } else {
                            "中国头围标准覆盖 0–36 月龄，且需要至少两次不同日期的测量才能叠加曲线。"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        if (points.size == 1) {
                            "已有一次测量，可查看下方同月龄百分位参考；增加不同日期的测量后即可叠加中国标准曲线。"
                        } else {
                            "至少有两次不同日期的测量后，才会叠加中国标准曲线。"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                selectedPoint?.let { point ->
                    Text(
                        SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(point.first)) + " · " +
                            growthMeasurementAgeLabel(baby.birthTime, point.first) + " · " +
                            String.format(Locale.CHINA, "%.2f", point.second) + " " + unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                selectedPoint?.let {
                    val sourceIndex = pointSourceIndices.getOrNull(selectedPointIndex)
                    sourceIndex?.let { index ->
                        growthMeasurementComparison(allPoints, index, chartZone)
                    }?.let { comparison ->
                        val sign = if (comparison.change > 0f) "+" else ""
                        val changeText = String.format(Locale.CHINA, "%.2f", comparison.change)
                        Text(
                            "较前一次测量 · 间隔 ${comparison.elapsedDays} 天 · 变化 " + sign + changeText + " " + unit,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (metric == "身长") {
                    selectedPoint?.let { point ->
                        val measuredDate = java.time.Instant.ofEpochMilli(point.first)
                            .atZone(chartZone).toLocalDate()
                        growthLengthHeightMethodHint(birthDate, measuredDate)?.let { hint ->
                            Text(
                                "$hint；实际记录请尽量保持测量方式一致。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (selectedAssessment == null) {
                    selectedPoint?.let { point ->
                        val measuredDate = java.time.Instant.ofEpochMilli(point.first)
                            .atZone(chartZone).toLocalDate()
                        growthReferenceUnavailableHint(
                            birthDate, measuredDate, referenceMetric, referenceSex
                        )?.let { hint ->
                            Text(
                                hint,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                selectedAssessment?.let { assessment ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("宝宝在同月龄参考人群中的位置", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(readableGrowthPosition(assessment.bandLabel), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("参考中间值", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(String.format(Locale.CHINA, "%.2f %s", assessment.medianValue, unit), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Text(
                        "这是与同月龄同性别参考分布的对照，不代表健康诊断。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    selectedReference?.let { reference ->
                        selectedPoint?.let { point ->
                            GrowthPercentileGuideCard(
                                reference = reference,
                                measuredValue = point.second,
                                unit = unit,
                                expanded = showAllPercentileMarkers,
                                onToggleExpanded = { showAllPercentileMarkers = !showAllPercentileMarkers }
                            )
                        }
                    }
                    val sourceIndex = pointSourceIndices.getOrNull(selectedPointIndex) ?: -1
                    val previousAssessment = measurementAssessments.getOrNull(sourceIndex - 1)
                    growthPercentileTransitionLabel(
                        previousAssessment?.bandLabel,
                        assessment.bandLabel
                    )?.let { transition ->
                        Text(
                            "与前一次实际测量对照：$transition。仅描述记录区间变化，不代表健康结论。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    "${points.size} 次测量 · 最新记录 ${SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(latest!!.first))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(if (showAllMeasurements) "全部测量" else "最近测量", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                visibleMeasurementIndices.asReversed().forEachIndexed { index, pointIndex ->
                    val point = points[pointIndex]
                    val isSelected = pointIndex == selectedPointIndex
                    Surface(
                        modifier = Modifier.clickable {
                            if (pointIndex >= 0) selectedPointIndex = pointIndex
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(point.first)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                measurementAssessments.getOrNull(pointIndex)?.let { assessment ->
                                    Text(
                                        "同月龄参考：${readableGrowthPosition(assessment.bandLabel)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                "${"%.2f".format(Locale.CHINA, point.second)} $unit",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (index == 0) {
                                Text(
                                    "最新",
                                    modifier = Modifier.padding(start = 8.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else if (isSelected) {
                                Text(
                                    "已选中",
                                    modifier = Modifier.padding(start = 8.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                if (points.size > 5) {
                    TextButton(
                        onClick = { showAllMeasurements = !showAllMeasurements },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(if (showAllMeasurements) "收起测量记录" else "查看全部 · ${points.size} 次")
                    }
                }
            }
        }
    }
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val longTerm = remember(allPoints) { growthMonthlySummaries(allPoints) }
            val interval = remember(allPoints) { growthMeasurementInterval(allPoints) }
            var showAllMonths by remember(allPoints) { mutableStateOf(false) }
            Spacer(Modifier.height(4.dp))
            Text("按月回顾", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("只汇总当月真实记录；没有测量的月份会自动跳过，不会估算数值。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (interval == null) {
                Text(
                    if (allPoints.isEmpty()) {
                        "还没有该项成长测量记录，添加体重、身长或头围数据后会在这里显示变化。"
                    } else {
                        "目前只有一次有效测量；至少记录两次，才能计算两次测量之间的变化。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            interval?.let { observed ->
                val sign = if (observed.change > 0f) "+" else ""
                val rate = observed.changePer30Days?.let { value ->
                    val rateSign = if (value > 0f) "+" else ""
                    " · 约 $rateSign${"%.2f".format(Locale.CHINA, value)} $unit/30天"
                } ?: ""
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
                ) {
                    Text(
                        "最近两次：间隔 ${observed.elapsedDays} 天，变化 $sign${"%.2f".format(Locale.CHINA, observed.change)} $unit$rate",
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (longTerm.isEmpty()) {
                Text("累计测量后，这里会显示每月变化。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val newestFirst = longTerm.asReversed()
                val visibleMonths = if (showAllMonths) newestFirst else newestFirst.take(3)
                visibleMonths.forEach { month ->
                    val changeSign = if (month.change > 0f) "+" else ""
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("${month.month.year}年${month.month.monthValue}月", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text("本月记录 ${month.measurementCount} 次", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("${"%.2f".format(Locale.CHINA, month.latestValue)} $unit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                if (month.measurementCount > 1) {
                                    "本月从 ${"%.2f".format(Locale.CHINA, month.firstValue)} 到 ${"%.2f".format(Locale.CHINA, month.latestValue)} $unit，变化 $changeSign${"%.2f".format(Locale.CHINA, month.change)} $unit（相隔 ${month.spanDays} 天）"
                                } else {
                                    month.previousValue?.let { previous ->
                                        val previousSign = if (month.latestValue > previous) "+" else ""
                                        val gap = month.daysSincePreviousMeasurement?.let { "，相隔 ${it} 天" } ?: ""
                                        "较上一次测量 $previousSign${"%.2f".format(Locale.CHINA, month.latestValue - previous)} $unit$gap"
                                    } ?: "这个月只有一次记录，暂时无法计算变化"
                                },
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (longTerm.size > 3) {
                    TextButton(
                        onClick = { showAllMonths = !showAllMonths },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(if (showAllMonths) "收起" else "查看全部 · ${longTerm.size} 个月")
                    }
                }
            }
            Text("这里只比较真实测量记录，不预测之后会长多少，也不作医学判断。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    }
    }
}


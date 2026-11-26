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
            if (recent.isEmpty()) Text("这个时间范围内还没有相关记录。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
    DailyTrendChart(recent, range, category, ranged)
    PeriodTrendSummary(recent, range, category, ranged)
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
                    "首尾周期可能不完整；无相关记录的日期按 0 统计，有记录但指标值为 0 会保留记录条数。非零日按所选指标累计值大于 0 的日期统计。点击周期可查看与上一周期的变化。",
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
    val points = remember(allPoints, rangeStart) {
        if (rangeStart == null) allPoints else allPoints.filter { it.first >= rangeStart }
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
    val referencePoints = remember(birthDate, points, referenceMetric, referenceSex, chartZone) {
        if (points.size < 2 || referenceSex == null) emptyList()
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
    val selectedAssessment = remember(selectedPoint, birthDate, referenceMetric, referenceSex) {
        selectedPoint?.let { point ->
            val measuredDate = java.time.Instant.ofEpochMilli(point.first).atZone(chartZone).toLocalDate()
            val reference = chineseGrowthReferenceAtDate(
                birthDate, measuredDate, referenceMetric, referenceSex
            )
            reference?.let { assessChineseGrowthPercentile(point.second, it) }
        }
    }
    val measurementAssessments = remember(points, birthDate, referenceMetric, referenceSex, chartZone) {
        points.map { point ->
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
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("成长曲线", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("按实际测量日期展示变化", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(latest?.let { "${"%.2f".format(Locale.getDefault(), it.second)} $unit" } ?: "暂无数据", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    if (delta != null) {
                        val sign = if (delta > 0) "+" else ""
                        Text("较上次 $sign${"%.2f".format(Locale.getDefault(), delta)} $unit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text("${"%.1f".format(Locale.getDefault(), max)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(110.dp))
                        Text("${"%.1f".format(Locale.getDefault(), min)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            drawPath(percentileBand, chartPrimary.copy(alpha = 0.09f))
                            fun percentileLine(index: Int) = Path().apply {
                                moveTo(referenceCoordinates.first()[index].x, referenceCoordinates.first()[index].y)
                                referenceCoordinates.drop(1).forEach { lineTo(it[index].x, it[index].y) }
                            }
                            listOf(1, 3).forEach { index ->
                                drawPath(
                                    percentileLine(index),
                                    chartPrimary.copy(alpha = 0.34f),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = 1.dp.toPx(),
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("━", color = chartSecondary, style = MaterialTheme.typography.titleSmall)
                            Text("宝宝测量", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("━", color = chartPrimary, style = MaterialTheme.typography.titleSmall)
                            Text("标准 P50", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("┄", color = chartPrimary.copy(alpha = 0.55f), style = MaterialTheme.typography.titleSmall)
                            Text("P25 / P75", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("▰", color = chartPrimary.copy(alpha = 0.38f), style = MaterialTheme.typography.titleSmall)
                            Text("P3–P97", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
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
                        if (points.size == 1) {
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
                    growthMeasurementComparison(points, selectedPointIndex, chartZone)?.let { comparison ->
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
                                Text("中国标准百分位区间", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(assessment.bandLabel, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("P50 参考值", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(String.format(Locale.CHINA, "%.2f %s", assessment.medianValue, unit), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Text(
                        "这是与同月龄同性别参考分布的对照，不代表健康诊断。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val previousAssessment = measurementAssessments.getOrNull(selectedPointIndex - 1)
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
                                        "中国标准 · ${assessment.bandLabel}",
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
            val longTerm = remember(allPoints) { growthMonthlySummaries(allPoints) }
            val interval = remember(allPoints) { growthMeasurementInterval(allPoints) }
            var showAllMonths by remember(allPoints) { mutableStateOf(false) }
            Spacer(Modifier.height(4.dp))
            Text("长期变化", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("按自然月汇总已有测量；没有记录的月份不会补值。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("${month.month.year}年${month.month.monthValue}月", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("${month.measurementCount} 次测量", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("${"%.2f".format(Locale.CHINA, month.latestValue)} $unit", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (month.measurementCount > 1) {
                                        "首次 ${"%.2f".format(Locale.CHINA, month.firstValue)} → 最近 ${"%.2f".format(Locale.CHINA, month.latestValue)} $unit · 间隔 ${month.spanDays} 天 · 变化 $changeSign${"%.2f".format(Locale.CHINA, month.change)} $unit"
                                    } else {
                                        month.previousValue?.let { previous ->
                                            val previousSign = if (month.latestValue > previous) "+" else ""
                                            val gap = month.daysSincePreviousMeasurement?.let { " · 间隔 ${it} 天" } ?: ""
                                            "较上次记录 $previousSign${"%.2f".format(Locale.CHINA, month.latestValue - previous)} $unit$gap"
                                        } ?: "单次记录"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
            Text("以上仅描述实际记录之间的变化，不进行生长速度预测或医学评价；请结合专业人员建议解读。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


package com.rinzaki.babylog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import kotlin.math.abs

@Composable
internal fun TrendsSummary(babyId: String, dao: BabyLogDao) {
    val context = LocalContext.current.applicationContext
    val visibleFeedingMethods = FeedingPreferences.enabledMethods(context)
    var range by remember { mutableStateOf("近7天") }
    var category by remember { mutableStateOf("全部") }
    val days = when (range) { "近7天" -> 7L; "近30天" -> 30L; "近90天" -> 90L; else -> Long.MAX_VALUE }
    val start = if (days == Long.MAX_VALUE) 0L else LocalDate.now().minusDays(days - 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
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
    val waterMl = supplementEvents.filter {
        runCatching {
            val payload = JSONObject(it.payloadJson)
            payload.optString("supplementName") == "水" && payload.optString("unit") == "ml"
        }.getOrDefault(false)
    }.sumOf { runCatching { JSONObject(it.payloadJson).optDouble("amount").toInt() }.getOrDefault(0) }
    val daysCovered = if (days == Long.MAX_VALUE) {
        calendarDaysCovered(recent, LocalDate.now(), ZoneId.systemDefault())
    } else days
    val average = recent.size.toDouble() / daysCovered.toDouble()
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("数据统计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${recent.size} 条记录", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("统计周期", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            SelectionRow(listOf("近7天", "近30天", "近90天", "全部"), range) { range = it }
            Text("记录类型", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            SelectionRow(listOf("全部", "喂养", "辅食", "补充", "护理", "睡眠", "成长"), category) { category = it }
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
            if (recent.isEmpty()) Text("这个时间范围内还没有相关记录。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
    DailyTrendChart(recent, range, category)
}

@Composable
private fun DailyTrendChart(events: List<BabyEventEntity>, range: String, category: String) {
    val availableMetrics = remember(category) { trendMetricsForCategory(category) }
    var metric by remember(category) { mutableStateOf(availableMetrics.first()) }
    val days = if (range == "全部") {
        calendarDaysCovered(events, LocalDate.now(), ZoneId.systemDefault()).coerceAtMost(366)
    } else when (range) { "近30天" -> 30; "近90天" -> 90; else -> 7 }
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
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
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
                        val horizontalPadding = 4.dp.toPx()
                        val slotWidth = (size.width - horizontalPadding * 2) / points.size.coerceAtLeast(1)
                        selectedDayIndex = ((tap.x - horizontalPadding) / slotWidth).toInt().coerceIn(points.indices)
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
                            index == selectedDayIndex -> Color(0xFFB77969)
                            point.value == 0f -> Color(0xFF8874B8).copy(alpha = 0.16f)
                            else -> Color(0xFF8874B8).copy(alpha = 0.85f)
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
                        String.format(Locale.CHINA, "%.1f", point.value) + " " + unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text("每日无记录时按 0 统计；睡眠按记录的时长合计。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun GrowthSection(baby: BabyEntity, events: List<BabyEventEntity>) {
    var metric by remember { mutableStateOf("体重") }
    var range by remember { mutableStateOf("全部") }
    val key = when (metric) { "身长" -> "lengthCm"; "头围" -> "headCircumferenceCm"; else -> "weightGrams" }
    val unit = if (metric == "体重") "kg" else "cm"
    val allPoints = remember(baby, events, key) { growthMetricPoints(baby, events, key) }
    val now = remember { System.currentTimeMillis() }
    val days = when (range) { "近 30 天" -> 30L; "近 90 天" -> 90L; "近半年" -> 182L; "近一年" -> 365L; else -> Long.MAX_VALUE }
    val points = remember(allPoints, days, now) {
        if (days == Long.MAX_VALUE) allPoints else allPoints.filter { it.first >= now - days * 86400000L }
    }
    val latest = points.lastOrNull()
    val previous = points.dropLast(1).lastOrNull()
    val delta = if (latest != null && previous != null) latest.second - previous.second else null
    val minValue = points.minOfOrNull { it.second }
    val maxValue = points.maxOfOrNull { it.second }
    val min = ((minValue ?: 0f) - if (metric == "体重") 0.25f else 1f).coerceAtLeast(0f)
    val max = (maxValue ?: 1f) + if (metric == "体重") 0.25f else 1f
    val dateFormat = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
    var selectedPointIndex by remember(points) { mutableStateOf(points.lastIndex) }
    val selectedPoint = points.getOrNull(selectedPointIndex)
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
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
            SelectionRow(listOf("近30天", "近90天", "近半年", "近一年", "全部"), range.replace("近30天","近 30 天").replace("近90天","近 90 天").replace("近半年","近半年").replace("近一年","近一年")) { range = it.replace("近30天","近 30 天").replace("近90天","近 90 天") }
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
                        Modifier.weight(1f).height(150.dp).pointerInput(points) {
                            detectTapGestures { tap ->
                                if (points.isNotEmpty()) {
                                    val start = points.first().first
                                    val span = (points.last().first - start).coerceAtLeast(1L)
                                    val left = 8f
                                    val right = size.width - 8f
                                    selectedPointIndex = points.indices.minByOrNull { index ->
                                        val x = left + (right - left) * ((points[index].first - start).toFloat() / span.toFloat())
                                        abs(x - tap.x)
                                    } ?: points.lastIndex
                                }
                            }
                        }
                    ) {
                        val left = 8f
                        val right = size.width - 8f
                        val top = 10f
                        val bottom = size.height - 12f
                        val valueSpan = (max - min).coerceAtLeast(0.1f)
                        val timeStart = points.first().first
                        val timeSpan = (points.last().first - timeStart).coerceAtLeast(1L)
                        repeat(4) { index ->
                            val y = top + (bottom - top) * index / 3f
                            drawLine(Color(0xFFEDE7F6), Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
                        }
                        val coords = points.map { point ->
                            val x = left + (right - left) * ((point.first - timeStart).toFloat() / timeSpan.toFloat())
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
                            drawPath(fillPath, Color(0xFF8874B8).copy(alpha = 0.10f))
                            val linePath = Path().apply {
                                moveTo(coords.first().x, coords.first().y)
                                for (i in 1 until coords.size) {
                                    val previousPoint = coords[i - 1]
                                    val currentPoint = coords[i]
                                    val middleX = (previousPoint.x + currentPoint.x) / 2f
                                    quadraticTo(previousPoint.x, previousPoint.y, middleX, (previousPoint.y + currentPoint.y) / 2f)
                                    lineTo(currentPoint.x, currentPoint.y)
                                }
                            }
                            drawPath(linePath, Color(0xFF8874B8), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                        }
                        coords.forEachIndexed { index, point ->
                            val isLatest = index == coords.lastIndex
                            val isSelected = index == selectedPointIndex
                            if (isSelected) drawCircle(Color(0xFFB77969).copy(alpha = 0.18f), radius = 11.dp.toPx(), center = point)
                            drawCircle(if (isLatest || isSelected) Color(0xFFB77969) else Color(0xFF8874B8), radius = if (isLatest || isSelected) 5.dp.toPx() else 3.5.dp.toPx(), center = point)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(points.first().first.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormat) }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(points.last().first.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormat) }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                selectedPoint?.let { point ->
                    Text(
                        SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(point.first)) + " · " +
                            String.format(Locale.CHINA, "%.2f", point.second) + " " + unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text("${points.size} 次测量 · 最新记录 ${SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(latest!!.first))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                points.takeLast(5).asReversed().forEach { point ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(point.first)), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${"%.2f".format(Locale.CHINA, point.second)} $unit", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            val longTerm = remember(allPoints) { growthMonthlySummaries(allPoints) }
            val interval = remember(allPoints) { growthMeasurementInterval(allPoints) }
            Spacer(Modifier.height(4.dp))
            Text("长期变化", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("按自然月汇总已有测量；没有记录的月份不会补值。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            interval?.let { observed ->
                val sign = if (observed.change > 0f) "+" else ""
                val rate = observed.changePer30Days?.let { value ->
                    val rateSign = if (value > 0f) "+" else ""
                    " · 约 $rateSign${"%.2f".format(Locale.CHINA, value)} $unit/30天"
                } ?: ""
                Text(
                    "最近两次：间隔 ${observed.elapsedDays} 天，变化 $sign${"%.2f".format(Locale.CHINA, observed.change)} $unit$rate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (longTerm.isEmpty()) {
                Text("累计测量后，这里会显示每月变化。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                longTerm.asReversed().forEach { month ->
                    val changeSign = if (month.change > 0f) "+" else ""
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${month.month.year}年${month.month.monthValue}月", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text("${month.measurementCount} 次测量", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${"%.2f".format(Locale.CHINA, month.latestValue)} $unit", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (month.measurementCount > 1) "月内 $changeSign${"%.2f".format(Locale.CHINA, month.change)} $unit" else "单次记录",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Text("以上仅描述实际记录之间的变化，不进行生长速度预测或医学评价；请结合专业人员建议解读。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


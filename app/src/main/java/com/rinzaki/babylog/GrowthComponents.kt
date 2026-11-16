package com.rinzaki.babylog

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.unit.dp
import com.rinzaki.babylog.data.BabyEntity
import com.rinzaki.babylog.data.BabyEventEntity
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@Composable
internal fun TrendsSummary(events: List<BabyEventEntity>) {
    var range by remember { mutableStateOf("近7天") }
    var category by remember { mutableStateOf("全部") }
    val days = when (range) { "近7天" -> 7L; "近30天" -> 30L; "近90天" -> 90L; else -> Long.MAX_VALUE }
    val start = if (days == Long.MAX_VALUE) 0L else LocalDate.now().minusDays(days - 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val ranged = events.filter { it.occurredAt >= start }
    val recent = ranged.filter { event ->
        when (category) {
            "喂养" -> event.eventType == "FEEDING"
            "护理" -> event.eventType == "DIAPER"
            "睡眠" -> event.eventType == "SLEEP"
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
    val daysCovered = if (days == Long.MAX_VALUE) {
        if (recent.isEmpty()) 1 else (((System.currentTimeMillis() - (recent.minOf { it.occurredAt })) / 86400000L) + 1).coerceAtLeast(1)
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
            SelectionRow(listOf("全部", "喂养", "护理", "睡眠", "成长"), category) { category = it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryPill("📈", "记录总数", "${recent.size} 条", Modifier.weight(1f))
                SummaryPill("🗓️", "日均记录", "${"%.1f".format(Locale.CHINA, average)} 条", Modifier.weight(1f))
            }
            if (category == "全部" || category == "喂养") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryPill("🍼", "记录奶量", "${milk} ml", Modifier.weight(1f))
                    SummaryPill("🧴", "喂养次数", "${feedCount} 次", Modifier.weight(1f))
                    SummaryPill("🤱", "亲喂时长", if (breastMinutes > 0) "${breastMinutes} 分钟" else "未记录", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryPill("🤱", "母乳", "${feedingMethods["母乳"] ?: 0} 次", Modifier.weight(1f))
                    SummaryPill("🍼", "配方奶", "${feedingMethods["配方奶"] ?: 0} 次", Modifier.weight(1f))
                    SummaryPill("🥛", "混合喂养", "${feedingMethods["混合喂养"] ?: 0} 次", Modifier.weight(1f))
                }
            }
            if (category == "全部" || category == "护理") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryPill("💧", "小便", "${peeCount} 次", Modifier.weight(1f))
                SummaryPill("🌿", "大便", "${stoolCount} 次", Modifier.weight(1f))
            }
            if (category == "全部" || category == "睡眠") SummaryPill("🌙", "睡眠时长", if (sleepMinutes >= 60) "${sleepMinutes / 60}小时${sleepMinutes % 60}分" else "${sleepMinutes}分钟", Modifier.fillMaxWidth())
            if (recent.isEmpty()) Text("这个时间范围内还没有相关记录。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
    DailyTrendChart(events)
}

@Composable
private fun DailyTrendChart(events: List<BabyEventEntity>) {
    var range by remember { mutableStateOf("近7天") }
    var metric by remember { mutableStateOf("喂养奶量") }
    val days = when (range) { "近30天" -> 30; "近90天" -> 90; else -> 7 }
    val unit = when (metric) {
        "喂养奶量" -> "ml"
        "睡眠小时" -> "小时"
        else -> "次"
    }
    val points = dailyTrendPoints(events, metric, days)
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
            SelectionRow(listOf("近7天", "近30天", "近90天"), range) { range = it }
            SelectionRow(listOf("喂养奶量", "喂养次数", "小便次数", "大便次数", "睡眠小时", "记录总数"), metric) { metric = it }
            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
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
                        color = Color(0xFF8874B8).copy(alpha = if (point.value == 0f) 0.16f else 0.85f),
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
    val allPoints = growthMetricPoints(baby, events, key)
    val now = System.currentTimeMillis()
    val days = when (range) { "近 30 天" -> 30L; "近 90 天" -> 90L; "近半年" -> 182L; "近一年" -> 365L; else -> Long.MAX_VALUE }
    val points = if (days == Long.MAX_VALUE) allPoints else allPoints.filter { it.first >= now - days * 86400000L }
    val latest = points.lastOrNull()
    val previous = points.dropLast(1).lastOrNull()
    val delta = if (latest != null && previous != null) latest.second - previous.second else null
    val minValue = points.minOfOrNull { it.second }
    val maxValue = points.maxOfOrNull { it.second }
    val min = ((minValue ?: 0f) - if (metric == "体重") 0.25f else 1f).coerceAtLeast(0f)
    val max = (maxValue ?: 1f) + if (metric == "体重") 0.25f else 1f
    val dateFormat = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
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
                    Canvas(Modifier.weight(1f).height(150.dp)) {
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
                            drawCircle(if (isLatest) Color(0xFFB77969) else Color(0xFF8874B8), radius = if (isLatest) 5.dp.toPx() else 3.5.dp.toPx(), center = point)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(points.first().first.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormat) }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(points.last().first.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormat) }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${points.size} 次测量 · 最新记录 ${SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(latest!!.first))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                points.takeLast(5).asReversed().forEach { point ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(point.first)), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${"%.2f".format(Locale.CHINA, point.second)} $unit", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Text("曲线按实际日期间隔绘制，仅展示已记录的数据，不代表医学评估；建议按医生建议解读。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


package com.rinzaki.babylog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rinzaki.babylog.data.BabyEventEntity
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class HomeTimelinePresentation(
    val title: String,
    val detail: String,
    val emoji: String
)

internal fun latestHomeTimelineEvents(events: List<BabyEventEntity>, limit: Int = 5): List<BabyEventEntity> =
    events.sortedByDescending { it.occurredAt }.take(limit.coerceAtLeast(0))

internal fun homeTimelinePresentation(event: BabyEventEntity): HomeTimelinePresentation {
    val payload = runCatching { JSONObject(event.payloadJson) }.getOrElse { JSONObject() }
    return when (event.eventType) {
        "FEEDING" -> {
            val amount = payload.optInt("amountMl")
            HomeTimelinePresentation("喂奶", if (amount > 0) "$amount ml" else "喂养记录", "🍼")
        }
        "DIAPER" -> {
            val details = buildList {
                if (payload.optBoolean("urine")) add("小便")
                if (payload.optBoolean("stool")) add("大便")
            }
            HomeTimelinePresentation("换尿布", details.joinToString(" · ").ifBlank { "护理记录" }, "🧷")
        }
        "SLEEP" -> {
            val minutes = payload.optInt("durationMinutes")
            val duration = when {
                minutes <= 0 -> "睡眠记录"
                minutes >= 60 -> "${minutes / 60} 小时 ${minutes % 60} 分钟"
                else -> "$minutes 分钟"
            }
            HomeTimelinePresentation("睡眠", duration, "🌙")
        }
        "GROWTH" -> {
            val parts = buildList {
                payload.optInt("weightGrams").takeIf { it > 0 }?.let { add("${"%.2f".format(Locale.getDefault(), it / 1000.0)} kg") }
                payload.optDouble("lengthCm").takeIf { it > 0.0 }?.let { add("${"%.1f".format(Locale.getDefault(), it)} cm") }
                payload.optDouble("headCircumferenceCm").takeIf { it > 0.0 }?.let { add("头围 ${"%.1f".format(Locale.getDefault(), it)} cm") }
            }
            HomeTimelinePresentation("成长测量", parts.joinToString(" · ").ifBlank { "成长数据记录" }, "📏")
        }
        "SUPPLEMENT" -> HomeTimelinePresentation("补充记录", payload.optString("supplementName").ifBlank { "营养补充" }, "🧴")
        "SOLID_FOOD" -> HomeTimelinePresentation("辅食记录", payload.optString("foodName").ifBlank { "辅食" }, "🥣")
        "MILESTONE" -> HomeTimelinePresentation("成长里程碑", payload.optString("milestoneTitle").ifBlank { "成长记录" }, "🌱")
        else -> HomeTimelinePresentation("日常记录", payload.optString("note").ifBlank { "已保存一条记录" }, "📝")
    }
}

@Composable
internal fun HomeTimelinePreview(
    events: List<BabyEventEntity>,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val latestEvents = remember(events) { latestHomeTimelineEvents(events) }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("今日时间线", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (events.isEmpty()) "记录会按发生时间整理在这里" else "最近 ${latestEvents.size} 条照护记录",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onViewAll) { Text("查看全部") }
            }
            if (latestEvents.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("🌱", style = MaterialTheme.typography.headlineSmall)
                        Text("今天还没有记录，添加第一条日常吧。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                latestEvents.forEachIndexed { index, event ->
                    val presentation = remember(event.id, event.payloadJson, event.eventType) {
                        homeTimelinePresentation(event)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                modifier = Modifier.size(38.dp),
                                shape = RoundedCornerShape(13.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(presentation.emoji, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(presentation.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(presentation.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Text(
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(event.occurredAt)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (index != latestEvents.lastIndex) {
                        Box(Modifier.padding(start = 18.dp).size(width = 2.dp, height = 8.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    }
                }
            }
        }
    }
}

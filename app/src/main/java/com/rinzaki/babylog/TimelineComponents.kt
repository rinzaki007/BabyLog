package com.rinzaki.babylog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@Composable
internal fun TimelineDayHeader(date: LocalDate, count: Int) {
    val today = LocalDate.now()
    val label = when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA))
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Text("$count 条", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun EventCard(event: BabyEventEntity, modifier: Modifier = Modifier, onEdit: () -> Unit, onDelete: () -> Unit) {
    val payload = remember(event.payloadJson) { runCatching { JSONObject(event.payloadJson) }.getOrElse { JSONObject() } }
    val (emoji, title, detail) = when (event.eventType) {
        "FEEDING" -> Triple("🍼", "喂养", feedingDescription(event.payloadJson))
        "DIAPER" -> {
            val details = mutableListOf<String>()
            if (payload.optBoolean("urine")) details.add("小便")
            if (payload.optBoolean("stool")) details.add("大便：${payload.optString("stoolShape")} · ${payload.optString("stoolColor")} · ${payload.optString("stoolAmount")}")
            Triple("🌿", "日常护理", details.joinToString("、"))
        }
        "SLEEP" -> {
            val mins = payload.optInt("durationMinutes")
            Triple("🌙", "睡眠", if (mins >= 60) "${mins / 60} 小时 ${mins % 60} 分钟" else "$mins 分钟")
        }
        "SUPPLEMENT" -> Triple("🧴", "补充记录", supplementDescription(event.payloadJson))
        "SOLID_FOOD" -> Triple("🥣", "辅食记录", solidFoodDescription(event.payloadJson))
        "MILESTONE" -> Triple("🌱", "成长里程碑", payload.optString("milestoneTitle").ifBlank { "成长记录" })
        "GROWTH" -> Triple("📏", "成长测量", listOfNotNull(
            payload.optInt("weightGrams").takeIf { it > 0 }?.let { "${"%.2f".format(Locale.getDefault(), it / 1000.0)} kg" },
            payload.optDouble("lengthCm").takeIf { it > 0 }?.let { "${"%.1f".format(Locale.getDefault(), it)} cm 身长" },
            payload.optDouble("headCircumferenceCm").takeIf { it > 0 }?.let { "${"%.1f".format(Locale.getDefault(), it)} cm 头围" }
        ).joinToString(" · "))
        else -> Triple("📝", "记录", "")
    }
    val time = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(event.occurredAt))
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(34.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(emoji, style = MaterialTheme.typography.titleMedium) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Text(
                    detail + if (payload.optString("note").isNotBlank()) " · " + payload.optString("note") else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp)) {
                    Text("编辑", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp)) {
                    Text("删除", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

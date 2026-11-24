package com.rinzaki.babylog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rinzaki.babylog.data.BabyEventEntity
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun TimelineEventDetailDialog(
    event: BabyEventEntity,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    val payload = remember(event.payloadJson) {
        runCatching { JSONObject(event.payloadJson) }.getOrElse { JSONObject() }
    }
    val (emoji, title, detail) = when (event.eventType) {
        "FEEDING" -> Triple("🍼", "喂养记录", feedingDescription(event.payloadJson))
        "DIAPER" -> {
            val details = mutableListOf<String>()
            if (payload.optBoolean("urine")) details.add("小便")
            if (payload.optBoolean("stool")) {
                details.add("大便：${payload.optString("stoolShape")} · ${payload.optString("stoolColor")} · ${payload.optString("stoolAmount")}")
            }
            Triple("🌿", "日常护理", details.joinToString("、").ifBlank { "未填写护理详情" })
        }
        "SLEEP" -> {
            val mins = payload.optInt("durationMinutes")
            Triple("🌙", "睡眠记录", if (mins >= 60) "${mins / 60} 小时 ${mins % 60} 分钟" else "$mins 分钟")
        }
        "SUPPLEMENT" -> Triple("🧴", "补充记录", supplementDescription(event.payloadJson))
        "SOLID_FOOD" -> Triple("🥣", "辅食记录", solidFoodDescription(event.payloadJson))
        "MILESTONE" -> Triple("🌱", "成长里程碑", payload.optString("milestoneTitle").ifBlank { "成长记录" })
        "TEMPERATURE" -> Triple(
            "🌡️", "体温记录",
            payload.optDouble("temperatureC").takeIf { it > 0.0 }?.let {
                "${"%.1f".format(Locale.getDefault(), it)} ℃ · ${payload.optString("measurementMethod").ifBlank { "测量" }}"
            } ?: "体温数据记录"
        )
        "GROWTH" -> Triple("📏", "成长测量", listOfNotNull(
            payload.optInt("weightGrams").takeIf { it > 0 }?.let { "${"%.2f".format(Locale.getDefault(), it / 1000.0)} kg 体重" },
            payload.optDouble("lengthCm").takeIf { it > 0 }?.let { "${"%.1f".format(Locale.getDefault(), it)} cm 身长" },
            payload.optDouble("headCircumferenceCm").takeIf { it > 0 }?.let { "${"%.1f".format(Locale.getDefault(), it)} cm 头围" }
        ).joinToString(" · ").ifBlank { "成长数据记录" })
        else -> Triple("📝", "其他记录", "记录详情")
    }
    val occurredAt = remember(event.occurredAt) {
        SimpleDateFormat("yyyy年M月d日 EEEE HH:mm", Locale.CHINA).format(Date(event.occurredAt))
    }
    val note = payload.optString("note").trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(emoji)
                Text(title, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(occurredAt, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        detail,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                if (note.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("备注", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text(note, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onEdit) { Text("编辑记录") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

package com.rinzaki.babylog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rinzaki.babylog.data.BabyEventEntity
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun TodaySummary(events: List<BabyEventEntity>) {
    val todayDate = LocalDate.now()
    val start = todayDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val end = todayDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val today = events.filter { it.occurredAt in start until end }
    val feeding = today.filter { it.eventType == "FEEDING" }
    val diapers = today.filter { it.eventType == "DIAPER" }
    val milk = feeding.sumOf {
        runCatching { JSONObject(it.payloadJson).optInt("amountMl") }.getOrDefault(0)
    }
    val pee = diapers.count { runCatching { JSONObject(it.payloadJson).optBoolean("urine") }.getOrDefault(false) }
    val stool = diapers.count { runCatching { JSONObject(it.payloadJson).optBoolean("stool") }.getOrDefault(false) }
    val sleepMinutes = today.filter { it.eventType == "SLEEP" }.sumOf {
        runCatching { JSONObject(it.payloadJson).optInt("durationMinutes") }.getOrDefault(0)
    }
    val supplements = today.filter { it.eventType == "SUPPLEMENT" }
    val waterMl = supplements.filter {
        runCatching { val payload = JSONObject(it.payloadJson); payload.optString("supplementName") == "水" && payload.optString("unit") == "ml" }.getOrDefault(false)
    }.sumOf { runCatching { JSONObject(it.payloadJson).optDouble("amount").toInt() }.getOrDefault(0) }
    val sleepLabel = if (sleepMinutes >= 60) {
        "${sleepMinutes / 60}小时${sleepMinutes % 60}分"
    } else {
        "${sleepMinutes}分钟"
    }

    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("今天的小日常", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (today.isEmpty()) "今天还没有记录，随时可以开始" else "今天已经留下了 ${today.size} 条记录",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(shape = CircleShape, color = Color(0xFFF2EDF7)) {
                    Text(
                        today.size.toString(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SummaryPill("🍼", "喂养", "${feeding.size} 次", Modifier.weight(1f))
                SummaryPill("🥛", "记录奶量", "${milk} ml", Modifier.weight(1f))
                SummaryPill("🌙", "睡眠", sleepLabel, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SummaryPill("💧", "小便", "${pee} 次", Modifier.weight(1f))
                SummaryPill("🌿", "大便", "${stool} 次", Modifier.weight(1f))
                SummaryPill("📏", "成长测量", "${today.count { it.eventType == "GROWTH" }} 次", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SummaryPill("🧴", "补充记录", "${supplements.size} 次", Modifier.weight(1f))
                SummaryPill("🚰", "饮水", "${waterMl} ml", Modifier.weight(1f))
                SummaryPill("📝", "其他记录", "${today.count { it.eventType == "OTHER" }} 次", Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun SummaryPill(emoji: String, label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().heightIn(min = 72.dp)
            .background(Color(0xFFFFF6F1), RoundedCornerShape(14.dp))
            .padding(horizontal = 4.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
    ) {
        Text(emoji, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
internal fun ActionCard(emoji: String, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 88.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(13.dp),
                color = Color(0xFFF2EDF7),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(emoji, style = MaterialTheme.typography.headlineSmall)
                }
            }
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

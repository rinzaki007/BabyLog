package com.rinzaki.babylog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rinzaki.babylog.data.BabyEventEntity
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun HomeDashboardHeader(todayDate: LocalDate, onSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("BABYLOG", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("今天也要好好记录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                todayDate.format(java.time.format.DateTimeFormatter.ofPattern("M月d日 · EEEE", java.util.Locale.CHINA)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        androidx.compose.material3.OutlinedButton(
            onClick = onSettings,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 13.dp, vertical = 7.dp)
        ) {
            Text("⚙ 设置")
        }
    }
}

@Composable
internal fun TodaySummary(events: List<BabyEventEntity>, onSelect: (String) -> Unit = {}) {
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
    val solidFoods = today.filter { it.eventType == "SOLID_FOOD" }
    val waterMl = supplements.filter {
        runCatching { val payload = JSONObject(it.payloadJson); payload.optString("supplementName") == "水" && payload.optString("unit") == "ml" }.getOrDefault(false)
    }.sumOf { runCatching { JSONObject(it.payloadJson).optDouble("amount").toInt() }.getOrDefault(0) }
    val sleepLabel = if (sleepMinutes >= 60) {
        "${sleepMinutes / 60}小时${sleepMinutes % 60}分"
    } else {
        "${sleepMinutes}分钟"
    }

    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
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
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
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
                SummaryPill("🍼", "喂养", "${feeding.size} 次", Modifier.weight(1f)) { onSelect("喂养") }
                SummaryPill("🥛", "记录奶量", "${milk} ml", Modifier.weight(1f)) { onSelect("喂养") }
                SummaryPill("🌙", "睡眠", sleepLabel, Modifier.weight(1f)) { onSelect("睡眠") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SummaryPill("💧", "小便", "${pee} 次", Modifier.weight(1f)) { onSelect("大小便") }
                SummaryPill("🌿", "大便", "${stool} 次", Modifier.weight(1f)) { onSelect("大小便") }
                SummaryPill("📏", "成长测量", "${today.count { it.eventType == "GROWTH" }} 次", Modifier.weight(1f)) { onSelect("成长测量") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SummaryPill("🧴", "补充记录", "${supplements.size} 次", Modifier.weight(1f)) { onSelect("补充记录") }
                SummaryPill("🚰", "饮水", "${waterMl} ml", Modifier.weight(1f)) { onSelect("补充记录") }
                SummaryPill("🥣", "辅食", "${solidFoods.size} 次", Modifier.weight(1f)) { onSelect("辅食记录") }
            }
        }
    }
}

@Composable
internal fun SummaryPill(emoji: String, label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().heightIn(min = 72.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(13.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
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


@Composable
internal fun HomeSyncStatusCard(
    syncLabel: String,
    lastSyncLabel: String,
    syncDetailLabel: String,
    syncConfigured: Boolean,
    syncing: Boolean,
    showDetails: Boolean,
    consistency: String?,
    syncError: String?,
    pendingCount: Int,
    conflictCount: Int,
    onToggleDetails: () -> Unit,
    onSyncNow: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleDetails)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusColor = when {
                    !syncError.isNullOrBlank() -> MaterialTheme.colorScheme.error
                    syncing -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.secondary
                }
                Box(Modifier.size(8.dp).background(statusColor, CircleShape))
                Text(
                    syncLabel,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 9.dp).weight(1f)
                )
                Text(
                    lastSyncLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (showDetails) "⌃" else "⌄",
                    modifier = Modifier.padding(start = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (showDetails) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (syncing) "正在交换本地与云端数据…" else "自动同步已开启",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                syncDetailLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            enabled = syncConfigured && !syncing,
                            onClick = onSyncNow
                        ) {
                            Text(if (syncing) "同步中" else "立即同步")
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "待同步 $pendingCount 项",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "冲突 $conflictCount 项",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (conflictCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (conflictCount > 0) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "本地与云端校验",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            consistency ?: "尚未校验",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (consistency == "一致") MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (!syncError.isNullOrBlank()) {
                        Text(
                            "同步提示：$syncError",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

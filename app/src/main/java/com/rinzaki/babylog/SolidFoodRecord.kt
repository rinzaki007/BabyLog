package com.rinzaki.babylog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rinzaki.babylog.data.BabyEventEntity
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val solidFoodPresets = listOf("米粉", "蔬菜泥", "水果泥", "肉泥", "蛋黄", "其他")
val solidFoodUnits = listOf("g", "勺", "ml", "口")
val solidFoodStatuses = listOf("正常", "少量尝试", "未吃完", "拒绝")
val solidFoodCategories = listOf("未分类", "谷物", "蔬菜", "水果", "肉蛋鱼", "蛋奶", "其他")
val solidFoodStages = listOf("首次尝试", "再次尝试", "日常食用", "未标记")
val solidFoodReactions = listOf("待观察", "未见明显异常", "出现不适", "未记录")

internal data class SolidFoodJourneyEntry(
    val name: String,
    val category: String,
    val firstTriedAt: Long,
    val latestAt: Long,
    val attempts: Int,
    val latestStage: String,
    val latestReaction: String
)

internal fun solidFoodJourney(events: List<BabyEventEntity>): List<SolidFoodJourneyEntry> {
    val grouped = linkedMapOf<String, MutableList<Pair<BabyEventEntity, JSONObject>>>()
    events.forEach { event ->
        if (event.eventType != "SOLID_FOOD" || event.deletedAt != null) return@forEach
        val payload = runCatching { JSONObject(event.payloadJson) }.getOrNull() ?: return@forEach
        val name = payload.optString("foodName").trim()
        if (name.isBlank()) return@forEach
        grouped.getOrPut(name) { mutableListOf() }.add(event to payload)
    }
    return grouped.map { (name, records) ->
        records.sortBy { it.first.occurredAt }
        val first = records.first()
        val latest = records.last()
        SolidFoodJourneyEntry(
            name = name,
            category = latest.second.optString("foodCategory").ifBlank { "未分类" },
            firstTriedAt = first.first.occurredAt,
            latestAt = latest.first.occurredAt,
            attempts = records.size,
            latestStage = latest.second.optString("foodStage").ifBlank { "未标记" },
            latestReaction = latest.second.optString("foodReaction").ifBlank { "未记录" }
        )
    }.sortedByDescending { it.latestAt }
}

fun solidFoodDescription(payloadJson: String): String {
    val payload = runCatching { JSONObject(payloadJson) }.getOrElse { JSONObject() }
    val name = payload.optString("foodName").ifBlank { "辅食" }
    val amount = payload.optDouble("amount", Double.NaN)
    val unit = payload.optString("unit")
    val quantity = if (amount.isFinite() && amount > 0.0 && unit.isNotBlank()) {
        val number = if (amount % 1.0 == 0.0) amount.toInt().toString() else amount.toString()
        " · $number $unit"
    } else ""
    val status = payload.optString("eatingStatus")
    val stage = payload.optString("foodStage")
    val reaction = payload.optString("foodReaction")
    return buildString {
        append(name)
        append(quantity)
        if (status.isNotBlank() && status != "正常") append(" · ").append(status)
        if (stage.isNotBlank() && stage != "未标记") append(" · ").append(stage)
        if (reaction.isNotBlank() && reaction != "未记录") append(" · ").append(reaction)
        val reactionNote = payload.optString("foodReactionNote").trim()
        if (reactionNote.isNotBlank()) append(" · ").append(reactionNote)
    }
}

@Composable
internal fun SolidFoodJourneySection(events: List<BabyEventEntity>) {
    val entries = remember(events) { solidFoodJourney(events) }
    val totalAttempts = remember(entries) { entries.sumOf { it.attempts } }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("辅食探索记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("记录尝试过的食物与观察情况", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${entries.size} 种 · $totalAttempts 次", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            if (entries.isEmpty()) {
                Text("还没有辅食尝试记录。添加第一条后，这里会逐步形成宝宝的食物探索清单。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                entries.take(6).forEach { entry ->
                    val firstDate = Instant.ofEpochMilli(entry.firstTriedAt).atZone(ZoneId.systemDefault())
                        .toLocalDate().format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(entry.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${entry.category} · 首次尝试 $firstDate · ${entry.attempts} 次",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            entry.latestReaction,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (entry.latestReaction == "出现不适") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (entries.size > 6) {
                    Text("最近展示 6 种，完整记录可在「记录」时间线中查看。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("这里只整理家庭观察记录，不作过敏或营养诊断；如有明显不适，请及时咨询专业人员。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

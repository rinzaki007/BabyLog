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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rinzaki.babylog.data.BabyEntity
import com.rinzaki.babylog.data.BabyEventEntity
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class GrowthMilestone(
    val id: String,
    val title: String,
    val category: String,
    val minAgeMonths: Int,
    val maxAgeMonths: Int
) {
    val ageRange: String
        get() = "约 ${minAgeMonths}–${maxAgeMonths} 个月"
}

internal val growthMilestones = listOf(
    GrowthMilestone("social_smile", "出现有意识的社交微笑", "互动", 1, 3),
    GrowthMilestone("head_control", "趴着时能短暂抬头", "大运动", 1, 4),
    GrowthMilestone("reach_grab", "主动伸手触碰或抓握玩具", "精细动作", 3, 6),
    GrowthMilestone("roll_over", "完成自主翻身", "大运动", 4, 7),
    GrowthMilestone("sit", "能够独坐一会儿", "大运动", 6, 9),
    GrowthMilestone("babble", "出现重复音节或丰富咿呀声", "语言", 6, 10),
    GrowthMilestone("crawl", "开始以自己的方式移动或爬行", "大运动", 7, 12),
    GrowthMilestone("pull_to_stand", "能够扶物站立", "大运动", 8, 12),
    GrowthMilestone("first_steps", "迈出独立行走的步伐", "大运动", 10, 18),
    GrowthMilestone("first_words", "开始有意义地说出单词", "语言", 10, 18),
    GrowthMilestone("run", "能够独立跑起来", "大运动", 18, 24),
    GrowthMilestone("two_word", "开始把两个词组合表达", "语言", 18, 30),
    GrowthMilestone("jump", "尝试双脚离地跳跃", "大运动", 24, 36)
)

internal fun completedGrowthMilestones(events: List<BabyEventEntity>): Map<String, BabyEventEntity> {
    val completed = linkedMapOf<String, BabyEventEntity>()
    events.forEach { event ->
        if (event.eventType != "MILESTONE" || event.deletedAt != null) return@forEach
        val id = runCatching { JSONObject(event.payloadJson).optString("milestoneId") }.getOrDefault("")
        if (id.isBlank()) return@forEach
        val previous = completed[id]
        if (previous == null || event.occurredAt > previous.occurredAt) completed[id] = event
    }
    return completed
}

internal fun growthAgeMonths(birthTime: Long, today: LocalDate, zone: ZoneId): Long {
    val birthDate = Instant.ofEpochMilli(birthTime).atZone(zone).toLocalDate()
    return growthCompletedMonths(birthDate, today)
}

/** Includes milestones in the current age window and those beginning within the next three months. */
internal fun growthMilestonesForCurrentStage(
    milestones: List<GrowthMilestone>,
    ageMonths: Long
): List<GrowthMilestone> = milestones.filter { milestone ->
    ageMonths in milestone.minAgeMonths.toLong()..milestone.maxAgeMonths.toLong() ||
        (milestone.minAgeMonths > ageMonths && milestone.minAgeMonths - ageMonths <= 3L)
}

@Composable
internal fun GrowthMilestonesSection(
    baby: BabyEntity,
    events: List<BabyEventEntity>,
    onRecord: (GrowthMilestone) -> Unit
) {
    val completed = remember(events) { completedGrowthMilestones(events) }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val ageMonths = remember(baby.birthTime, today) { growthAgeMonths(baby.birthTime, today, zone) }
    val ageLabel = growthMeasurementAgeLabel(
        baby.birthTime,
        today.atStartOfDay(zone).toInstant().toEpochMilli(),
        zone
    )
    var filter by remember(baby.id) { mutableStateOf("当前阶段") }
    val visibleMilestones = when (filter) {
        "已记录" -> growthMilestones.filter { it.id in completed }
        "待记录" -> growthMilestones.filter { it.id !in completed }
        "全部" -> growthMilestones
        else -> growthMilestonesForCurrentStage(growthMilestones, ageMonths)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("成长里程碑", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("宝宝当前 $ageLabel · 记录值得纪念的第一次", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${completed.size}/${growthMilestones.size}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf("当前阶段", "全部", "待记录", "已记录").forEach { option ->
                    TextButton(
                        onClick = { filter = option },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            option,
                            color = if (filter == option) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (filter == option) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
            if (filter == "当前阶段") {
                Text(
                    "显示当前月龄范围内，以及未来 3 个月可以留意的成长瞬间。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (visibleMilestones.isEmpty()) {
                Text(
                    if (filter == "当前阶段") "这个月龄暂时没有对应条目，可以切换「全部」浏览成长清单。"
                    else "这里还没有相关记录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                visibleMilestones.forEach { milestone ->
                    val event = completed[milestone.id]
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(milestone.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            val detail = if (event == null) {
                                "${milestone.ageRange} · ${milestone.category}"
                            } else {
                                val date = Instant.ofEpochMilli(event.occurredAt).atZone(zone)
                                    .toLocalDate().format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))
                                val ageAtEvent = growthMeasurementAgeLabel(baby.birthTime, event.occurredAt, zone)
                                "${milestone.ageRange} · ${milestone.category} · ${date}（${ageAtEvent}）已记录"
                            }
                            Text(detail, style = MaterialTheme.typography.labelSmall, color = if (event == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                        }
                        if (event == null) {
                            TextButton(onClick = { onRecord(milestone) }) { Text("记录") }
                        } else {
                            Text("✓ 已记录", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Text(
                "年龄范围仅作回忆记录参考，不是发育标准或筛查结论；每个宝宝的成长节奏都不同。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rinzaki.babylog.data.BabyEntity
import com.rinzaki.babylog.data.BabyEventEntity
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class GrowthMilestone(
    val id: String,
    val title: String,
    val ageRange: String,
    val minAgeMonths: Int,
    val maxAgeMonths: Int,
    val category: String
)

internal val growthMilestones = listOf(
    GrowthMilestone("social_smile", "出现有意识的社交微笑", "约 1–3 个月", 1, 3, "互动"),
    GrowthMilestone("head_control", "趴着时能短暂抬头", "约 1–4 个月", 1, 4, "大运动"),
    GrowthMilestone("reach_grab", "主动伸手触碰或抓握玩具", "约 3–6 个月", 3, 6, "精细动作"),
    GrowthMilestone("roll_over", "完成自主翻身", "约 4–7 个月", 4, 7, "大运动"),
    GrowthMilestone("sit", "能够独坐一会儿", "约 6–9 个月", 6, 9, "大运动"),
    GrowthMilestone("babble", "出现重复音节或丰富咿呀声", "约 6–10 个月", 6, 10, "语言"),
    GrowthMilestone("crawl", "开始以自己的方式移动或爬行", "约 7–12 个月", 7, 12, "大运动"),
    GrowthMilestone("pull_to_stand", "能够扶物站立", "约 8–12 个月", 8, 12, "大运动"),
    GrowthMilestone("first_steps", "迈出独立行走的步伐", "约 10–18 个月", 10, 18, "大运动"),
    GrowthMilestone("first_words", "开始有意义地说出单词", "约 10–18 个月", 10, 18, "语言"),
    GrowthMilestone("run", "能够独立跑起来", "约 18–24 个月", 18, 24, "大运动"),
    GrowthMilestone("two_word", "开始把两个词组合表达", "约 18–30 个月", 18, 30, "语言"),
    GrowthMilestone("jump", "尝试双脚离地跳跃", "约 24–36 个月", 24, 36, "大运动")
)

internal fun completedGrowthMilestones(events: List<BabyEventEntity>): Map<String, BabyEventEntity> =
    events.asSequence()
        .filter { it.eventType == "MILESTONE" && it.deletedAt == null }
        .mapNotNull { event ->
            val id = runCatching { JSONObject(event.payloadJson).optString("milestoneId") }
                .getOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            id to event
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, matches) -> matches.maxBy { it.occurredAt } }

internal fun babyAgeMonths(
    birthTime: Long,
    today: LocalDate = LocalDate.now(),
    zoneId: ZoneId = ZoneId.systemDefault()
): Long {
    val birthDate = runCatching {
        Instant.ofEpochMilli(birthTime).atZone(zoneId).toLocalDate()
    }.getOrDefault(today)
    return ChronoUnit.MONTHS.between(birthDate, today).coerceAtLeast(0)
}

@Composable
internal fun GrowthMilestonesSection(
    baby: BabyEntity,
    events: List<BabyEventEntity>,
    onRecord: (GrowthMilestone) -> Unit
) {
    val completed = remember(events) { completedGrowthMilestones(events) }
    val ageMonths = remember(baby.birthTime) { babyAgeMonths(baby.birthTime) }
    val ageLabel = if (ageMonths < 1) "未满 1 个月" else "$ageMonths 个月"
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA) }

    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("成长里程碑", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("宝宝当前 $ageLabel · 记录值得纪念的第一次", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${completed.size}/${growthMilestones.size}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            growthMilestones.forEach { milestone ->
                val event = completed[milestone.id]
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(milestone.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${milestone.ageRange} · ${milestone.category}" + (event?.let {
                                val date = Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()
                                " · ${date.format(formatter)} 已记录"
                            } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (event != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (event == null) TextButton(onClick = { onRecord(milestone) }) { Text("记录") }
                    else Text("✓ 已记录", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
            Text("年龄范围仅作回忆记录参考，不是发育标准或筛查结论；每个宝宝的成长节奏都不同。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

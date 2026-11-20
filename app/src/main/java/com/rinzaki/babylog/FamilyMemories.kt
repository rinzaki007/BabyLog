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
import androidx.compose.ui.Modifier
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

internal data class FamilyMemoryMoment(
    val title: String,
    val date: LocalDate,
    val daysRemaining: Long,
    val subtitle: String
)

internal fun familyMemoryMoments(birthDate: LocalDate, today: LocalDate): List<FamilyMemoryMoment> {
    val candidates = buildList {
        val hundredDays = birthDate.plusDays(100)
        if (!hundredDays.isBefore(today)) add(Triple("百日纪念日", hundredDays, "出生第 100 天"))
        val firstBirthday = birthDate.plusYears(1)
        if (!firstBirthday.isBefore(today)) add(Triple("一周岁", firstBirthday, "第一个生日"))
        val thousandDays = birthDate.plusDays(1000)
        if (!thousandDays.isBefore(today)) add(Triple("千日纪念日", thousandDays, "出生第 1000 天"))
        // Keep future birthdays available beyond the first ten years of use.
        val elapsedYears = ChronoUnit.YEARS.between(birthDate, today).coerceAtLeast(0L)
        var nextBirthdayYear = maxOf(1L, elapsedYears)
        while (runCatching { birthDate.plusYears(nextBirthdayYear).isBefore(today) }.getOrDefault(true)) {
            nextBirthdayYear += 1L
        }
        for (year in nextBirthdayYear..(nextBirthdayYear + 2L)) {
            val birthday = runCatching { birthDate.plusYears(year) }.getOrNull() ?: continue
            add(Triple(if (year == 1L) "一周岁" else "$year 周岁", birthday, "生日"))
        }
    }
    return candidates
        .distinctBy { it.second to it.first }
        .sortedBy { it.second }
        .take(3)
        .map { (title, date, subtitle) ->
            FamilyMemoryMoment(title, date, ChronoUnit.DAYS.between(today, date), subtitle)
        }
}

internal data class FamilyMemoryRecap(
    val milestoneCount: Int,
    val latestMilestones: List<Pair<String, LocalDate>>
)

internal fun familyMemoryRecap(events: List<BabyEventEntity>, zone: ZoneId, limit: Int = 3): FamilyMemoryRecap {
    require(limit in 1..20)
    val milestones = events.asSequence()
        .filter { it.eventType == "MILESTONE" && it.deletedAt == null }
        .mapNotNull { event ->
            val title = runCatching { JSONObject(event.payloadJson).optString("milestoneTitle").trim() }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            title to Instant.ofEpochMilli(event.occurredAt).atZone(zone).toLocalDate()
        }
        .toList()
    return FamilyMemoryRecap(
        milestoneCount = milestones.size,
        latestMilestones = milestones.sortedByDescending { it.second }.take(limit)
    )
}

@Composable
internal fun FamilyMemorySection(baby: BabyEntity, milestoneEvents: List<BabyEventEntity>) {
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zone)
    val birthDate = remember(baby.birthTime, zone) {
        Instant.ofEpochMilli(baby.birthTime).atZone(zone).toLocalDate()
    }
    val moments = remember(birthDate, today) { familyMemoryMoments(birthDate, today) }
    val recap = remember(milestoneEvents) { familyMemoryRecap(milestoneEvents, zone) }
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("家庭纪念日", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (moments.isEmpty()) {
                Text("新的纪念日即将到来，记得留下照片和故事。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                moments.forEach { moment ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(moment.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("${moment.date.format(formatter)} · ${moment.subtitle}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            if (moment.daysRemaining == 0L) "就是今天" else "${moment.daysRemaining} 天后",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Text("成长回顾", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("已经记录 ${recap.milestoneCount} 个成长里程碑", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (recap.latestMilestones.isEmpty()) {
                Text("记录第一次翻身、爬行或开口说话，这些瞬间会一直留在这里。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                recap.latestMilestones.forEach { (title, date) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("🌱 $title", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(date.format(formatter), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text("纪念日根据出生日期自动计算；成长回顾只整理已保存的里程碑，不会补写或推测记录。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

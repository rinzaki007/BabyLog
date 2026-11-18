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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rinzaki.babylog.data.BabyEntity
import com.rinzaki.babylog.data.BabyLogDao

@Composable
internal fun GrowthMilestonesSection(
    baby: BabyEntity,
    dao: BabyLogDao,
    onRecord: (GrowthMilestone) -> Unit
) {
    val eventsFlow = remember(baby.id) { dao.observeEventsByType(baby.id, "MILESTONE") }
    val events by eventsFlow.collectAsState(initial = emptyList())
    val completed = remember(events) { GrowthMilestoneCatalog.completed(events) }
    val ageMonths = remember(baby.birthTime) { GrowthMilestoneCatalog.ageMonths(baby.birthTime) }
    val ageLabel = if (ageMonths < 1) "未满 1 个月" else "$ageMonths 个月"
    val milestones = GrowthMilestoneCatalog.milestones

    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("成长里程碑", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("宝宝当前 $ageLabel · 记录值得纪念的第一次", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${completed.size}/${milestones.size}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            for (milestone in milestones) {
                val event = completed[milestone.id]
                val statusText = if (event == null) "${milestone.ageRange} · ${milestone.category}"
                    else "${milestone.ageRange} · ${milestone.category} · ${GrowthMilestoneCatalog.formatRecordedDate(event.occurredAt)} 已记录"
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(milestone.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(statusText, style = MaterialTheme.typography.labelSmall,
                            color = if (event != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (event == null) TextButton(onClick = { onRecord(milestone) }) { Text("记录") }
                    else Text("✓ 已记录", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
            Text("年龄范围仅作回忆记录参考，不是发育标准或筛查结论；每个宝宝的成长节奏都不同。",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

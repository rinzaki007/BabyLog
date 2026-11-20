package com.rinzaki.babylog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rinzaki.babylog.data.BabyEventEntity
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

@Composable
internal fun VaccineScheduleSection(
    birthDate: LocalDate,
    sex: String?,
    events: List<BabyEventEntity>,
    onAddRecord: (String, String) -> Unit
) {
    var showAll by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("待接种") }
    var showCatalog by remember { mutableStateOf(false) }
    val records = remember(events) {
        events.sortedBy { it.occurredAt }.mapNotNull { event ->
            runCatching {
                val payload = JSONObject(event.payloadJson)
                payload.optString("vaccineName") to payload.optString("dose")
            }.getOrNull()
        }
    }
    val jeInactivated = records.any { it.first.contains("乙脑") && it.first.contains("灭活") }
    val hepAInactivated = records.any { it.first.contains("甲肝") && it.first.contains("灭活") }
    val allItems = remember(birthDate, sex, jeInactivated, hepAInactivated) {
        val national = chinaNationalVaccineSchedule(birthDate, sex).filter { (item, _) ->
            when (item.group) {
                "乙脑" -> if (jeInactivated) item.vaccineName.contains("灭活") else item.vaccineName.contains("减毒活")
                "甲肝" -> if (hepAInactivated) item.vaccineName.contains("灭活") else item.vaccineName.contains("减毒活")
                else -> true
            }
        }
        national + chinaSelfPaidVaccineSchedule(birthDate)
    }
    val today = LocalDate.now()
    val decorated = allItems.map { (item, date) ->
        Triple(item, date, vaccineDoseStatus(item, records))
    }.sortedBy { it.second }
    val pending = decorated.filter { it.third != VaccineDoseStatus.RECORDED }
    val completedCount = decorated.count { it.third == VaccineDoseStatus.RECORDED }
    val reviewCount = decorated.count { it.third == VaccineDoseStatus.REVIEW_REQUIRED }
    val filtered = when (selectedCategory) {
        "待接种" -> pending
        "全部" -> decorated
        else -> decorated.filter { it.first.category == selectedCategory }
    }
    val visible = if (showAll) filtered else {
        if (selectedCategory == "待接种") filtered.take(8)
        else (filtered.filter { it.third != VaccineDoseStatus.RECORDED }.take(8) +
            filtered.filter { it.third == VaccineDoseStatus.RECORDED }.takeLast(4)).distinct().sortedBy { it.second }
    }
    val recordedConflicts = remember(events) {
        recordedLiveVaccineConflicts(events.mapNotNull { event ->
            runCatching {
                val name = JSONObject(event.payloadJson).optString("vaccineName")
                if (name.isBlank()) null else name to java.time.Instant.ofEpochMilli(event.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()
            }.getOrNull()
        })
    }
    val conflicts = remember(allItems, recordedConflicts) { (liveVaccineScheduleConflicts(allItems) + recordedConflicts).distinct() }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("接种计划 · 倒计时", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("按类别查看计划剂次；联合疫苗成分不自动抵扣", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("已接种 $completedCount/${decorated.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            SelectionRow(listOf("待接种", "全部", "国家免规", "自费常见", "流脑系列", "联合疫苗"), selectedCategory) {
                selectedCategory = it
                showAll = false
            }
            if (reviewCount > 0) {
                Text(
                    "有 $reviewCount 项可能受到联合疫苗或流脑替代产品影响，暂不自动抵扣，也不标记为逾期。请携带具体疫苗名称、剂次和接种日期向接种门诊核对。",
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            conflicts.forEach { warning ->
                Text("⚠ 接种间隔提示：$warning。部分注射用减毒活疫苗通常需同日接种或间隔至少 28 天，请让接种门诊核对具体产品和日期。",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (visible.isEmpty()) {
                Text(if (selectedCategory == "待接种") "当前没有待记录剂次，太棒啦！" else "这个分类下暂时没有计划项目。", color = MaterialTheme.colorScheme.primary)
            } else {
                visible.forEach { (item, date, doseStatus) ->
                    val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
                    val status = when (doseStatus) {
                        VaccineDoseStatus.RECORDED -> "已接种"
                        VaccineDoseStatus.REVIEW_REQUIRED -> "待门诊核对"
                        VaccineDoseStatus.PENDING -> when {
                            days < 0 -> "逾期 ${-days} 天"
                            days == 0L -> "今天接种"
                            else -> "还有 ${days} 天"
                        }
                    }
                    val statusColor = when (doseStatus) {
                        VaccineDoseStatus.RECORDED -> MaterialTheme.colorScheme.secondary
                        VaccineDoseStatus.REVIEW_REQUIRED -> MaterialTheme.colorScheme.tertiary
                        VaccineDoseStatus.PENDING -> when {
                            days <= 0 -> MaterialTheme.colorScheme.error
                            days <= 14 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }
                    }
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(item.vaccineName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text(item.category, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                            }
                            Text("${item.dose} · $date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (item.note.isNotBlank()) Text(item.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(status, color = statusColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            when (doseStatus) {
                                VaccineDoseStatus.PENDING -> TextButton(onClick = { onAddRecord(item.vaccineName, item.dose) }) {
                                    Text("＋ 记录", style = MaterialTheme.typography.labelMedium)
                                }
                                VaccineDoseStatus.REVIEW_REQUIRED -> Text(
                                    "核对成分",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor
                                )
                                VaccineDoseStatus.RECORDED -> Unit
                            }
                        }
                    }
                }
            }
            if (filtered.size > visible.size || showAll) {
                TextButton(onClick = { showAll = !showAll }, modifier = Modifier.align(Alignment.End)) {
                    Text(if (showAll) "收起计划" else "查看本分类全部 ${filtered.size} 项")
                }
            }
            TextButton(onClick = { showCatalog = !showCatalog }, modifier = Modifier.align(Alignment.Start)) {
                Text(if (showCatalog) "收起其他疫苗参考" else "了解其他可选疫苗（不自动倒计时）")
            }
            if (showCatalog) {
                chinaOptionalVaccineCatalog().groupBy { it.category }.forEach { (category, entries) ->
                    Text(category, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    entries.forEach { entry ->
                        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(10.dp)) {
                            Text(entry.name, fontWeight = FontWeight.SemiBold)
                            Text(entry.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Text("自费疫苗提醒仅作参考。不同厂家、价型和既往接种史可能改变程序；联合疫苗的成分抵扣及替代关系请由接种门诊核对。",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun VaccinePage(events: List<BabyEventEntity>, birthDate: LocalDate, sex: String?, onSave: (BabyEventEntity?, JSONObject, Long, (Boolean) -> Unit) -> Unit, onDelete: (BabyEventEntity) -> Unit) {
    var showEditor by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<BabyEventEntity?>(null) }
    var editing by remember { mutableStateOf<BabyEventEntity?>(null) }
    var editorInitialName by remember { mutableStateOf("") }
    var editorInitialDose by remember { mutableStateOf("") }
    var visibleHistoryCount by remember { mutableStateOf(6) }
    var showHistory by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("疫苗接种档案", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("独立管理宝宝每一次接种，方便长期查询和核对。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(
            onClick = {
                editing = null
                editorInitialName = ""
                editorInitialDose = ""
                showEditor = true
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("＋ 添加疫苗接种记录") }
        VaccineScheduleSection(birthDate, sex, events) { vaccineName, dose ->
            editing = null
            editorInitialName = vaccineName
            editorInitialDose = dose
            showEditor = true
        }
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("💉 宝宝疫苗档案", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("已记录 " + events.size + " 次接种", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("接种安排请以当地接种机构和医生提供的计划为准。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (events.isEmpty()) {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💉", style = MaterialTheme.typography.headlineLarge)
                    Text("还没有疫苗接种记录", fontWeight = FontWeight.SemiBold)
                    Text("完成接种后，记录疫苗名称、日期和剂次。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("接种历史", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("共 ${events.size} 条记录", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { showHistory = !showHistory }) {
                    Text(if (showHistory) "收起历史" else "展开历史")
                }
            }
            if (showHistory) events.sortedByDescending { it.occurredAt }.take(visibleHistoryCount).forEach { event ->
                val data = remember(event.payloadJson) { runCatching { JSONObject(event.payloadJson) }.getOrElse { JSONObject() } }
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("💉", style = MaterialTheme.typography.titleLarge)
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(data.optString("vaccineName", "疫苗接种"), fontWeight = FontWeight.Bold)
                                Text(SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()).format(Date(event.occurredAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(data.optString("dose"), color = MaterialTheme.colorScheme.primary)
                        }
                        listOfNotNull(
                            data.optString("clinic").takeIf { it.isNotBlank() }?.let { "接种机构：" + it },
                            data.optString("site").takeIf { it.isNotBlank() }?.let { "接种部位：" + it },
                            data.optString("batchNumber").takeIf { it.isNotBlank() }?.let { "疫苗批号：" + it },
                            data.optString("note").takeIf { it.isNotBlank() }?.let { "备注：" + it }
                        ).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {
                                editing = event
                                editorInitialName = ""
                                editorInitialDose = ""
                                showEditor = true
                            }) { Text("编辑") }
                            TextButton(onClick = { deleting = event }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            if (showHistory && events.size > visibleHistoryCount) {
                OutlinedButton(
                    onClick = { visibleHistoryCount = (visibleHistoryCount + 6).coerceAtMost(events.size) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("加载更多接种记录（已显示 ${visibleHistoryCount}/${events.size} 条）") }
            }
        }
    }
    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除这条接种记录？") },
            text = { Text("删除后会从本机接种档案中隐藏；如果已启用家庭同步，也会同步删除。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target)
                    deleting = null
                }) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }
        )
    }
    if (showEditor) VaccineEditor(
        editing,
        initialName = editorInitialName,
        initialDose = editorInitialDose,
        saving = saving,
        onDismiss = { if (!saving) showEditor = false }
    ) { payload, timestamp ->
        if (!saving) {
            saving = true
            onSave(editing, payload, timestamp) { saved ->
                saving = false
                if (saved) showEditor = false
            }
        }
    }
}


@Composable
internal fun VaccineEditor(existing: BabyEventEntity?, initialName: String = "", initialDose: String = "", saving: Boolean = false, onDismiss: () -> Unit, onSave: (JSONObject, Long) -> Unit) {
    val data = remember(existing?.id, initialName, initialDose) { runCatching { JSONObject(existing?.payloadJson ?: "{}") }.getOrElse { JSONObject() } }
    var name by remember(existing?.id, initialName) { mutableStateOf(data.optString("vaccineName").ifBlank { initialName }) }
    var dose by remember(existing?.id, initialDose) { mutableStateOf(data.optString("dose").ifBlank { initialDose }) }
    var clinic by remember(existing?.id) { mutableStateOf(data.optString("clinic")) }
    var site by remember(existing?.id) { mutableStateOf(data.optString("site")) }
    var batch by remember(existing?.id) { mutableStateOf(data.optString("batchNumber")) }
    var note by remember(existing?.id) { mutableStateOf(data.optString("note")) }
    var date by remember(existing?.id) { mutableStateOf(existing?.let { java.time.Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate() } ?: LocalDate.now()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDateTimePicker by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "添加疫苗记录" else "编辑疫苗记录", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(60) }, Modifier.fillMaxWidth(), label = { Text("疫苗名称 *") }, singleLine = true)
                OutlinedButton(onClick = { showDateTimePicker = true }, modifier = Modifier.fillMaxWidth()) { Text("接种日期：" + date) }
                OutlinedTextField(dose, { dose = it.take(30) }, Modifier.fillMaxWidth(), label = { Text("剂次（如：第 1 剂）") }, singleLine = true)
                OutlinedTextField(clinic, { clinic = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("接种机构") }, singleLine = true)
                OutlinedTextField(site, { site = it.take(40) }, Modifier.fillMaxWidth(), label = { Text("接种部位") }, singleLine = true)
                OutlinedTextField(batch, { batch = it.take(50) }, Modifier.fillMaxWidth(), label = { Text("疫苗批号（可选）") }, singleLine = true)
                OutlinedTextField(note, { note = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("备注（可选）") }, minLines = 2)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (saving) return@Button
                if (name.isBlank()) { error = "请填写疫苗名称"; return@Button }
                val payload = JSONObject().put("vaccineName", name.trim()).put("dose", dose.trim())
                    .put("clinic", clinic.trim()).put("site", site.trim()).put("batchNumber", batch.trim()).put("note", note.trim())
                onSave(payload, date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            }, enabled = !saving) { Text(if (saving) "保存中…" else "保存记录") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
    if (showDateTimePicker) DateTimeOptionDialog(
        initial = date.atStartOfDay(), includeTime = false,
        onDismiss = { showDateTimePicker = false },
        onConfirm = { date = it.toLocalDate(); showDateTimePicker = false }
    )
}


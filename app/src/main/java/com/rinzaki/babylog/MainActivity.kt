package com.rinzaki.babylog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.room.Room
import com.rinzaki.babylog.data.BabyEntity
import com.rinzaki.babylog.data.BabyEventEntity
import com.rinzaki.babylog.data.BabyLogDao
import com.rinzaki.babylog.data.BabyLogDatabase
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.UUID

private val WarmColors = lightColorScheme(
    primary = Color(0xFF7564A8),
    onPrimary = Color.White,
    secondary = Color(0xFFB77969),
    background = Color(0xFFFFF9F5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF2EDF7),
    onSurface = Color(0xFF302B38),
    onSurfaceVariant = Color(0xFF716B78)
)

class MainActivity : ComponentActivity() {
    private lateinit var database: BabyLogDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = Room.databaseBuilder(applicationContext, BabyLogDatabase::class.java, "babylog.db").build()
        setContent { BabyLogApp(database.babyLogDao()) }
    }

    override fun onDestroy() {
        if (::database.isInitialized) database.close()
        super.onDestroy()
    }
}

@Composable
private fun BabyLogApp(dao: BabyLogDao) {
    val babies by dao.observeBabies().collectAsState(initial = emptyList())
    val baby = babies.firstOrNull()
    val eventsFlow = remember(baby?.id) {
        if (baby == null) flowOf(emptyList()) else dao.observeEvents(baby.id)
    }
    val events by eventsFlow.collectAsState(initial = emptyList())
    var editingProfile by remember { mutableStateOf(false) }
    var addingEvent by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    MaterialTheme(colorScheme = WarmColors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("BABYLOG", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text("成长的每一天", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("把细碎日常，变成温柔的回忆", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(shape = CircleShape, color = Color(0xFFEDE7F6), modifier = Modifier.size(54.dp)) {
                            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                Text("♡", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                item {
                    if (baby == null || editingProfile) {
                        BabyProfileEditor(existing = baby, onSave = { name, birthDate ->
                            val now = System.currentTimeMillis()
                            val birthTime = birthDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            dao.upsertBaby(BabyEntity(
                                id = baby?.id ?: UUID.randomUUID().toString(),
                                familyId = baby?.familyId ?: "00000000-0000-0000-0000-000000000000",
                                displayName = name.trim(), birthTime = birthTime,
                                createdAt = baby?.createdAt ?: now, updatedAt = now
                            ))
                            editingProfile = false
                        })
                    } else BabyProfileCard(baby, onEdit = { editingProfile = true })
                }
                if (baby != null) {
                    item { TodaySummary(events) }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("快速记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ActionCard("🍼", "配方奶", "记录每次奶量", Modifier.weight(1f)) { addingEvent = "FEEDING" }
                                ActionCard("🧷", "尿布", "尿液 / 大便", Modifier.weight(1f)) { addingEvent = "DIAPER" }
                                ActionCard("🌙", "睡眠", "记录睡眠时长", Modifier.weight(1f)) { addingEvent = "SLEEP" }
                            }
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("最近记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("共 ${events.size} 条", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (events.isEmpty()) {
                        item {
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF2EDF7))) {
                                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("🌱", style = MaterialTheme.typography.headlineLarge)
                                    Text("第一条记录，等你来写", fontWeight = FontWeight.SemiBold)
                                    Text("从一次喂养、一片尿布或一段好觉开始。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    } else {
                        items(events, key = { it.id }) { event ->
                            EventCard(event) { scope.launch { dao.softDeleteEvent(event.id, System.currentTimeMillis()) } }
                        }
                    }
                } else {
                    item { Text("创建宝宝档案后，就可以开始记录每天的喂养、尿布和睡眠。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                item {
                    Text("本地优先保存 · 家庭共享与云同步将在后续版本接入", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (addingEvent != null && baby != null) {
        EventEditor(eventType = addingEvent!!, onDismiss = { addingEvent = null }, onSave = { type, payload, occurredAt ->
            val now = System.currentTimeMillis()
            scope.launch {
                dao.upsertEvent(BabyEventEntity(
                    id = UUID.randomUUID().toString(), babyId = baby.id, eventType = type,
                    occurredAt = occurredAt, payloadJson = payload.toString(), createdBy = null, updatedAt = now
                ))
                addingEvent = null
            }
        })
    }
}

@Composable
private fun BabyProfileEditor(existing: BabyEntity?, onSave: suspend (String, LocalDate) -> Unit) {
    var name by remember(existing?.id) { mutableStateOf(existing?.displayName.orEmpty()) }
    var birthDate by remember(existing?.id) {
        mutableStateOf(existing?.let { java.time.Instant.ofEpochMilli(it.birthTime).atZone(ZoneId.systemDefault()).toLocalDate().toString() }.orEmpty())
    }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (existing == null) "建立宝宝档案" else "编辑宝宝档案", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("先认识一下今天的小主角吧。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(name, { name = it.take(30); error = null }, Modifier.fillMaxWidth(), label = { Text("宝宝昵称") }, singleLine = true)
            OutlinedTextField(
                birthDate, { birthDate = it.take(10); error = null }, Modifier.fillMaxWidth(),
                label = { Text("出生日期") }, placeholder = { Text("例如：2026-11-14") },
                supportingText = { Text("格式：YYYY-MM-DD") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                val parsed = runCatching { LocalDate.parse(birthDate.trim()) }.getOrNull()
                when {
                    name.isBlank() -> error = "请填写宝宝昵称"
                    parsed == null -> error = "出生日期格式不正确，请使用 YYYY-MM-DD"
                    parsed.isAfter(LocalDate.now()) -> error = "出生日期不能晚于今天"
                    else -> {
                        saving = true
                        scope.launch { try { onSave(name, parsed) } finally { saving = false } }
                    }
                }
            }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(if (saving) "保存中…" else "保存档案") }
        }
    }
}

@Composable
private fun BabyProfileCard(baby: BabyEntity, onEdit: () -> Unit) {
    val birthDate = java.time.Instant.ofEpochMilli(baby.birthTime).atZone(ZoneId.systemDefault()).toLocalDate()
    val days = java.time.temporal.ChronoUnit.DAYS.between(birthDate, LocalDate.now()).coerceAtLeast(0) + 1
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE7F6))) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(58.dp)) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { Text("👶", style = MaterialTheme.typography.headlineMedium) }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(baby.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("第 $days 天 · $birthDate", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onEdit) { Text("编辑") }
        }
    }
}

@Composable
private fun TodaySummary(events: List<BabyEventEntity>) {
    val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val end = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val today = events.filter { it.occurredAt in start until end }
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("今天的小日常", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${today.size} 条记录", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryPill("🍼", "喂养", today.count { it.eventType == "FEEDING" }, Modifier.weight(1f))
                SummaryPill("🧷", "尿布", today.count { it.eventType == "DIAPER" }, Modifier.weight(1f))
                SummaryPill("🌙", "睡眠", today.count { it.eventType == "SLEEP" }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryPill(emoji: String, label: String, count: Int, modifier: Modifier = Modifier) {
    Column(modifier.background(Color(0xFFFFF5F0), RoundedCornerShape(16.dp)).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji)
        Text("$count", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionCard(emoji: String, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(emoji, style = MaterialTheme.typography.headlineSmall)
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EventEditor(eventType: String, onDismiss: () -> Unit, onSave: (String, JSONObject, Long) -> Unit) {
    val now = remember { LocalDateTime.now() }
    var date by remember { mutableStateOf(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))) }
    var time by remember { mutableStateOf(now.format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var amount by remember { mutableStateOf("") }
    var urine by remember { mutableStateOf(true) }
    var stool by remember { mutableStateOf(false) }
    var stoolShape by remember { mutableStateOf("糊状") }
    var stoolColor by remember { mutableStateOf("黄色") }
    var stoolAmount by remember { mutableStateOf("中量") }
    var sleepMinutes by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val title = when (eventType) { "FEEDING" -> "记录配方奶"; "DIAPER" -> "记录大小便"; else -> "记录睡眠" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("发生时间", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(date, { date = it.take(10) }, Modifier.weight(1.2f), label = { Text("日期") }, placeholder = { Text("yyyy-MM-dd") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
                    OutlinedTextField(time, { time = it.take(5) }, Modifier.weight(0.8f), label = { Text("时间") }, placeholder = { Text("HH:mm") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
                }
                when (eventType) {
                    "FEEDING" -> {
                        Text("配方奶奶量", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(amount, { amount = it.filter(Char::isDigit).take(4); error = null }, Modifier.fillMaxWidth(), label = { Text("本次喝了多少") }, suffix = { Text("ml") }, placeholder = { Text("例如：120") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    }
                    "DIAPER" -> {
                        Text("小便记录", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (urine) FilledTonalButton(onClick = { urine = false }) { Text("✓ 有小便") } else OutlinedButton(onClick = { urine = true }) { Text("有小便") }
                            if (stool) FilledTonalButton(onClick = { stool = false }) { Text("✓ 有大便") } else OutlinedButton(onClick = { stool = true }) { Text("有大便") }
                        }
                        if (stool) {
                            Text("大便性状", style = MaterialTheme.typography.labelLarge)
                            SelectionRow(listOf("稀水样", "糊状", "软便", "成形", "干硬"), stoolShape) { stoolShape = it }
                            Text("大便颜色", style = MaterialTheme.typography.labelLarge)
                            SelectionRow(listOf("黄色", "金黄色", "绿色", "棕色", "灰白色", "黑色", "红色", "其他"), stoolColor) { stoolColor = it }
                            Text("大便量", style = MaterialTheme.typography.labelLarge)
                            SelectionRow(listOf("少量", "中量", "大量"), stoolAmount) { stoolAmount = it }
                        }
                    }
                    "SLEEP" -> OutlinedTextField(sleepMinutes, { sleepMinutes = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), label = { Text("睡眠时长（分钟）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                }
                OutlinedTextField(note, { note = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("备注（可选）") }, minLines = 1, maxLines = 2)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val timestamp = runCatching {
                    LocalDateTime.parse("$date $time", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }.getOrNull()
                if (timestamp == null) { error = "请检查日期和时间格式"; return@Button }
                val payload = JSONObject()
                when (eventType) {
                    "FEEDING" -> {
                        val ml = amount.toIntOrNull()
                        if (ml == null || ml <= 0) { error = "请输入有效奶量（毫升）"; return@Button }
                        payload.put("method", "配方奶").put("amountMl", ml)
                    }
                    "DIAPER" -> {
                        if (!urine && !stool) { error = "请至少选择小便或大便"; return@Button }
                        payload.put("urine", urine).put("stool", stool)
                        if (stool) payload.put("stoolShape", stoolShape).put("stoolColor", stoolColor).put("stoolAmount", stoolAmount)
                    }
                    "SLEEP" -> {
                        val minutes = sleepMinutes.toIntOrNull()
                        if (minutes == null || minutes <= 0) { error = "请填写有效睡眠时长"; return@Button }
                        payload.put("durationMinutes", minutes)
                    }
                }
                if (note.isNotBlank()) payload.put("note", note.trim())
                onSave(eventType, payload, timestamp)
            }) { Text("保存记录") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun SelectionRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        options.chunked(4).forEach { group ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                group.forEach { option ->
                    if (selected == option) FilledTonalButton(onClick = { onSelect(option) }, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 6.dp)) { Text(option) }
                    else OutlinedButton(onClick = { onSelect(option) }, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 6.dp)) { Text(option) }
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: BabyEventEntity, onDelete: () -> Unit) {
    val payload = remember(event.payloadJson) { runCatching { JSONObject(event.payloadJson) }.getOrElse { JSONObject() } }
    val (emoji, title, detail) = when (event.eventType) {
        "FEEDING" -> Triple("🍼", "配方奶", "${payload.optInt("amountMl")} ml")
        "DIAPER" -> {
            val details = mutableListOf<String>()
            if (payload.optBoolean("urine")) details.add("小便")
            if (payload.optBoolean("stool")) details.add("大便：${payload.optString("stoolShape", "未记录")} · ${payload.optString("stoolColor", "未记录")} · ${payload.optString("stoolAmount", "未记录")}")
            Triple("🧷", "大小便", details.joinToString("、"))
        }
        "SLEEP" -> {
            val minutes = payload.optInt("durationMinutes")
            Triple("🌙", "睡眠", if (minutes >= 60) "${minutes / 60} 小时 ${minutes % 60} 分钟" else "$minutes 分钟")
        }
        else -> Triple("📝", "记录", "")
    }
    val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(event.occurredAt))
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = Color(0xFFF2EDF7), modifier = Modifier.size(42.dp)) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { Text(emoji) }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail + if (payload.optString("note").isNotBlank()) " · " + payload.optString("note") else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
        }
    }
}

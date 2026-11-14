package com.rinzaki.babylog

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.room.Room
import com.rinzaki.babylog.data.BabyEntity
import com.rinzaki.babylog.data.BabyEventEntity
import com.rinzaki.babylog.data.BabyPhotoEntity
import com.rinzaki.babylog.data.BabyLogDao
import com.rinzaki.babylog.data.BabyLogDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.io.File
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path

private val WarmColors = lightColorScheme(
    primary = Color(0xFF7564A8), onPrimary = Color.White,
    secondary = Color(0xFFB77969), background = Color(0xFFFFF9F5),
    surface = Color.White, surfaceVariant = Color(0xFFF2EDF7),
    onSurface = Color(0xFF302B38), onSurfaceVariant = Color(0xFF716B78)
)

class MainActivity : ComponentActivity() {
    private lateinit var database: BabyLogDatabase
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = Room.databaseBuilder(applicationContext, BabyLogDatabase::class.java, "babylog.db")
            .addMigrations(BabyLogDatabase.MIGRATION_1_2, BabyLogDatabase.MIGRATION_2_3).build()
        setContent { BabyLogApp(database.babyLogDao()) }
    }
    override fun onDestroy() {
        if (::database.isInitialized) database.close()
        super.onDestroy()
    }
}

@Composable
private fun BabyLogApp(dao: BabyLogDao) {
    val appContext = LocalContext.current.applicationContext
    val babies by dao.observeBabies().collectAsState(initial = emptyList())
    val baby = babies.firstOrNull()
    val eventsFlow = remember(baby?.id) { if (baby == null) flowOf(emptyList()) else dao.observeEvents(baby.id) }
    val events by eventsFlow.collectAsState(initial = emptyList())
    val photosFlow = remember(baby?.id) { if (baby == null) flowOf(emptyList()) else dao.observePhotos(baby.id) }
    val photos by photosFlow.collectAsState(initial = emptyList())
    var editingProfile by remember { mutableStateOf(false) }
    var addingEvent by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    MaterialTheme(colorScheme = WarmColors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Column {
                        Text("BABYLOG", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("成长的每一天", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("把细碎日常，变成温柔的回忆", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    if (baby == null || editingProfile) {
                        BabyProfileEditor(baby) { name, birthDate, sex, avatar, weight, length, head ->
                            val now = System.currentTimeMillis()
                            val birthTime = birthDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            scope.launch {
                                dao.upsertBaby(BabyEntity(
                                    id = baby?.id ?: UUID.randomUUID().toString(),
                                    familyId = baby?.familyId ?: "00000000-0000-0000-0000-000000000000",
                                    displayName = name.trim(), birthTime = birthTime,
                                    createdAt = baby?.createdAt ?: now, updatedAt = now,
                                    sex = sex, avatarUri = avatar, birthWeightGrams = weight,
                                    birthLengthCm = length, birthHeadCircumferenceCm = head
                                ))
                                editingProfile = false
                            }
                        }
                    } else BabyProfileCard(baby) { editingProfile = true }
                }
                if (baby != null) {
                    item { TodaySummary(events) }
                    item { GrowthSection(baby, events) }
                    item { BabyAlbumSection(baby, photos) { uri ->
                        scope.launch {
                            runCatching {
                                val folder = File(appContext.filesDir, "baby_album").apply { mkdirs() }
                                val destination = File(folder, "${UUID.randomUUID()}.jpg")
                                withContext(Dispatchers.IO) {
                                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                                        destination.outputStream().use { output -> input.copyTo(output) }
                                    } ?: error("无法读取所选照片")
                                }
                                val now = System.currentTimeMillis()
                                dao.upsertPhoto(BabyPhotoEntity(UUID.randomUUID().toString(), baby.id, destination.absolutePath, null, now, now, now))
                            }
                        }
                    } }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("快捷记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ActionCard("🍼", "喂养", "奶量与时间", Modifier.weight(1f)) { addingEvent = "FEEDING" }
                                ActionCard("🌿", "日常护理", "大小便情况", Modifier.weight(1f)) { addingEvent = "DIAPER" }
                                ActionCard("🌙", "睡眠", "休息时长", Modifier.weight(1f)) { addingEvent = "SLEEP" }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ActionCard("📏", "成长记录", "体重变化", Modifier.weight(1f)) { addingEvent = "GROWTH" }
                                Spacer(Modifier.weight(2f))
                            }
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("成长足迹", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("共 ${events.size} 条", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (events.isEmpty()) {
                        item {
                            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF2EDF7))) {
                                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("🌱", style = MaterialTheme.typography.headlineLarge)
                                    Text("第一条记录，等你来写", fontWeight = FontWeight.SemiBold)
                                    Text("从一次喂养、一段好觉或一次成长测量开始。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else items(events, key = { it.id }) { event ->
                        EventCard(event) { scope.launch { dao.softDeleteEvent(event.id, System.currentTimeMillis()) } }
                    }
                } else item { Text("创建宝宝档案后，就可以开始记录每天的生活点滴。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { Text("照片与记录目前保存在本机 · 家庭共享与云同步将在后续阶段接入", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }

    if (addingEvent != null && baby != null) {
        EventEditor(addingEvent!!, onDismiss = { addingEvent = null }) { type, payload, occurredAt ->
            val now = System.currentTimeMillis()
            scope.launch {
                dao.upsertEvent(BabyEventEntity(UUID.randomUUID().toString(), baby.id, type, occurredAt, payload.toString(), null, now))
                addingEvent = null
            }
        }
    }
}


@Composable
private fun GrowthSection(baby: BabyEntity, events: List<BabyEventEntity>) {
    val points = events.filter { it.eventType == "GROWTH" }.mapNotNull { event ->
        val grams = runCatching { JSONObject(event.payloadJson).optInt("weightGrams") }.getOrDefault(0)
        if (grams > 0) event.occurredAt to grams / 1000f else null
    }.sortedBy { it.first }
    val latest = points.lastOrNull()
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("成长曲线", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("体重变化记录", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(latest?.let { "${"%.2f".format(Locale.getDefault(), it.second)} kg" } ?: "暂无数据", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            if (points.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(112.dp), contentAlignment = Alignment.Center) {
                    Text("记录几次体重后，这里就会出现成长曲线", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                val min = (points.minOf { it.second } - 0.3f).coerceAtLeast(0f)
                val max = points.maxOf { it.second } + 0.3f
                Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                    val left = 12f
                    val right = size.width - 12f
                    val top = 12f
                    val bottom = size.height - 12f
                    repeat(4) { i ->
                        val y = top + (bottom - top) * i / 3f
                        drawLine(Color(0xFFEDE7F6), Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
                    }
                    val span = (max - min).coerceAtLeast(0.1f)
                    val coords = points.mapIndexed { index, point ->
                        val x = if (points.size == 1) (left + right) / 2f else left + (right - left) * index / (points.size - 1)
                        val y = bottom - (point.second - min) / span * (bottom - top)
                        Offset(x, y)
                    }
                    if (coords.size > 1) {
                        val path = Path().apply {
                            moveTo(coords.first().x, coords.first().y)
                            coords.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(path, Color(0xFF8874B8), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
                    }
                    coords.forEach { drawCircle(Color(0xFF8874B8), radius = 4.dp.toPx(), center = it) }
                }
                Text("${points.size} 次测量 · 最近记录 ${SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(latest!!.first))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("曲线仅展示已记录的数据，不代表医学评估。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BabyAlbumSection(baby: BabyEntity, photos: List<BabyPhotoEntity>, onAdd: (android.net.Uri) -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onAdd(uri)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("成长相册", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("记录每一个值得珍藏的日子", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { picker.launch(arrayOf("image/*")) }) { Text("添加照片") }
        }
        if (photos.isEmpty()) {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5F0))) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("📷", style = MaterialTheme.typography.headlineLarge)
                    Text("收藏宝宝的第一张成长照片", fontWeight = FontWeight.SemiBold)
                    Text("照片按拍摄记录时间排列，后续可同步到家庭相册。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            photos.forEach { photo ->
                val day = (java.time.temporal.ChronoUnit.DAYS.between(
                    java.time.Instant.ofEpochMilli(baby.birthTime).atZone(ZoneId.systemDefault()).toLocalDate(),
                    java.time.Instant.ofEpochMilli(photo.takenAt).atZone(ZoneId.systemDefault()).toLocalDate()
                ) + 1).coerceAtLeast(1)
                val milestone = when (day) { 1L -> "初见"; 7L -> "第一周"; 30L -> "满月"; 100L -> "百日"; 180L -> "半岁"; 365L -> "一周岁"; else -> "第 ${day} 天" }
                AlbumPhotoCard(photo, milestone)
            }
        }
    }
}

@Composable
private fun AlbumPhotoCard(photo: BabyPhotoEntity, milestone: String) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, photo.localPath) {
        value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(photo.localPath) }.getOrNull() }
    }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (bitmap != null) Image(bitmap!!.asImageBitmap(), contentDescription = milestone, modifier = Modifier.size(104.dp))
            else Box(Modifier.size(104.dp).background(Color(0xFFF2EDF7), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text("📷") }
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(milestone, fontWeight = FontWeight.Bold)
                Text(SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()).format(Date(photo.takenAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("成长瞬间", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun BabyProfileEditor(
    existing: BabyEntity?,
    onSave: suspend (String, LocalDate, String?, String?, Int?, Double?, Double?) -> Unit
) {
    val context = LocalContext.current
    var name by remember(existing?.id) { mutableStateOf(existing?.displayName.orEmpty()) }
    var birthDate by remember(existing?.id) { mutableStateOf(existing?.let { java.time.Instant.ofEpochMilli(it.birthTime).atZone(ZoneId.systemDefault()).toLocalDate() } ?: LocalDate.now()) }
    var sex by remember(existing?.id) { mutableStateOf(existing?.sex ?: "未设置") }
    var avatar by remember(existing?.id) { mutableStateOf(existing?.avatarUri) }
    var birthWeight by remember(existing?.id) { mutableStateOf((existing?.birthWeightGrams ?: 0).toFloat()) }
    var birthLength by remember(existing?.id) { mutableStateOf((existing?.birthLengthCm ?: 0.0).toFloat()) }
    var birthHead by remember(existing?.id) { mutableStateOf((existing?.birthHeadCircumferenceCm ?: 0.0).toFloat()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            avatar = uri.toString()
        }
    }
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text(if (existing == null) "建立宝宝档案" else "编辑宝宝档案", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(avatar, 68)
                Column(Modifier.weight(1f)) {
                    Text("宝宝头像", fontWeight = FontWeight.SemiBold)
                    Text("选择一张照片作为档案头像", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }) { Text("上传图片") }
            }
            OutlinedTextField(name, { name = it.take(30) }, Modifier.fillMaxWidth(), label = { Text("宝宝昵称") }, singleLine = true)
            Text("性别", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            SelectionRow(listOf("男宝", "女宝", "未设置"), sex) { sex = it }
            Text("出生日期", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = {
                DatePickerDialog(context, { _, y, m, d -> birthDate = LocalDate.of(y, m + 1, d) },
                    birthDate.year, birthDate.monthValue - 1, birthDate.dayOfMonth).show()
            }, modifier = Modifier.fillMaxWidth()) { Text(birthDate.toString()) }
            Text("出生体重  ${if (birthWeight <= 0f) "未记录" else "${birthWeight.toInt()} g"}", fontWeight = FontWeight.SemiBold)
            Slider(birthWeight, { birthWeight = it }, valueRange = 0f..5000f, steps = 99)
            Text("出生身长  ${if (birthLength <= 0f) "未记录" else "${"%.1f".format(Locale.getDefault(), birthLength)} cm"}", fontWeight = FontWeight.SemiBold)
            Slider(birthLength, { birthLength = it }, valueRange = 0f..60f, steps = 119)
            Text("出生头围  ${if (birthHead <= 0f) "未记录" else "${"%.1f".format(Locale.getDefault(), birthHead)} cm"}", fontWeight = FontWeight.SemiBold)
            Slider(birthHead, { birthHead = it }, valueRange = 0f..45f, steps = 89)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                when {
                    name.isBlank() -> error = "请填写宝宝昵称"
                    birthDate.isAfter(LocalDate.now()) -> error = "出生日期不能晚于今天"
                    else -> {
                        saving = true
                        scope.launch {
                            try { onSave(name, birthDate, sex.takeIf { it != "未设置" }, avatar, birthWeight.toInt().takeIf { it > 0 }, birthLength.toDouble().takeIf { it > 0 }, birthHead.toDouble().takeIf { it > 0 }) }
                            finally { saving = false }
                        }
                    }
                }
            }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(if (saving) "保存中…" else "保存档案") }
        }
    }
}

@Composable
private fun Avatar(uri: String?, size: Int) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, uri) {
        value = if (uri.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) } }.getOrNull()
        }
    }
    Surface(shape = CircleShape, color = Color(0xFFEDE7F6), modifier = Modifier.size(size.dp)) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), contentDescription = "宝宝头像", modifier = Modifier.fillMaxSize())
        else Box(contentAlignment = Alignment.Center) { Text("👶", style = MaterialTheme.typography.headlineMedium) }
    }
}

@Composable
private fun BabyProfileCard(baby: BabyEntity, onEdit: () -> Unit) {
    val birthDate = java.time.Instant.ofEpochMilli(baby.birthTime).atZone(ZoneId.systemDefault()).toLocalDate()
    val days = java.time.temporal.ChronoUnit.DAYS.between(birthDate, LocalDate.now()).coerceAtLeast(0) + 1
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE7F6))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(baby.avatarUri, 62)
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(baby.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${baby.sex ?: "宝宝"} · 第 $days 天", style = MaterialTheme.typography.bodySmall)
                Text("出生体重 ${baby.birthWeightGrams?.let { "$it g" } ?: "未记录"} · $birthDate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(listOfNotNull(baby.birthLengthCm?.let { "身长 ${"%.1f".format(Locale.getDefault(), it)} cm" }, baby.birthHeadCircumferenceCm?.let { "头围 ${"%.1f".format(Locale.getDefault(), it)} cm" }).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val milk = today.filter { it.eventType == "FEEDING" }.sumOf {
        runCatching { JSONObject(it.payloadJson).optInt("amountMl") }.getOrDefault(0)
    }
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("今天的小日常", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${today.size} 条记录", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryPill("🍼", "喂养次数", today.count { it.eventType == "FEEDING" }.toString(), Modifier.weight(1f))
                SummaryPill("🥛", "奶量合计", "$milk ml", Modifier.weight(1f))
                SummaryPill("🌿", "护理", today.count { it.eventType == "DIAPER" }.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryPill(emoji: String, label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Color(0xFFFFF5F0), RoundedCornerShape(16.dp)).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionCard(emoji: String, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(emoji, style = MaterialTheme.typography.headlineSmall)
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EventEditor(eventType: String, onDismiss: () -> Unit, onSave: (String, JSONObject, Long) -> Unit) {
    val context = LocalContext.current
    val now = remember { LocalDateTime.now() }
    var date by remember { mutableStateOf(now.toLocalDate()) }
    var hour by remember { mutableStateOf(now.hour) }
    var minute by remember { mutableStateOf(now.minute) }
    var amount by remember { mutableStateOf(120f) }
    var urine by remember { mutableStateOf(true) }
    var stool by remember { mutableStateOf(false) }
    var stoolShape by remember { mutableStateOf("糊状") }
    var stoolColor by remember { mutableStateOf("黄色") }
    var stoolAmount by remember { mutableStateOf("中量") }
    var sleepMinutes by remember { mutableStateOf(60f) }
    var weightKg by remember { mutableStateOf(5f) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val title = when (eventType) { "FEEDING" -> "喂养记录"; "DIAPER" -> "日常护理"; "SLEEP" -> "睡眠记录"; else -> "成长记录" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("记录时间", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                    }, modifier = Modifier.weight(1f)) { Text(date.toString()) }
                    OutlinedButton(onClick = {
                        TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, true).show()
                    }, modifier = Modifier.weight(1f)) { Text("%02d:%02d".format(hour, minute)) }
                }
                when (eventType) {
                    "FEEDING" -> {
                        Text("本次奶量  ${amount.toInt()} ml", fontWeight = FontWeight.SemiBold)
                        Slider(amount, { amount = it }, valueRange = 30f..300f, steps = 26)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(60f, 90f, 120f, 150f, 180f, 210f).forEach { v ->
                                if (amount.toInt() == v.toInt()) FilledTonalButton(onClick = { amount = v }, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp)) { Text(v.toInt().toString()) }
                                else OutlinedButton(onClick = { amount = v }, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp)) { Text(v.toInt().toString()) }
                            }
                        }
                        Text("单位：毫升（ml）", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    "DIAPER" -> {
                        Text("护理情况", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (urine) FilledTonalButton(onClick = { urine = false }) { Text("✓ 有小便") } else OutlinedButton(onClick = { urine = true }) { Text("有小便") }
                            if (stool) FilledTonalButton(onClick = { stool = false }) { Text("✓ 有大便") } else OutlinedButton(onClick = { stool = true }) { Text("有大便") }
                        }
                        if (stool) {
                            Text("大便性状"); SelectionRow(listOf("稀水样", "糊状", "软便", "成形", "干硬"), stoolShape) { stoolShape = it }
                            Text("颜色"); SelectionRow(listOf("黄色", "金黄色", "绿色", "棕色", "灰白色", "黑色", "红色", "其他"), stoolColor) { stoolColor = it }
                            Text("大便量"); SelectionRow(listOf("少量", "中量", "大量"), stoolAmount) { stoolAmount = it }
                        }
                    }
                    "SLEEP" -> {
                        Text("睡眠时长  ${sleepMinutes.toInt()} 分钟", fontWeight = FontWeight.SemiBold)
                        Slider(sleepMinutes, { sleepMinutes = it }, valueRange = 10f..720f, steps = 70)
                    }
                    "GROWTH" -> {
                        Text("当前体重  ${"%.2f".format(Locale.getDefault(), weightKg)} kg", fontWeight = FontWeight.SemiBold)
                        Slider(weightKg, { weightKg = it }, valueRange = 2f..20f, steps = 179)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(3f, 4f, 5f, 6f, 8f, 10f).forEach { v ->
                                if (weightKg == v) FilledTonalButton(onClick = { weightKg = v }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("$v") }
                                else OutlinedButton(onClick = { weightKg = v }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("$v") }
                            }
                        }
                    }
                }
                OutlinedTextField(note, { note = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("备注（可选）") }, minLines = 1, maxLines = 2)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val timestamp = date.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val payload = JSONObject()
                when (eventType) {
                    "FEEDING" -> payload.put("method", "配方奶").put("amountMl", amount.toInt())
                    "DIAPER" -> {
                        if (!urine && !stool) { error = "请至少选择一项护理情况"; return@Button }
                        payload.put("urine", urine).put("stool", stool)
                        if (stool) payload.put("stoolShape", stoolShape).put("stoolColor", stoolColor).put("stoolAmount", stoolAmount)
                    }
                    "SLEEP" -> payload.put("durationMinutes", sleepMinutes.toInt())
                    "GROWTH" -> payload.put("weightGrams", (weightKg * 1000).toInt())
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
        "FEEDING" -> Triple("🍼", "喂养", "${payload.optInt("amountMl")} ml 配方奶")
        "DIAPER" -> {
            val details = mutableListOf<String>()
            if (payload.optBoolean("urine")) details.add("小便")
            if (payload.optBoolean("stool")) details.add("大便：${payload.optString("stoolShape")} · ${payload.optString("stoolColor")} · ${payload.optString("stoolAmount")}")
            Triple("🌿", "日常护理", details.joinToString("、"))
        }
        "SLEEP" -> {
            val mins = payload.optInt("durationMinutes")
            Triple("🌙", "睡眠", if (mins >= 60) "${mins / 60} 小时 ${mins % 60} 分钟" else "$mins 分钟")
        }
        "GROWTH" -> Triple("📏", "体重记录", "${"%.2f".format(Locale.getDefault(), payload.optInt("weightGrams") / 1000.0)} kg")
        else -> Triple("📝", "记录", "")
    }
    val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(event.occurredAt))
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = Color(0xFFF2EDF7), modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(emoji) }
            }
            Column(Modifier.weight(1f).padding(start = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail + if (payload.optString("note").isNotBlank()) " · " + payload.optString("note") else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
        }
    }
}

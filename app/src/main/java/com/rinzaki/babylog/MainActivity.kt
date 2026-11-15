package com.rinzaki.babylog

import android.graphics.BitmapFactory
import android.os.Bundle
import android.content.ContentValues
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.Room
import com.rinzaki.babylog.data.BabyEntity
import com.rinzaki.babylog.data.BabyEventEntity
import com.rinzaki.babylog.data.BabyPhotoEntity
import com.rinzaki.babylog.data.BabyLogDao
import com.rinzaki.babylog.data.BabyLogDatabase
import com.rinzaki.babylog.data.SyncOperationEntity
import com.rinzaki.babylog.sync.SyncPreferences
import com.rinzaki.babylog.sync.SyncScheduler
import com.rinzaki.babylog.sync.SyncSettingsDialog
import com.rinzaki.babylog.sync.babySyncPayload
import com.rinzaki.babylog.sync.eventSyncPayload
import com.rinzaki.babylog.sync.photoSyncPayload
import com.rinzaki.babylog.sync.mimeTypeForPath
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

private val BabyLogTypography = Typography().copy(
    displayLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp),
    displayMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp),
    displaySmall = TextStyle(fontSize = 26.sp, lineHeight = 32.sp),
    headlineLarge = TextStyle(fontSize = 24.sp, lineHeight = 30.sp),
    headlineMedium = TextStyle(fontSize = 22.sp, lineHeight = 28.sp),
    headlineSmall = TextStyle(fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp)
)

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
            .addMigrations(BabyLogDatabase.MIGRATION_1_2, BabyLogDatabase.MIGRATION_2_3, BabyLogDatabase.MIGRATION_3_4, BabyLogDatabase.MIGRATION_4_5)
            .enableMultiInstanceInvalidation().build()
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
    var showSyncSettings by remember { mutableStateOf(false) }
    var addingEvent by remember { mutableStateOf<String?>(null) }
    var editingEvent by remember { mutableStateOf<BabyEventEntity?>(null) }
    var deletingEvent by remember { mutableStateOf<BabyEventEntity?>(null) }
    var showSyncDetails by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pageListStates = listOf(rememberLazyListState(), rememberLazyListState(), rememberLazyListState(), rememberLazyListState(), rememberLazyListState())
    var lastSyncAt by remember { mutableStateOf(SyncPreferences.lastSyncAt(appContext)) }
    var syncError by remember { mutableStateOf(SyncPreferences.lastSyncError(appContext)) }
    var syncing by remember { mutableStateOf(SyncPreferences.isSyncing(appContext)) }
    var syncConsistency by remember { mutableStateOf(SyncPreferences.lastConsistency(appContext)) }
    var selectedEventFilter by remember { mutableStateOf("全部") }
    val vaccineEvents = events.filter { it.eventType == "VACCINE" }
    val normalEvents = events.filter { it.eventType != "VACCINE" }
    val filteredEvents = normalEvents.filter { event ->
        when (selectedEventFilter) {
            "喂养" -> event.eventType == "FEEDING"
            "大小便" -> event.eventType == "DIAPER"
            "睡眠" -> event.eventType == "SLEEP"
            "成长测量" -> event.eventType == "GROWTH"
            "其他" -> event.eventType !in setOf("FEEDING", "DIAPER", "SLEEP", "GROWTH")
            else -> true
        }
    }
    LaunchedEffect(Unit) {
        if (SyncPreferences.familyId(appContext).isNotBlank()) SyncScheduler.schedule(appContext)
        var lastForegroundSync = 0L
        while (true) {
            lastSyncAt = SyncPreferences.lastSyncAt(appContext)
            syncError = SyncPreferences.lastSyncError(appContext)
            syncing = SyncPreferences.isSyncing(appContext)
            syncConsistency = SyncPreferences.lastConsistency(appContext)
            val now = System.currentTimeMillis()
            if (SyncPreferences.familyId(appContext).isNotBlank() && now - lastForegroundSync >= 30_000L) {
                SyncScheduler.enqueue(appContext)
                lastForegroundSync = now
            }
            kotlinx.coroutines.delay(5000)
        }
    }
    var selectedNav by remember { mutableStateOf(0) }

    MaterialTheme(colorScheme = WarmColors, typography = BabyLogTypography) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.navigationBarsPadding().height(64.dp),
                    containerColor = Color.White,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0.dp)
                ) {
                    val destinations = listOf("首页", "记录", "成长", "相册", "疫苗")
                    destinations.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = selectedNav == index,
                            onClick = { selectedNav = index },
                            icon = { Text(listOf("⌂", "＋", "⌁", "▦", "✚")[index], style = MaterialTheme.typography.titleMedium) },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = if (selectedNav == index) FontWeight.SemiBold else FontWeight.Medium), maxLines = 1) }
                        )
                    }
                }
            },
        ) { scaffoldPadding ->
            LazyColumn(
                state = pageListStates[selectedNav],
                modifier = Modifier.fillMaxSize().padding(scaffoldPadding).pointerInput(selectedNav) {
                    var horizontalTravel = 0f
                    val swipeThreshold = 72.dp.toPx()
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount -> horizontalTravel += dragAmount },
                        onDragEnd = {
                            if (horizontalTravel < -swipeThreshold) selectedNav = (selectedNav + 1).coerceAtMost(4)
                            else if (horizontalTravel > swipeThreshold) selectedNav = (selectedNav - 1).coerceAtLeast(0)
                            horizontalTravel = 0f
                        },
                        onDragCancel = { horizontalTravel = 0f }
                    )
                },
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 9.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                if (selectedNav == 0) item {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text("BABYLOG", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text("成长的每一天", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(onClick = { showSyncSettings = true }, contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp)) {
                                Text("⚙ 设置")
                            }
                        }
                        if (!syncError.isNullOrBlank()) Text("同步提示：" + syncError, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                if (selectedNav == 0) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().clickable { showSyncDetails = !showSyncDetails }
                        ) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                val syncLabel = when {
                                    SyncPreferences.familyId(appContext).isBlank() -> "离线保存"
                                    syncing -> "正在同步"
                                    !syncError.isNullOrBlank() -> "同步需要检查"
                                    lastSyncAt > 0L -> "家庭同步正常"
                                    else -> "等待首次同步"
                                }
                                Box(Modifier.size(8.dp).background(
                                    when { !syncError.isNullOrBlank() -> MaterialTheme.colorScheme.error; syncing -> MaterialTheme.colorScheme.primary; else -> Color(0xFF55A477) },
                                    CircleShape
                                ))
                                Text(syncLabel, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 9.dp).weight(1f))
                                Text(
                                    if (lastSyncAt > 0L) SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(lastSyncAt)) else "查看详情",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(if (showSyncDetails) "⌃" else "⌄", modifier = Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (showSyncDetails) Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(if (syncing) "正在交换本地与云端数据…" else "自动同步已开启", style = MaterialTheme.typography.bodyMedium)
                                        Text(if (lastSyncAt > 0L) "上次成功：${SimpleDateFormat("yyyy年M月d日 HH时mm分", Locale.CHINA).format(Date(lastSyncAt))}" else "尚无成功同步记录", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    OutlinedButton(enabled = SyncPreferences.familyId(appContext).isNotBlank() && !syncing, onClick = { SyncScheduler.enqueue(appContext) }) { Text(if (syncing) "同步中" else "立即同步") }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("本地与云端校验", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(syncConsistency ?: "尚未校验", style = MaterialTheme.typography.labelSmall, color = if (syncConsistency == "一致") Color(0xFF38845B) else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                                }
                                if (!syncError.isNullOrBlank()) Text("同步提示：$syncError", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                if (selectedNav == 0) item {
                    if (baby == null || editingProfile) {
                        BabyProfileEditor(baby) { name, birthDate, sex, avatar, weight, length, head ->
                            val now = System.currentTimeMillis()
                            val birthTime = birthDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            scope.launch {
                                val babyId = baby?.id ?: UUID.randomUUID().toString()
                                val savedAvatar = persistBabyAvatar(appContext, avatar, babyId)
                                val updatedBaby = BabyEntity(
                                    id = babyId,
                                    familyId = SyncPreferences.familyId(appContext).ifBlank { baby?.familyId ?: "00000000-0000-0000-0000-000000000000" },
                                    displayName = name.trim(), birthTime = birthTime,
                                    createdAt = baby?.createdAt ?: now, updatedAt = now,
                                    sex = sex, avatarUri = savedAvatar, birthWeightGrams = weight,
                                    birthLengthCm = length, birthHeadCircumferenceCm = head,
                                    serverVersion = baby?.serverVersion
                                )
                                dao.upsertBaby(updatedBaby)
                                if (SyncPreferences.familyId(appContext).isNotBlank()) {
                                    dao.clearPendingOperations("baby", updatedBaby.id)
                                    dao.enqueueOperation(SyncOperationEntity(UUID.randomUUID().toString(), "baby", updatedBaby.id, "upsert", babySyncPayload(updatedBaby).toString(), updatedBaby.serverVersion, now))
                                    SyncScheduler.enqueue(appContext)
                                }
                                editingProfile = false
                            }
                        }
                    } else BabyProfileCard(baby) { editingProfile = true }
                }
                if (selectedNav == 0 && baby != null) item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("快捷记录", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionCard("🍼", "喂养", "配方奶 · ml", Modifier.weight(1f)) { addingEvent = "FEEDING" }
                            ActionCard("🌿", "大小便", "护理情况", Modifier.weight(1f)) { addingEvent = "DIAPER" }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionCard("🌙", "睡眠", "休息时长", Modifier.weight(1f)) { addingEvent = "SLEEP" }
                            ActionCard("📏", "成长测量", "体重身长", Modifier.weight(1f)) { addingEvent = "GROWTH" }
                        }
                    }
                }
                if (baby != null) {
                    if (selectedNav == 0) item { TodaySummary(normalEvents) }

                    if (selectedNav == 2) item { TrendsSummary(normalEvents) }
                    if (selectedNav == 2) item { GrowthSection(baby, normalEvents) }
                    if (selectedNav == 3) item {
                        BabyAlbumSection(
                            baby = baby,
                            photos = photos,
                            onAdd = { uri, takenAt ->
                                scope.launch {
                                    runCatching {
                                        val mime = appContext.contentResolver.getType(uri) ?: "image/jpeg"
                                        require(mime in setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")) { "暂不支持该照片格式：$mime" }
                                        val extension = when (mime) { "image/png" -> ".png"; "image/webp" -> ".webp"; "image/heic" -> ".heic"; "image/heif" -> ".heif"; else -> ".jpg" }
                                        val folder = File(appContext.filesDir, "baby_album").apply { mkdirs() }
                                        val photoId = UUID.randomUUID().toString()
                                        val destination = File(folder, photoId + extension)
                                        withContext(Dispatchers.IO) {
                                            appContext.contentResolver.openInputStream(uri)?.use { input -> destination.outputStream().use { output -> input.copyTo(output) } } ?: error("无法读取所选照片")
                                        }
                                        val now = System.currentTimeMillis()
                                        val photo = BabyPhotoEntity(photoId, baby.id, destination.absolutePath, null, takenAt, now, now)
                                        dao.upsertPhoto(photo)
                                        if (SyncPreferences.familyId(appContext).isNotBlank()) {
                                            dao.enqueueOperation(SyncOperationEntity(UUID.randomUUID().toString(), "photo", photo.id, "upsert", photoSyncPayload(photo, mime).toString(), photo.serverVersion, now))
                                            SyncScheduler.enqueue(appContext)
                                        }
                                    }
                                }
                            },
                            onCaption = { photo, caption ->
                                scope.launch {
                                    val updated = photo.copy(caption = caption, updatedAt = System.currentTimeMillis(), syncStatus = "PENDING")
                                    dao.upsertPhoto(updated)
                                    if (SyncPreferences.familyId(appContext).isNotBlank()) {
                                        dao.clearPendingOperations("photo", photo.id)
                                        dao.enqueueOperation(SyncOperationEntity(UUID.randomUUID().toString(), "photo", photo.id, "upsert", photoSyncPayload(updated, mimeTypeForPath(updated.localPath)).toString(), photo.serverVersion, updated.updatedAt))
                                        SyncScheduler.enqueue(appContext)
                                    }
                                }
                            },
                            onDelete = { photo ->
                                scope.launch {
                                    val now = System.currentTimeMillis()
                                    dao.softDeletePhoto(photo.id, now)
                                    if (SyncPreferences.familyId(appContext).isNotBlank()) {
                                        dao.clearPendingOperations("photo", photo.id)
                                        dao.enqueueOperation(SyncOperationEntity(UUID.randomUUID().toString(), "photo", photo.id, "delete", photoSyncPayload(photo, mimeTypeForPath(photo.localPath)).toString(), photo.serverVersion, now))
                                        SyncScheduler.enqueue(appContext)
                                    }
                                    withContext(Dispatchers.IO) { File(photo.localPath).delete() }
                                }
                            }
                        )
                    }
                    if (selectedNav == 1) item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("成长足迹", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("共 ${normalEvents.size} 条", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (selectedNav == 1) item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf("全部", "喂养", "大小便", "睡眠", "成长测量", "其他")) { category ->
                                if (selectedEventFilter == category) {
                                    FilledTonalButton(onClick = { selectedEventFilter = category }) { Text(category) }
                                } else {
                                    OutlinedButton(onClick = { selectedEventFilter = category }) { Text(category) }
                                }
                            }
                        }
                    }
                    if (selectedNav == 1 && filteredEvents.isEmpty()) {
                        item {
                            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF2EDF7))) {
                                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("🌱", style = MaterialTheme.typography.headlineLarge)
                                    Text(
                                        if (selectedEventFilter == "全部") "第一条记录，等你来写" else "还没有${selectedEventFilter}记录",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        if (selectedEventFilter == "全部") "从一次喂养、一段好觉或一次成长测量开始。" else "试试切换其他分类，查看宝宝的成长足迹。",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else if (selectedNav == 1) items(filteredEvents, key = { it.id }) { event ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            EventCard(event, modifier = Modifier.fillMaxWidth(), onEdit = { editingEvent = event }, onDelete = { deletingEvent = event })
                        }
                    }
                    if (selectedNav == 4) item { VaccinePage(vaccineEvents, birthDate = java.time.Instant.ofEpochMilli(baby.birthTime).atZone(ZoneId.systemDefault()).toLocalDate(), sex = baby.sex, onSave = { existing, payload, timestamp ->
                        val now = System.currentTimeMillis()
                        scope.launch {
                            val vaccine = existing?.copy(occurredAt = timestamp, payloadJson = payload.toString(), updatedAt = now, syncStatus = "PENDING")
                                ?: BabyEventEntity(UUID.randomUUID().toString(), baby.id, "VACCINE", timestamp, payload.toString(), null, now)
                            dao.upsertEvent(vaccine)
                            if (SyncPreferences.familyId(appContext).isNotBlank()) {
                                dao.clearPendingOperations("event", vaccine.id)
                                dao.enqueueOperation(SyncOperationEntity(UUID.randomUUID().toString(), "event", vaccine.id, "upsert", eventSyncPayload(vaccine).toString(), vaccine.serverVersion, now))
                                SyncScheduler.enqueue(appContext)
                            }
                        }
                    }, onDelete = { event -> scope.launch {
                        val now = System.currentTimeMillis(); dao.softDeleteEvent(event.id, now)
                        if (SyncPreferences.familyId(appContext).isNotBlank()) {
                            val deleted = dao.getEvent(event.id)
                            if (deleted != null) { dao.clearPendingOperations("event", deleted.id); dao.enqueueOperation(SyncOperationEntity(UUID.randomUUID().toString(), "event", deleted.id, "delete", eventSyncPayload(deleted).toString(), deleted.serverVersion, now)); SyncScheduler.enqueue(appContext) }
                        }
                    } }) }
                } else if (selectedNav == 0) item { Text("创建宝宝档案后，就可以开始记录每天的生活点滴。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }

    if (deletingEvent != null) {
        val target = deletingEvent!!
        AlertDialog(
            onDismissRequest = { deletingEvent = null },
            title = { Text("删除这条记录？") },
            text = { Text("删除后会从本机记录中隐藏；如果已启用家庭同步，也会同步删除。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val now = System.currentTimeMillis()
                        dao.softDeleteEvent(target.id, now)
                        if (SyncPreferences.familyId(appContext).isNotBlank()) {
                            val deleted = dao.getEvent(target.id)
                            if (deleted != null) {
                                dao.clearPendingOperations("event", deleted.id)
                                dao.enqueueOperation(SyncOperationEntity(UUID.randomUUID().toString(), "event", deleted.id, "delete", eventSyncPayload(deleted).toString(), deleted.serverVersion, now))
                                SyncScheduler.enqueue(appContext)
                            }
                        }
                        deletingEvent = null
                    }
                }) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingEvent = null }) { Text("取消") } }
        )
    }

    if (showSyncSettings) {
        SyncSettingsDialog(dao = dao, onDismiss = { showSyncSettings = false })
    }

    if ((addingEvent != null || editingEvent != null) && baby != null) {
        val currentEvent = editingEvent
        EventEditor(currentEvent?.eventType ?: addingEvent!!, existing = currentEvent, onDismiss = { addingEvent = null; editingEvent = null }) { type, payload, occurredAt ->
            val now = System.currentTimeMillis()
            scope.launch {
                val event = currentEvent?.copy(
                    eventType = type, occurredAt = occurredAt, payloadJson = payload.toString(),
                    updatedAt = now, syncStatus = "PENDING", deletedAt = null
                ) ?: BabyEventEntity(UUID.randomUUID().toString(), baby.id, type, occurredAt, payload.toString(), null, now)
                dao.upsertEvent(event)
                if (SyncPreferences.familyId(appContext).isNotBlank()) {
                    dao.clearPendingOperations("event", event.id)
                    dao.enqueueOperation(SyncOperationEntity(UUID.randomUUID().toString(), "event", event.id, "upsert", eventSyncPayload(event).toString(), event.serverVersion, now))
                    SyncScheduler.enqueue(appContext)
                }
                addingEvent = null
                editingEvent = null
            }
        }
    }
}




@Composable
private fun VaccineScheduleSection(
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
        Triple(item, date, vaccineDoseIsRecorded(item, records))
    }.sortedBy { it.second }
    val pending = decorated.filterNot { it.third }
    val completedCount = decorated.size - pending.size
    val filtered = when (selectedCategory) {
        "待接种" -> pending
        "全部" -> decorated
        else -> decorated.filter { it.first.category == selectedCategory }
    }
    val visible = if (showAll) filtered else {
        if (selectedCategory == "待接种") filtered.take(8)
        else (filtered.filterNot { it.third }.take(8) + filtered.filter { it.third }.takeLast(4)).distinct().sortedBy { it.second }
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
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EFF8))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("接种计划 · 倒计时", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("按类别查看计划剂次，完成后可直接在项目旁记录", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("已接种 $completedCount/${decorated.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            SelectionRow(listOf("待接种", "全部", "国家免规", "自费常见", "流脑系列", "联合疫苗"), selectedCategory) {
                selectedCategory = it
                showAll = false
            }
            conflicts.forEach { warning ->
                Text("⚠ 接种间隔提示：$warning。部分注射用减毒活疫苗通常需同日接种或间隔至少 28 天，请让接种门诊核对具体产品和日期。",
                    color = Color(0xFFB65D4A), style = MaterialTheme.typography.bodySmall)
            }
            if (visible.isEmpty()) {
                Text(if (selectedCategory == "待接种") "当前没有待记录剂次，太棒啦！" else "这个分类下暂时没有计划项目。", color = MaterialTheme.colorScheme.primary)
            } else {
                visible.forEach { (item, date, completed) ->
                    val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
                    val status = when {
                        completed -> "已接种"
                        days < 0 -> "逾期 ${-days} 天"
                        days == 0L -> "今天接种"
                        else -> "还有 ${days} 天"
                    }
                    val statusColor = when {
                        completed -> Color(0xFF54856A)
                        days <= 0 -> Color(0xFFB65D4A)
                        days <= 14 -> Color(0xFFB77936)
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
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
                            if (!completed) TextButton(onClick = { onAddRecord(item.vaccineName, item.dose) }) {
                                Text("＋ 记录", style = MaterialTheme.typography.labelMedium)
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
                        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).padding(10.dp)) {
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
private fun VaccinePage(events: List<BabyEventEntity>, birthDate: LocalDate, sex: String?, onSave: (BabyEventEntity?, JSONObject, Long) -> Unit, onDelete: (BabyEventEntity) -> Unit) {
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<BabyEventEntity?>(null) }
    var editorInitialName by remember { mutableStateOf("") }
    var editorInitialDose by remember { mutableStateOf("") }
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
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE7F6))) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("💉 宝宝疫苗档案", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("已记录 " + events.size + " 次接种", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("接种安排请以当地接种机构和医生提供的计划为准。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (events.isEmpty()) {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💉", style = MaterialTheme.typography.headlineLarge)
                    Text("还没有疫苗接种记录", fontWeight = FontWeight.SemiBold)
                    Text("完成接种后，记录疫苗名称、日期和剂次。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Text("接种历史", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            events.sortedByDescending { it.occurredAt }.forEach { event ->
                val data = remember(event.payloadJson) { runCatching { JSONObject(event.payloadJson) }.getOrElse { JSONObject() } }
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
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
                            TextButton(onClick = { onDelete(event) }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
    if (showEditor) VaccineEditor(
        editing,
        initialName = editorInitialName,
        initialDose = editorInitialDose,
        onDismiss = { showEditor = false }
    ) { payload, timestamp ->
        onSave(editing, payload, timestamp)
        showEditor = false
    }
}


@Composable
private fun VaccineEditor(existing: BabyEventEntity?, initialName: String = "", initialDose: String = "", onDismiss: () -> Unit, onSave: (JSONObject, Long) -> Unit) {
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
            Column(Modifier.height(460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                if (name.isBlank()) { error = "请填写疫苗名称"; return@Button }
                val payload = JSONObject().put("vaccineName", name.trim()).put("dose", dose.trim())
                    .put("clinic", clinic.trim()).put("site", site.trim()).put("batchNumber", batch.trim()).put("note", note.trim())
                onSave(payload, date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            }) { Text("保存记录") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
    if (showDateTimePicker) DateTimeOptionDialog(
        initial = date.atStartOfDay(), includeTime = false,
        onDismiss = { showDateTimePicker = false },
        onConfirm = { date = it.toLocalDate(); showDateTimePicker = false }
    )
}

@Composable
private fun TrendsSummary(events: List<BabyEventEntity>) {
    var range by remember { mutableStateOf("近7天") }
    var category by remember { mutableStateOf("全部") }
    val days = when (range) { "近7天" -> 7L; "近30天" -> 30L; "近90天" -> 90L; else -> Long.MAX_VALUE }
    val start = if (days == Long.MAX_VALUE) 0L else LocalDate.now().minusDays(days - 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val ranged = events.filter { it.occurredAt >= start }
    val recent = ranged.filter { event ->
        when (category) {
            "喂养" -> event.eventType == "FEEDING"
            "护理" -> event.eventType == "DIAPER"
            "睡眠" -> event.eventType == "SLEEP"
            "成长" -> event.eventType == "GROWTH"
            else -> true
        }
    }
    val milk = recent.filter { it.eventType == "FEEDING" }.sumOf { runCatching { JSONObject(it.payloadJson).optInt("amountMl") }.getOrDefault(0) }
    val feedCount = recent.count { it.eventType == "FEEDING" }
    val peeCount = recent.count { it.eventType == "DIAPER" && runCatching { JSONObject(it.payloadJson).optBoolean("urine") }.getOrDefault(false) }
    val stoolCount = recent.count { it.eventType == "DIAPER" && runCatching { JSONObject(it.payloadJson).optBoolean("stool") }.getOrDefault(false) }
    val sleepMinutes = recent.filter { it.eventType == "SLEEP" }.sumOf { runCatching { JSONObject(it.payloadJson).optInt("durationMinutes") }.getOrDefault(0) }
    val daysCovered = if (days == Long.MAX_VALUE) {
        if (recent.isEmpty()) 1 else (((System.currentTimeMillis() - (recent.minOf { it.occurredAt })) / 86400000L) + 1).coerceAtLeast(1)
    } else days
    val average = recent.size.toDouble() / daysCovered.toDouble()
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("数据统计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${recent.size} 条记录", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("统计周期", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            SelectionRow(listOf("近7天", "近30天", "近90天", "全部"), range) { range = it }
            Text("记录类型", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            SelectionRow(listOf("全部", "喂养", "护理", "睡眠", "成长"), category) { category = it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryPill("📈", "记录总数", "${recent.size} 条", Modifier.weight(1f))
                SummaryPill("🗓️", "日均记录", "${"%.1f".format(Locale.CHINA, average)} 条", Modifier.weight(1f))
            }
            if (category == "全部" || category == "喂养") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryPill("🍼", "配方奶总量", "${milk} ml", Modifier.weight(1f))
                SummaryPill("🧴", "喂养次数", "${feedCount} 次", Modifier.weight(1f))
            }
            if (category == "全部" || category == "护理") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryPill("💧", "小便", "${peeCount} 次", Modifier.weight(1f))
                SummaryPill("🌿", "大便", "${stoolCount} 次", Modifier.weight(1f))
            }
            if (category == "全部" || category == "睡眠") SummaryPill("🌙", "睡眠时长", if (sleepMinutes >= 60) "${sleepMinutes / 60}小时${sleepMinutes % 60}分" else "${sleepMinutes}分钟", Modifier.fillMaxWidth())
            if (recent.isEmpty()) Text("这个时间范围内还没有相关记录。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable
private fun GrowthSection(baby: BabyEntity, events: List<BabyEventEntity>) {
    var metric by remember { mutableStateOf("体重") }
    var range by remember { mutableStateOf("全部") }
    val key = when (metric) { "身长" -> "lengthCm"; "头围" -> "headCircumferenceCm"; else -> "weightGrams" }
    val unit = if (metric == "体重") "kg" else "cm"
    val allPoints = events.filter { it.eventType == "GROWTH" }.mapNotNull { event ->
        val value = runCatching {
            JSONObject(event.payloadJson).let { payload ->
                if (key == "weightGrams") payload.optDouble(key).takeIf { it > 0 }?.div(1000.0)
                else payload.optDouble(key).takeIf { it > 0 }
            }
        }.getOrNull()
        value?.let { event.occurredAt to it.toFloat() }
    }.sortedBy { it.first }
    val now = System.currentTimeMillis()
    val days = when (range) { "近 30 天" -> 30L; "近 90 天" -> 90L; "近半年" -> 182L; "近一年" -> 365L; else -> Long.MAX_VALUE }
    val points = if (days == Long.MAX_VALUE) allPoints else allPoints.filter { it.first >= now - days * 86400000L }
    val latest = points.lastOrNull()
    val previous = points.dropLast(1).lastOrNull()
    val delta = if (latest != null && previous != null) latest.second - previous.second else null
    val minValue = points.minOfOrNull { it.second }
    val maxValue = points.maxOfOrNull { it.second }
    val min = ((minValue ?: 0f) - if (metric == "体重") 0.25f else 1f).coerceAtLeast(0f)
    val max = (maxValue ?: 1f) + if (metric == "体重") 0.25f else 1f
    val dateFormat = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("成长曲线", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("按实际测量日期展示变化", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(latest?.let { "${"%.2f".format(Locale.getDefault(), it.second)} $unit" } ?: "暂无数据", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    if (delta != null) {
                        val sign = if (delta > 0) "+" else ""
                        Text("较上次 $sign${"%.2f".format(Locale.getDefault(), delta)} $unit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            SelectionRow(listOf("体重", "身长", "头围"), metric) { metric = it }
            SelectionRow(listOf("近30天", "近90天", "近半年", "近一年", "全部"), range.replace("近30天","近 30 天").replace("近90天","近 90 天").replace("近半年","近半年").replace("近一年","近一年")) { range = it.replace("近30天","近 30 天").replace("近90天","近 90 天") }
            if (points.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    Text("还没有${metric}数据，记录几次后就能看到变化", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.width(42.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Text("${"%.1f".format(Locale.getDefault(), max)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(110.dp))
                        Text("${"%.1f".format(Locale.getDefault(), min)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Canvas(Modifier.weight(1f).height(150.dp)) {
                        val left = 8f
                        val right = size.width - 8f
                        val top = 10f
                        val bottom = size.height - 12f
                        val valueSpan = (max - min).coerceAtLeast(0.1f)
                        val timeStart = points.first().first
                        val timeSpan = (points.last().first - timeStart).coerceAtLeast(1L)
                        repeat(4) { index ->
                            val y = top + (bottom - top) * index / 3f
                            drawLine(Color(0xFFEDE7F6), Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
                        }
                        val coords = points.map { point ->
                            val x = left + (right - left) * ((point.first - timeStart).toFloat() / timeSpan.toFloat())
                            val y = bottom - (point.second - min) / valueSpan * (bottom - top)
                            Offset(x, y)
                        }
                        if (coords.size > 1) {
                            val fillPath = Path().apply {
                                moveTo(coords.first().x, bottom)
                                lineTo(coords.first().x, coords.first().y)
                                coords.drop(1).forEach { lineTo(it.x, it.y) }
                                lineTo(coords.last().x, bottom)
                                close()
                            }
                            drawPath(fillPath, Color(0xFF8874B8).copy(alpha = 0.10f))
                            val linePath = Path().apply {
                                moveTo(coords.first().x, coords.first().y)
                                for (i in 1 until coords.size) {
                                    val previousPoint = coords[i - 1]
                                    val currentPoint = coords[i]
                                    val middleX = (previousPoint.x + currentPoint.x) / 2f
                                    quadraticTo(previousPoint.x, previousPoint.y, middleX, (previousPoint.y + currentPoint.y) / 2f)
                                    lineTo(currentPoint.x, currentPoint.y)
                                }
                            }
                            drawPath(linePath, Color(0xFF8874B8), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                        }
                        coords.forEachIndexed { index, point ->
                            val isLatest = index == coords.lastIndex
                            drawCircle(if (isLatest) Color(0xFFB77969) else Color(0xFF8874B8), radius = if (isLatest) 5.dp.toPx() else 3.5.dp.toPx(), center = point)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(points.first().first.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormat) }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(points.last().first.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormat) }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${points.size} 次测量 · 最新记录 ${SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(latest!!.first))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                points.takeLast(5).asReversed().forEach { point ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(point.first)), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${"%.2f".format(Locale.CHINA, point.second)} $unit", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Text("曲线按实际日期间隔绘制，仅展示已记录的数据，不代表医学评估；建议按医生建议解读。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BabyAlbumSection(
    baby: BabyEntity,
    photos: List<BabyPhotoEntity>,
    onAdd: (android.net.Uri, Long) -> Unit,
    onCaption: (BabyPhotoEntity, String) -> Unit,
    onDelete: (BabyPhotoEntity) -> Unit
) {
    var deletingPhoto by remember { mutableStateOf<BabyPhotoEntity?>(null) }
    var viewingPhoto by remember { mutableStateOf<BabyPhotoEntity?>(null) }
    var editingCaption by remember { mutableStateOf<BabyPhotoEntity?>(null) }
    var photoDateTime by remember { mutableStateOf(LocalDateTime.now()) }
    val photoDateTimeLabel = remember(photoDateTime) {
        photoDateTime.format(DateTimeFormatter.ofPattern("M月d日 HH时mm分", Locale.CHINA))
    }
    val context = LocalContext.current
    val albumScope = rememberCoroutineScope()
    var galleryMessage by remember { mutableStateOf<String?>(null) }
    var pendingPhotoUris by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    var showPhotoTimePicker by remember { mutableStateOf(false) }
    var albumFilter by remember { mutableStateOf("全部") }
    var selectedPhotoDate by remember { mutableStateOf<LocalDate?>(null) }
    var showPhotoDatePicker by remember { mutableStateOf(false) }
    val filteredPhotos = photos.filter { photo ->
        val date = java.time.Instant.ofEpochMilli(photo.takenAt).atZone(ZoneId.systemDefault()).toLocalDate()
        when (albumFilter) {
            "近7天" -> photo.takenAt >= System.currentTimeMillis() - 7L * 86400000L
            "近30天" -> photo.takenAt >= System.currentTimeMillis() - 30L * 86400000L
            "本月" -> date.year == LocalDate.now().year && date.month == LocalDate.now().month
            "指定日期" -> selectedPhotoDate == date
            else -> true
        }
    }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            pendingPhotoUris = uris
            showPhotoTimePicker = true
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("成长相册", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "共 ${photos.size} 张照片 · 按拍摄日期排列",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Button(
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                ) { Text("＋ 添加照片") }
            }
        }

        SelectionRow(listOf("全部", "近7天", "近30天", "本月", "指定日期"), albumFilter) { selected ->
            albumFilter = selected
            if (selected == "指定日期") showPhotoDatePicker = true
        }
        if (albumFilter == "指定日期" && selectedPhotoDate != null) {
            Text("已筛选：${selectedPhotoDate!!.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        if (photos.isEmpty()) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5F0))
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("📷", style = MaterialTheme.typography.headlineLarge)
                    Text("收藏宝宝的第一张成长照片", fontWeight = FontWeight.SemiBold)
                    Text(
                        "照片会保存在本机；加入家庭同步后可共享到 NAS 家庭相册。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val grouped = filteredPhotos.sortedByDescending { it.takenAt }.groupBy {
                SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(it.takenAt))
            }
            if (filteredPhotos.isEmpty()) Text("这个日期范围内还没有照片。", modifier = Modifier.fillMaxWidth().padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            grouped.forEach { (day, dayPhotos) ->
                val photoRows = dayPhotos.chunked(3)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        Modifier.width(18.dp).height((photoRows.size * 124 + 36).dp)
                            .background(Color.Transparent)
                    ) {
                        Box(
                            Modifier.align(Alignment.TopCenter).padding(top = 5.dp)
                                .width(2.dp).fillMaxSize()
                                .background(Color(0xFFE4DCEF), RoundedCornerShape(2.dp))
                        )
                        Surface(
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 3.dp).size(11.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {}
                    }
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(day, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${dayPhotos.size} 张",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        photoRows.forEach { rowPhotos ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                rowPhotos.forEach { photo ->
                                    AlbumPhotoCard(
                                        photo = photo,
                                        onClick = {
                                            viewingPhoto = photo
                                            galleryMessage = null
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(3 - rowPhotos.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }

    viewingPhoto?.let { photo ->
        Dialog(
            onDismissRequest = { viewingPhoto = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, photo.localPath) {
                value = withContext(Dispatchers.IO) {
                    runCatching { decodeSampledPhoto(photo.localPath, 1600, 2000) }.getOrNull()
                }
            }
            var zoom by remember(photo.id) { mutableStateOf(1f) }
            var panX by remember(photo.id) { mutableStateOf(0f) }
            var panY by remember(photo.id) { mutableStateOf(0f) }
            var viewportSize by remember(photo.id) { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
            fun clampPhotoPan(nextZoom: Float, x: Float, y: Float): Pair<Float, Float> {
                val image = bitmap
                if (nextZoom <= 1f || image == null || viewportSize.width == 0 || viewportSize.height == 0) return 0f to 0f
                val fitScale = minOf(viewportSize.width.toFloat() / image.width.toFloat(), viewportSize.height.toFloat() / image.height.toFloat())
                val fittedWidth = image.width * fitScale
                val fittedHeight = image.height * fitScale
                val maxX = ((fittedWidth * nextZoom - viewportSize.width) / 2f).coerceAtLeast(0f)
                val maxY = ((fittedHeight * nextZoom - viewportSize.height) / 2f).coerceAtLeast(0f)
                return x.coerceIn(-maxX, maxX) to y.coerceIn(-maxY, maxY)
            }
            val zoomState = rememberTransformableState { zoomChange, panChange, _ ->
                val nextZoom = (zoom * zoomChange).coerceIn(1f, 5f)
                zoom = nextZoom
                val bounded = clampPhotoPan(nextZoom, panX + panChange.x, panY + panChange.y)
                panX = bounded.first
                panY = bounded.second
            }
            Surface(shape = RoundedCornerShape(0.dp), color = Color.Black, modifier = Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(photo.takenAt)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(SimpleDateFormat("HH时mm分", Locale.CHINA).format(Date(photo.takenAt)), style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                        }
                        TextButton(onClick = { zoom = 1f; panX = 0f; panY = 0f }) { Text("适应屏幕", color = Color.White) }
                        TextButton(onClick = { viewingPhoto = null }) { Text("关闭", color = Color.White) }
                    }
                    Box(
                        Modifier.fillMaxWidth().weight(1f)
                            .onSizeChanged { viewportSize = it }
                            .pointerInput(photo.id) {
                                detectTapGestures(onDoubleTap = {
                                    if (zoom > 1f) { zoom = 1f; panX = 0f; panY = 0f } else { zoom = 2.5f; val bounded = clampPhotoPan(zoom, panX, panY); panX = bounded.first; panY = bounded.second }
                                })
                            }
                            .transformable(zoomState),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap!!.asImageBitmap(),
                                contentDescription = "宝宝照片，可双指缩放、拖动查看，双击放大",
                                modifier = Modifier.fillMaxSize().graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                    translationX = panX
                                    translationY = panY
                                },
                                contentScale = ContentScale.Fit
                            )
                        } else Text("照片暂时无法读取", color = Color.White)
                        Text("双指缩放 · 双击放大", modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                    }
                    Column(Modifier.fillMaxWidth().background(Color(0xFF171717)).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(photo.caption?.takeIf { it.isNotBlank() } ?: "还没有添加照片备注", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        galleryMessage?.let { Text(it, color = Color(0xFFD7C9F4), style = MaterialTheme.typography.bodySmall) }
                    }
                    Row(Modifier.fillMaxWidth().background(Color(0xFF171717)).padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { editingCaption = photo; viewingPhoto = null }) { Text("编辑备注", color = Color.White) }
                        TextButton(onClick = {
                            albumScope.launch {
                                galleryMessage = runCatching {
                                    withContext(Dispatchers.IO) { savePhotoToGallery(context, photo.localPath) }
                                    "已保存到手机相册"
                                }.getOrElse { "保存失败：${it.message}" }
                            }
                        }) { Text("保存到手机", color = Color.White) }
                        TextButton(onClick = { deletingPhoto = photo; viewingPhoto = null }) { Text("删除", color = Color(0xFFFF8A80)) }
                    }
                }
            }
        }
    }

    editingCaption?.let { photo ->
        var caption by remember(photo.id) { mutableStateOf(photo.caption.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editingCaption = null },
            title = { Text("照片备注") },
            text = {
                OutlinedTextField(
                    caption,
                    { caption = it.take(120) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("写下这张照片的故事") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onCaption(photo, caption.trim())
                    editingCaption = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editingCaption = null }) { Text("取消") } }
        )
    }

    deletingPhoto?.let { photo ->
        AlertDialog(
            onDismissRequest = { deletingPhoto = null },
            title = { Text("删除这张照片？") },
            text = {
                Text("照片会从本机相册移除；如果已加入家庭同步，也会从 NAS 家庭相册中删除。")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(photo)
                    deletingPhoto = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deletingPhoto = null }) { Text("取消") } }
        )
    }
    if (showPhotoDatePicker) {
        val datePickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = selectedPhotoDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showPhotoDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedPhotoDate = datePickerState.selectedDateMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
                    showPhotoDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showPhotoDatePicker = false }) { Text("取消") } }
        ) { androidx.compose.material3.DatePicker(state = datePickerState) }
    }
    if (showPhotoTimePicker) DateTimeOptionDialog(
        initial = photoDateTime, includeTime = true,
        onDismiss = { showPhotoTimePicker = false; pendingPhotoUris = emptyList() },
        onConfirm = { selected ->
            photoDateTime = selected
            val timestamp = selected.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            pendingPhotoUris.forEach { onAdd(it, timestamp) }
            pendingPhotoUris = emptyList()
            showPhotoTimePicker = false
        }
    )
}
private fun savePhotoToGallery(context: android.content.Context, path: String) {
    val source = File(path)
    require(source.isFile) { "照片文件不存在" }
    val mime = mimeTypeForPath(path)
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "BabyLog-${System.currentTimeMillis()}${source.extension.let { if (it.isBlank()) ".jpg" else ".$it" }}")
        put(MediaStore.Images.Media.MIME_TYPE, mime)
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BabyLog")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("无法创建相册文件")
    try {
        context.contentResolver.openOutputStream(uri)?.use { output -> source.inputStream().use { it.copyTo(output) } } ?: error("无法写入相册")
        if (android.os.Build.VERSION.SDK_INT >= 29) context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    } catch (e: Exception) { context.contentResolver.delete(uri, null, null); throw e }
}

private fun decodeSampledPhoto(path: String, reqWidth: Int, reqHeight: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > reqWidth * 2 || bounds.outHeight / sampleSize > reqHeight * 2) {
        sampleSize *= 2
    }
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
    })
}

@Composable
private fun AlbumPhotoCard(
    photo: BabyPhotoEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, photo.localPath) {
        value = withContext(Dispatchers.IO) {
            runCatching { decodeSampledPhoto(photo.localPath, 360, 360) }.getOrNull()
        }
    }
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF2EDF7))
    ) {
        Box(Modifier.fillMaxWidth().height(124.dp)) {
            if (bitmap != null) {
                Image(
                    bitmap!!.asImageBitmap(),
                    contentDescription = "宝宝照片缩略图",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("📷", style = MaterialTheme.typography.titleLarge)
                }
            }
            Box(
                Modifier.align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(photo.takenAt)),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
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
    var showBirthDatePicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) avatar = uri.toString()
    }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text(if (existing == null) "建立宝宝档案" else "编辑宝宝档案", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(avatar, 68)
                Column(Modifier.weight(1f)) {
                    Text("宝宝头像", fontWeight = FontWeight.SemiBold)
                    Text("选择一张照片作为档案头像", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("从相册选择") }
            }
            OutlinedTextField(name, { name = it.take(30) }, Modifier.fillMaxWidth(), label = { Text("宝宝昵称") }, singleLine = true)
            Text("性别", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            SelectionRow(listOf("男宝", "女宝", "未设置"), sex) { sex = it }
            Text("出生日期", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = { showBirthDatePicker = true }, modifier = Modifier.fillMaxWidth()) { Text(birthDate.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))) }
            OutlinedTextField(
                value = if (birthWeight <= 0f) "" else birthWeight.toInt().toString(),
                onValueChange = { birthWeight = it.filter(Char::isDigit).toFloatOrNull()?.coerceIn(0f, 8000f) ?: 0f },
                modifier = Modifier.fillMaxWidth(), label = { Text("出生体重（g）") }, singleLine = true
            )
            OutlinedTextField(
                value = if (birthLength <= 0f) "" else "%.1f".format(Locale.getDefault(), birthLength),
                onValueChange = { birthLength = it.replace(',', '.').toFloatOrNull()?.coerceIn(0f, 80f) ?: 0f },
                modifier = Modifier.fillMaxWidth(), label = { Text("出生身长（cm）") }, singleLine = true
            )
            OutlinedTextField(
                value = if (birthHead <= 0f) "" else "%.1f".format(Locale.getDefault(), birthHead),
                onValueChange = { birthHead = it.replace(',', '.').toFloatOrNull()?.coerceIn(0f, 60f) ?: 0f },
                modifier = Modifier.fillMaxWidth(), label = { Text("出生头围（cm）") }, singleLine = true
            )
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
    if (showBirthDatePicker) DateTimeOptionDialog(
        initial = birthDate.atStartOfDay(), includeTime = false,
        onDismiss = { showBirthDatePicker = false },
        onConfirm = { birthDate = it.toLocalDate(); showBirthDatePicker = false }
    )
}

private suspend fun persistBabyAvatar(context: android.content.Context, source: String?, babyId: String): String? {
    if (source.isNullOrBlank()) return null
    val existing = File(source)
    if (existing.isFile) return existing.absolutePath
    val parsed = runCatching { android.net.Uri.parse(source) }.getOrNull() ?: return null
    val bitmap = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > 640 || bounds.outHeight / sampleSize > 640) sampleSize *= 2
            context.contentResolver.openInputStream(parsed)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                })
            }
        }.getOrNull()
    } ?: return null
    return withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "baby_avatars").apply { mkdirs() }
        val destination = File(directory, "$babyId.jpg")
        destination.outputStream().use { output ->
            if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, output)) {
                throw java.io.IOException("头像压缩失败")
            }
        }
        bitmap.recycle()
        destination.absolutePath
    }
}

@Composable
private fun Avatar(uri: String?, size: Int) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, uri) {
        value = if (uri.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            val file = File(uri)
            if (file.isFile) file.inputStream().use { BitmapFactory.decodeStream(it) }
            else context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
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
    val today = LocalDate.now()
    val days = java.time.temporal.ChronoUnit.DAYS.between(birthDate, today).coerceAtLeast(0) + 1
    val months = java.time.temporal.ChronoUnit.MONTHS.between(birthDate, today).coerceAtLeast(0)
    val ageText = if (months < 1) "第 $days 天" else "$months 个月"
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE7F6))) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(baby.avatarUri, 54)
                Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(baby.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${baby.sex ?: "宝宝"} · $ageText", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("出生于 $birthDate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onEdit) { Text("编辑资料") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ProfileMetric("出生体重", baby.birthWeightGrams?.let { "$it g" } ?: "未记录", Modifier.weight(1f))
                ProfileMetric("出生身长", baby.birthLengthCm?.let { "${"%.1f".format(Locale.getDefault(), it)} cm" } ?: "未记录", Modifier.weight(1f))
                ProfileMetric("出生头围", baby.birthHeadCircumferenceCm?.let { "${"%.1f".format(Locale.getDefault(), it)} cm" } ?: "未记录", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ProfileMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Color.White.copy(alpha = 0.72f), RoundedCornerShape(11.dp)).padding(vertical = 5.dp, horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
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
    Column(
        modifier.fillMaxWidth().heightIn(min = 84.dp)
            .background(Color(0xFFFFF5F0), RoundedCornerShape(14.dp))
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
    ) {
        Text(emoji)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun ActionCard(emoji: String, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 88.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Surface(shape = RoundedCornerShape(13.dp), color = Color(0xFFF2EDF7), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(emoji, style = MaterialTheme.typography.headlineSmall) }
            }
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun EventEditor(eventType: String, existing: BabyEventEntity? = null, onDismiss: () -> Unit, onSave: (String, JSONObject, Long) -> Unit) {
    val initialPayload = remember(existing?.id) { runCatching { JSONObject(existing?.payloadJson ?: "{}") }.getOrElse { JSONObject() } }
    val initialTime = remember(existing?.id) {
        existing?.occurredAt?.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() } ?: LocalDateTime.now()
    }
    var date by remember(existing?.id) { mutableStateOf(initialTime.toLocalDate()) }
    var hour by remember(existing?.id) { mutableStateOf(initialTime.hour) }
    var minute by remember(existing?.id) { mutableStateOf(initialTime.minute) }
    var amountText by remember(existing?.id) { mutableStateOf(if (existing == null) "" else initialPayload.optInt("amountMl").takeIf { it > 0 }?.toString().orEmpty()) }
    var urine by remember(existing?.id) { mutableStateOf(if (existing == null) true else initialPayload.optBoolean("urine")) }
    var stool by remember(existing?.id) { mutableStateOf(initialPayload.optBoolean("stool")) }
    var stoolShape by remember(existing?.id) { mutableStateOf(initialPayload.optString("stoolShape", "糊状")) }
    var stoolColor by remember(existing?.id) { mutableStateOf(initialPayload.optString("stoolColor", "黄色")) }
    var stoolAmount by remember(existing?.id) { mutableStateOf(initialPayload.optString("stoolAmount", "中量")) }
    var sleepMinutes by remember(existing?.id) { mutableStateOf(initialPayload.optInt("durationMinutes", 60).toFloat().coerceIn(10f, 720f)) }
    var weightKg by remember(existing?.id) { mutableStateOf(initialPayload.optInt("weightGrams").takeIf { it > 0 }?.div(1000.0)?.toString().orEmpty()) }
    var lengthCm by remember(existing?.id) { mutableStateOf(initialPayload.optDouble("lengthCm").takeIf { it > 0 }?.toString().orEmpty()) }
    var headCm by remember(existing?.id) { mutableStateOf(initialPayload.optDouble("headCircumferenceCm").takeIf { it > 0 }?.toString().orEmpty()) }
    var note by remember(existing?.id) { mutableStateOf(initialPayload.optString("note")) }
    var error by remember { mutableStateOf<String?>(null) }
    var showEventTimePicker by remember { mutableStateOf(false) }
    val title = when (eventType) { "FEEDING" -> "喂养记录"; "DIAPER" -> "日常护理"; "SLEEP" -> "睡眠记录"; else -> "成长记录" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("记录时间", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { showEventTimePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(date.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)) + " · %02d时%02d分".format(hour, minute))
                }
                when (eventType) {
                    "FEEDING" -> {
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { raw -> amountText = raw.filter(Char::isDigit).take(4) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("本次奶量（ml）") },
                            singleLine = true
                        )
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
                        OutlinedTextField(
                            value = weightKg,
                            onValueChange = { raw -> weightKg = raw.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.') },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("当前体重（kg）") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = lengthCm,
                            onValueChange = { raw -> lengthCm = raw.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.') },
                            modifier = Modifier.fillMaxWidth(), label = { Text("身长（cm）") }, singleLine = true
                        )
                        OutlinedTextField(
                            value = headCm,
                            onValueChange = { raw -> headCm = raw.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.') },
                            modifier = Modifier.fillMaxWidth(), label = { Text("头围（cm）") }, singleLine = true
                        )
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
                    "FEEDING" -> {
                        val amountMl = amountText.toIntOrNull()
                        if (amountMl == null || amountMl !in 1..1000) {
                            error = "请输入 1–1000 ml 的奶量"
                            return@Button
                        }
                        payload.put("method", "配方奶").put("amountMl", amountMl)
                    }
                    "DIAPER" -> {
                        if (!urine && !stool) { error = "请至少选择一项护理情况"; return@Button }
                        payload.put("urine", urine).put("stool", stool)
                        if (stool) payload.put("stoolShape", stoolShape).put("stoolColor", stoolColor).put("stoolAmount", stoolAmount)
                    }
                    "SLEEP" -> payload.put("durationMinutes", sleepMinutes.toInt())
                    "GROWTH" -> {
                        val weight = weightKg.toDoubleOrNull()
                        val length = lengthCm.toDoubleOrNull()
                        val head = headCm.toDoubleOrNull()
                        if (weight == null && length == null && head == null) {
                            error = "请至少填写一项测量数据"
                            return@Button
                        }
                        if ((weight != null && weight !in 0.1..80.0) ||
                            (length != null && length !in 20.0..120.0) ||
                            (head != null && head !in 20.0..70.0)) {
                            error = "请检查测量数值是否在合理范围内"
                            return@Button
                        }
                        weight?.let { payload.put("weightGrams", (it * 1000).toInt()) }
                        length?.let { payload.put("lengthCm", it) }
                        head?.let { payload.put("headCircumferenceCm", it) }
                    }
                }
                if (note.isNotBlank()) payload.put("note", note.trim())
                onSave(eventType, payload, timestamp)
            }) { Text("保存记录") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
    if (showEventTimePicker) DateTimeOptionDialog(
        initial = date.atTime(hour, minute), includeTime = true,
        onDismiss = { showEventTimePicker = false },
        onConfirm = { selected ->
            date = selected.toLocalDate()
            hour = selected.hour
            minute = selected.minute
            showEventTimePicker = false
        }
    )
}

@Composable
private fun SelectionRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    val columns = if (options.size <= 4) options.size.coerceAtLeast(1) else 3
    val rows = options.chunked(columns)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        rows.forEachIndexed { rowIndex, group ->
            val missing = columns - group.size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (missing > 0) Spacer(Modifier.weight(missing / 2f))
                group.forEach { option ->
                    val modifier = Modifier.weight(1f)
                    if (selected == option) {
                        FilledTonalButton(
                            onClick = { onSelect(option) },
                            modifier = modifier,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(option, maxLines = 1, style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSelect(option) },
                            modifier = modifier,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(option, maxLines = 1, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                if (missing > 0) Spacer(Modifier.weight(missing / 2f))
            }
        }
    }
}

@Composable
private fun EventCard(event: BabyEventEntity, modifier: Modifier = Modifier, onEdit: () -> Unit, onDelete: () -> Unit) {
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
        "GROWTH" -> Triple("📏", "成长测量", listOfNotNull(
            payload.optInt("weightGrams").takeIf { it > 0 }?.let { "${"%.2f".format(Locale.getDefault(), it / 1000.0)} kg" },
            payload.optDouble("lengthCm").takeIf { it > 0 }?.let { "${"%.1f".format(Locale.getDefault(), it)} cm 身长" },
            payload.optDouble("headCircumferenceCm").takeIf { it > 0 }?.let { "${"%.1f".format(Locale.getDefault(), it)} cm 头围" }
        ).joinToString(" · "))
        else -> Triple("📝", "记录", "")
    }
    val time = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(event.occurredAt))
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(shape = RoundedCornerShape(11.dp), color = Color(0xFFF2EDF7), modifier = Modifier.size(34.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(emoji, style = MaterialTheme.typography.titleMedium) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Text(
                    detail + if (payload.optString("note").isNotBlank()) " · " + payload.optString("note") else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)) {
                    Text("编辑", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)) {
                    Text("删除", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}


@Composable
private fun DateTimeOptionDialog(
    initial: LocalDateTime,
    includeTime: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit
) {
    var selected by remember(initial) { mutableStateOf(initial) }
    val currentYear = LocalDate.now().year
    val dateLabel = selected.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA))
    val timeLabel = selected.format(DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (includeTime) "设置日期与时间" else "选择日期", fontWeight = FontWeight.Bold)
                Text("记录会保存为你选择的时间", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = Color(0xFFF1ECF7), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(dateLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        if (includeTime) Text(timeLabel, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text("快速选择日期", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("今天" to LocalDate.now(), "昨天" to LocalDate.now().minusDays(1), "前天" to LocalDate.now().minusDays(2)).forEach { (label, day) ->
                        val active = selected.toLocalDate() == day
                        if (active) FilledTonalButton(onClick = { selected = day.atTime(selected.toLocalTime()) }, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp)) { Text(label) }
                        else OutlinedButton(onClick = { selected = day.atTime(selected.toLocalTime()) }, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp)) { Text(label) }
                    }
                }
                Text("选择具体日期", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 340.dp).align(Alignment.CenterHorizontally),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OptionPicker("年", selected.year.toString(), (1900..currentYear).toList().map(Int::toString), modifier = Modifier.weight(1.25f)) { value ->
                        val year = value.toInt()
                        val maxDay = selected.month.length(java.time.Year.isLeap(year.toLong()))
                        selected = selected.withYear(year).withDayOfMonth(selected.dayOfMonth.coerceAtMost(maxDay))
                    }
                    OptionPicker("月", "%02d".format(selected.monthValue), (1..12).toList().map { "%02d".format(it) }, modifier = Modifier.weight(1f)) { value ->
                        val month = value.toInt()
                        val maxDay = selected.withMonth(month).month.length(java.time.Year.isLeap(selected.year.toLong()))
                        selected = selected.withMonth(month).withDayOfMonth(selected.dayOfMonth.coerceAtMost(maxDay))
                    }
                    OptionPicker("日", "%02d".format(selected.dayOfMonth), (1..selected.month.length(java.time.Year.isLeap(selected.year.toLong()))).toList().map { "%02d".format(it) }, modifier = Modifier.weight(1f)) { value ->
                        selected = selected.withDayOfMonth(value.toInt())
                    }
                }
                if (includeTime) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("设置时间", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("24 小时制", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("小时", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OptionPicker("时", "%02d".format(selected.hour), (0..23).toList().map { "%02d".format(it) }) { selected = selected.withHour(it.toInt()) }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("分钟", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OptionPicker("分", "%02d".format(selected.minute), (0..59).toList().map { "%02d".format(it) }) { selected = selected.withMinute(it.toInt()) }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(selected) }) { Text(if (includeTime) "确认时间" else "确认日期") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun OptionPicker(
    label: String,
    selected: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp), shape = RoundedCornerShape(13.dp)) {
            Text("$selected $label", fontWeight = FontWeight.Bold)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}
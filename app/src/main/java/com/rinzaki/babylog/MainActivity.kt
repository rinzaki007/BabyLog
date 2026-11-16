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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
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
    val photosFlow = remember(baby?.id) { if (baby == null) flowOf(emptyList()) else dao.observePhotoPage(baby.id, 60, 0) }
    val photos by photosFlow.collectAsState(initial = emptyList())
    val photoCountFlow = remember(baby?.id) { if (baby == null) flowOf(0) else dao.observePhotoCount(baby.id) }
    val photoCount by photoCountFlow.collectAsState(initial = 0)
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
    var selectedStartDate by remember { mutableStateOf<LocalDate?>(null) }
    var selectedEndDate by remember { mutableStateOf<LocalDate?>(null) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var timelineEvents by remember { mutableStateOf<List<BabyEventEntity>>(emptyList()) }
    var timelineTotal by remember { mutableStateOf(0) }
    var timelineLoading by remember { mutableStateOf(false) }
    val timelineEventType = when (selectedEventFilter) {
        "喂养" -> "FEEDING"
        "大小便" -> "DIAPER"
        "睡眠" -> "SLEEP"
        "成长测量" -> "GROWTH"
        "其他" -> "OTHER"
        else -> null
    }
    val timelineStartAt = selectedStartDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    val timelineEndAt = selectedEndDate?.plusDays(1)?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    val vaccineEvents = events.filter { it.eventType == "VACCINE" }
    val normalEvents = events.filter { it.eventType != "VACCINE" }
    LaunchedEffect(baby?.id, selectedEventFilter, selectedStartDate, selectedEndDate, events) {
        if (baby == null) {
            timelineEvents = emptyList()
            timelineTotal = 0
        } else {
            timelineLoading = true
            timelineEvents = dao.getTimelinePage(baby.id, timelineStartAt, timelineEndAt, timelineEventType, 100, 0)
            timelineTotal = dao.countTimelineEvents(baby.id, timelineStartAt, timelineEndAt, timelineEventType)
            timelineLoading = false
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
                    modifier = Modifier.navigationBarsPadding().height(68.dp),
                    containerColor = Color.White,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0.dp)
                ) {
                    val destinations = listOf("首页", "记录", "成长", "相册", "疫苗")
                    destinations.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = selectedNav == index,
                            onClick = { selectedNav = index },
                            icon = { Icon(imageVector = when (index) { 0 -> Icons.Filled.Home; 1 -> Icons.Filled.Add; 2 -> Icons.Filled.ShowChart; 3 -> Icons.Filled.Photo; else -> Icons.Filled.Favorite }, contentDescription = label) },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = if (selectedNav == index) FontWeight.SemiBold else FontWeight.Medium), maxLines = 1) }
                        )
                    }
                }
            },
        ) { scaffoldPadding ->
            AnimatedContent(
                targetState = selectedNav,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(animationSpec = tween(190)) { it / 8 } + fadeIn(tween(190))) togetherWith
                            (slideOutHorizontally(animationSpec = tween(150)) { -it / 8 } + fadeOut(tween(150)))
                    } else {
                        (slideInHorizontally(animationSpec = tween(190)) { -it / 8 } + fadeIn(tween(190))) togetherWith
                            (slideOutHorizontally(animationSpec = tween(150)) { it / 8 } + fadeOut(tween(150)))
                    }
                },
                label = "main-page-transition"
            ) { page ->
            LazyColumn(
                state = pageListStates[page],
                modifier = Modifier.fillMaxSize().padding(scaffoldPadding).pointerInput(page) {
                    var horizontalTravel = 0f
                    val swipeThreshold = 72.dp.toPx()
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount -> horizontalTravel += dragAmount },
                        onDragEnd = {
                            if (horizontalTravel < -swipeThreshold) selectedNav = (page + 1).coerceAtMost(4)
                            else if (horizontalTravel > swipeThreshold) selectedNav = (page - 1).coerceAtLeast(0)
                            horizontalTravel = 0f
                        },
                        onDragCancel = { horizontalTravel = 0f }
                    )
                },
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 9.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                if (page == 0) item {
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
                    }
                }
                if (page == 0) item {
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
                if (page == 0) item {
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
                if (page == 0 && baby != null) item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("快捷记录", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionCard("🍼", "喂养", "多种喂养方式", Modifier.weight(1f)) { addingEvent = "FEEDING" }
                            ActionCard("🌿", "大小便", "护理情况", Modifier.weight(1f)) { addingEvent = "DIAPER" }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionCard("🌙", "睡眠", "休息时长", Modifier.weight(1f)) { addingEvent = "SLEEP" }
                            ActionCard("📏", "成长测量", "体重身长", Modifier.weight(1f)) { addingEvent = "GROWTH" }
                        }
                    }
                }
                if (baby != null) {
                    if (page == 0) item { TodaySummary(normalEvents) }

                    if (page == 2) item { TrendsSummary(normalEvents) }
                    if (page == 2) item { GrowthSection(baby, normalEvents) }
                    if (page == 3) item {
                        BabyAlbumSection(
                            baby = baby,
                            photos = photos,
                            photoCount = photoCount,
                            onLoadMore = { offset, limit -> dao.getPhotoPage(baby.id, limit, offset) },
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
                    if (page == 1) item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("成长足迹", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("共 ${timelineTotal} 条", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (page == 1) item {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = { showStartDatePicker = true }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp)) {
                                    Text(selectedStartDate?.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))?.let { "从 $it" } ?: "开始日期", maxLines = 1)
                                }
                                OutlinedButton(onClick = { showEndDatePicker = true }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp)) {
                                    Text(selectedEndDate?.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))?.let { "至 $it" } ?: "结束日期", maxLines = 1)
                                }
                                if (selectedStartDate != null || selectedEndDate != null) TextButton(onClick = { selectedStartDate = null; selectedEndDate = null }) { Text("清除") }
                            }
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
                    }
                    if (page == 1 && timelineEvents.isEmpty() && !timelineLoading) {
                        item {
                            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF2EDF7))) {
                                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("🌱", style = MaterialTheme.typography.headlineLarge)
                                    Text(
                                        if (selectedStartDate != null || selectedEndDate != null) "这个日期范围内暂无记录" else if (selectedEventFilter == "全部") "第一条记录，等你来写" else "还没有${selectedEventFilter}记录",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        if (selectedStartDate != null || selectedEndDate != null) "可以调整日期范围，或清除日期筛选查看全部记录。" else if (selectedEventFilter == "全部") "从一次喂养、一段好觉或一次成长测量开始。" else "试试切换其他分类，查看宝宝的成长足迹。",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else if (page == 1) items(timelineEvents, key = { it.id }) { event ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            EventCard(event, modifier = Modifier.fillMaxWidth(), onEdit = { editingEvent = event }, onDelete = { deletingEvent = event })
                        }
                    }
                    if (page == 1 && timelineEvents.size < timelineTotal) item {
                        OutlinedButton(
                            enabled = !timelineLoading,
                            onClick = {
                                scope.launch {
                                    timelineLoading = true
                                    val nextPage = dao.getTimelinePage(baby.id, timelineStartAt, timelineEndAt, timelineEventType, 100, timelineEvents.size)
                                    timelineEvents = timelineEvents + nextPage
                                    timelineLoading = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (timelineLoading) "正在加载…" else "加载更多记录（已显示 ${timelineEvents.size} / ${timelineTotal}）") }
                    }
                    if (page == 4) item { VaccinePage(vaccineEvents, birthDate = java.time.Instant.ofEpochMilli(baby.birthTime).atZone(ZoneId.systemDefault()).toLocalDate(), sex = baby.sex, onSave = { existing, payload, timestamp ->
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
                } else if (page == 0) item { Text("创建宝宝档案后，就可以开始记录每天的生活点滴。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
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
    if (showStartDatePicker) DateTimeOptionDialog(
        initial = (selectedStartDate ?: LocalDate.now().minusDays(7)).atStartOfDay(),
        includeTime = false,
        onDismiss = { showStartDatePicker = false },
        onConfirm = { selected ->
            val date = selected.toLocalDate()
            selectedStartDate = date
            if (selectedEndDate != null && selectedEndDate!!.isBefore(date)) selectedEndDate = date
            showStartDatePicker = false
        }
    )
    if (showEndDatePicker) DateTimeOptionDialog(
        initial = (selectedEndDate ?: LocalDate.now()).atStartOfDay(),
        includeTime = false,
        onDismiss = { showEndDatePicker = false },
        onConfirm = { selected ->
            val date = selected.toLocalDate()
            selectedEndDate = date
            if (selectedStartDate != null && selectedStartDate!!.isAfter(date)) selectedStartDate = date
            showEndDatePicker = false
        }
    )

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
private fun EventEditor(eventType: String, existing: BabyEventEntity? = null, onDismiss: () -> Unit, onSave: (String, JSONObject, Long) -> Unit) {
    val context = LocalContext.current.applicationContext
    val initialPayload = remember(existing?.id) { runCatching { JSONObject(existing?.payloadJson ?: "{}") }.getOrElse { JSONObject() } }
    val existingFeedingMethod = initialPayload.optString("method", "配方奶").ifBlank { "配方奶" }
    val enabledFeedingMethods = remember { FeedingPreferences.enabledMethods(context) }
    val feedingOptions = remember(existing?.id, enabledFeedingMethods) {
        (enabledFeedingMethods + if (existing != null && existingFeedingMethod in listOf("母乳", "配方奶", "混合喂养")) listOf(existingFeedingMethod) else emptyList())
            .distinct().ifEmpty { listOf("配方奶") }
    }
    val initialTime = remember(existing?.id) {
        existing?.occurredAt?.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() } ?: LocalDateTime.now()
    }
    var date by remember(existing?.id) { mutableStateOf(initialTime.toLocalDate()) }
    var hour by remember(existing?.id) { mutableStateOf(initialTime.hour) }
    var minute by remember(existing?.id) { mutableStateOf(initialTime.minute) }
    var amountText by remember(existing?.id) { mutableStateOf(if (existing == null) "" else initialPayload.optInt("amountMl").takeIf { it > 0 }?.toString().orEmpty()) }
    var feedingMethod by remember(existing?.id) { mutableStateOf(if (existingFeedingMethod in feedingOptions) existingFeedingMethod else feedingOptions.first()) }
    var breastMinutes by remember(existing?.id) { mutableStateOf(initialPayload.optInt("breastMinutes").takeIf { it > 0 }?.toString().orEmpty()) }
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
                        Text("喂养方式", fontWeight = FontWeight.SemiBold)
                        SelectionRow(feedingOptions, feedingMethod) { feedingMethod = it }
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { raw -> amountText = raw.filter(Char::isDigit).take(4) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (feedingMethod == "母乳") "母乳量（ml，可选）" else "配方奶量（ml）") },
                            singleLine = true
                        )
                        Text(if (feedingMethod == "母乳") "亲喂可只记录时长；瓶喂母乳可填写奶量。" else "单位：毫升（ml），也可以直接输入其他数值。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SelectionRow(listOf("60", "90", "120", "150", "180", "210"), amountText) { amountText = it }
                        if (feedingMethod != "配方奶") {
                            OutlinedTextField(
                                value = breastMinutes,
                                onValueChange = { breastMinutes = it.filter(Char::isDigit).take(3) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("亲喂时长（分钟，可选）") },
                                singleLine = true
                            )
                            SelectionRow(listOf("5", "10", "15", "20", "30"), breastMinutes) { breastMinutes = it }
                        }
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
                        SelectionRow(listOf("30", "60", "90", "120", "180"), sleepMinutes.toInt().toString()) { sleepMinutes = it.toFloat() }
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
                var payload = JSONObject()
                when (eventType) {
                    "FEEDING" -> {
                        payload = try {
                            buildFeedingPayload(feedingMethod, amountText, breastMinutes)
                        } catch (validation: IllegalArgumentException) {
                            error = validation.message
                            return@Button
                        }
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
internal fun SelectionRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
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
        "FEEDING" -> Triple("🍼", "喂养", feedingDescription(event.payloadJson))
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
internal fun DateTimeOptionDialog(
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
package com.rinzaki.babylog

import android.graphics.BitmapFactory
import android.widget.Toast
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.rinzaki.babylog.navigation.BabyLogDestination
import com.rinzaki.babylog.navigation.BabyLogNavigationBar
import com.rinzaki.babylog.ui.theme.BabyLogTheme
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.rinzaki.babylog.sync.deletePhotoLocalArtifacts
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

class MainActivity : ComponentActivity() {
    private lateinit var database: BabyLogDatabase
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = Room.databaseBuilder(applicationContext, BabyLogDatabase::class.java, "babylog.db")
            .addMigrations(BabyLogDatabase.MIGRATION_1_2, BabyLogDatabase.MIGRATION_2_3, BabyLogDatabase.MIGRATION_3_4, BabyLogDatabase.MIGRATION_4_5, BabyLogDatabase.MIGRATION_5_6, BabyLogDatabase.MIGRATION_6_7)
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
    val todayDate = LocalDate.now()
    val todayStartAt = remember(baby?.id, todayDate) { todayDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val todayEndAt = remember(baby?.id, todayDate) { todayDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val todayEventsFlow = remember(baby?.id, todayStartAt, todayEndAt) {
        if (baby == null) flowOf(emptyList()) else dao.observeNonVaccineEventsInRange(baby.id, todayStartAt, todayEndAt)
    }
    val todayEvents by todayEventsFlow.collectAsState(initial = emptyList())
    val growthEventsFlow = remember(baby?.id) {
        if (baby == null) flowOf(emptyList()) else dao.observeEventsByType(baby.id, "GROWTH")
    }
    val growthEvents by growthEventsFlow.collectAsState(initial = emptyList())
    val milestoneEventsFlow = remember(baby?.id) {
        if (baby == null) flowOf(emptyList()) else dao.observeEventsByType(baby.id, "MILESTONE")
    }
    val milestoneEvents by milestoneEventsFlow.collectAsState(initial = emptyList())
    val solidFoodEventsFlow = remember(baby?.id) {
        if (baby == null) flowOf(emptyList()) else dao.observeEventsByType(baby.id, "SOLID_FOOD")
    }
    val solidFoodEvents by solidFoodEventsFlow.collectAsState(initial = emptyList())
    val vaccineEventsFlow = remember(baby?.id) {
        if (baby == null) flowOf(emptyList()) else dao.observeEventsByType(baby.id, "VACCINE")
    }
    val vaccineEvents by vaccineEventsFlow.collectAsState(initial = emptyList())
    val eventRevisionFlow = remember(baby?.id) {
        if (baby == null) flowOf("0:0") else dao.observeEventRevision(baby.id)
    }
    val eventRevision by eventRevisionFlow.collectAsState(initial = "0:0")
    val allEventsFlow = remember(baby?.id) {
        if (baby == null) flowOf(emptyList()) else dao.observeEvents(baby.id)
    }
    val allEvents by allEventsFlow.collectAsState(initial = emptyList())
    val photosFlow = remember(baby?.id) { if (baby == null) flowOf(emptyList()) else dao.observePhotoPage(baby.id, 60, 0) }
    val photos by photosFlow.collectAsState(initial = emptyList())
    val photoCountFlow = remember(baby?.id) { if (baby == null) flowOf(0) else dao.observePhotoCount(baby.id) }
    val photoCount by photoCountFlow.collectAsState(initial = 0)
    val photoRevisionFlow = remember(baby?.id) { if (baby == null) flowOf("0:0") else dao.observePhotoRevision(baby.id) }
    val photoRevision by photoRevisionFlow.collectAsState(initial = "0:0")
    var editingProfile by remember { mutableStateOf(false) }
    var exportingGrowthReport by remember { mutableStateOf(false) }
    var showSyncSettings by remember { mutableStateOf(false) }
    var addingEvent by remember { mutableStateOf<String?>(null) }
    var showRecordTypePicker by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<BabyEventEntity?>(null) }
    var deletingEvent by remember { mutableStateOf<BabyEventEntity?>(null) }
    var savingEvent by remember { mutableStateOf(false) }
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
    var timelinePhotos by remember { mutableStateOf<List<BabyPhotoEntity>>(emptyList()) }
    var timelineLinkedPhotos by remember { mutableStateOf<Map<String, List<BabyPhotoEntity>>>(emptyMap()) }
    var selectedTimelinePhoto by remember { mutableStateOf<BabyPhotoEntity?>(null) }
    var timelineTotal by remember { mutableStateOf(0) }
    var timelineLoading by remember { mutableStateOf(false) }
    var timelineError by remember { mutableStateOf<String?>(null) }
    var timelineLoadMoreError by remember { mutableStateOf<String?>(null) }
    var timelineRetryToken by remember { mutableStateOf(0) }
    val timelineEventType = timelineEventTypeForFilter(selectedEventFilter)
    val timelineStartAt = selectedStartDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    val timelineEndAt = selectedEndDate?.plusDays(1)?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    LaunchedEffect(baby?.id, selectedEventFilter, selectedStartDate, selectedEndDate, eventRevision, timelineRetryToken) {
        timelineError = null
        timelineLoadMoreError = null
        if (baby == null) {
            timelineEvents = emptyList()
            timelineTotal = 0
            timelineLoading = false
        } else {
            timelineLoading = true
            try {
                val firstPage = dao.getTimelinePage(baby.id, timelineStartAt, timelineEndAt, timelineEventType, 100, 0)
                val total = dao.countTimelineEvents(baby.id, timelineStartAt, timelineEndAt, timelineEventType)
                timelineEvents = firstPage
                timelineTotal = total
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                timelineEvents = emptyList()
                timelineTotal = 0
                timelineError = error.message?.takeIf { it.isNotBlank() } ?: "加载记录失败，请重试"
            } finally {
                timelineLoading = false
            }
        }
    }
    LaunchedEffect(baby?.id, timelineEvents, photoRevision) {
        if (baby == null || timelineEvents.isEmpty()) {
            timelinePhotos = emptyList()
            timelineLinkedPhotos = emptyMap()
        } else {
            val zone = ZoneId.systemDefault()
            val days = timelineEvents.map { java.time.Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }
            val startAt = days.minOrNull()!!.atStartOfDay(zone).toInstant().toEpochMilli()
            val endAt = days.maxOrNull()!!.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            timelinePhotos = dao.getPhotosInRange(baby.id, startAt, endAt, 300)
            val linkedByEvent = linkedMapOf<String, List<BabyPhotoEntity>>()
            timelineEvents.forEach { event ->
                val linked = PhotoEventAssociation.photoIds(event).mapNotNull { photoId ->
                    dao.getPhoto(photoId)?.takeIf { it.babyId == baby.id && it.deletedAt == null }
                }.distinctBy { it.id }
                if (linked.isNotEmpty()) linkedByEvent[event.id] = linked
            }
            timelineLinkedPhotos = linkedByEvent
        }
    }
    // Pre-group once per data change instead of scanning the full lists for every row.
    val timelinePhotosByDay = remember(timelinePhotos) {
        timelinePhotos.groupBy { java.time.Instant.ofEpochMilli(it.takenAt).atZone(ZoneId.systemDefault()).toLocalDate() }
    }
    val timelineDayCounts = remember(timelineEvents) {
        timelineEvents.groupingBy { java.time.Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate() }.eachCount()
    }
    val lastTimelineEventIdByDay = remember(timelineEvents) {
        timelineEvents.groupBy { java.time.Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate() }
            .mapValues { (_, dayEvents) -> dayEvents.last().id }
    }
    val loadMoreTimelinePage: suspend () -> Unit = {
        val currentBaby = baby
        if (currentBaby != null && timelineEvents.isNotEmpty()) {
            timelineLoading = true
            timelineLoadMoreError = null
            try {
                val cursor = timelineEvents.last()
                val nextPage = dao.getTimelinePageAfter(
                    currentBaby.id,
                    timelineStartAt,
                    timelineEndAt,
                    timelineEventType,
                    cursor.occurredAt,
                    cursor.id,
                    100
                )
                val existingIds = timelineEvents.mapTo(mutableSetOf()) { it.id }
                timelineEvents = timelineEvents + nextPage.filterNot { it.id in existingIds }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                timelineLoadMoreError = error.message?.takeIf { it.isNotBlank() } ?: "加载更多记录失败，请重试"
            } finally {
                timelineLoading = false
            }
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
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { BabyLogDestination.entries.size })
    LaunchedEffect(selectedNav) {
        if (pagerState.currentPage != selectedNav) pagerState.animateScrollToPage(selectedNav)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page -> selectedNav = page }
    }

    BabyLogTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                BabyLogNavigationBar(selectedIndex = selectedNav, onSelect = { selectedNav = it })
            },
        ) { scaffoldPadding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
                beyondViewportPageCount = 1
            ) { page ->
            LazyColumn(
                state = pageListStates[page],
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 9.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                if (page == 0) item { HomeDashboardHeader(todayDate = todayDate) { showSyncSettings = true } }
                if (page == 0) item {
                    val syncConfigured = SyncPreferences.familyId(appContext).isNotBlank()
                    val syncLabel = when {
                        !syncConfigured -> "离线保存"
                        syncing -> "正在同步"
                        !syncError.isNullOrBlank() -> "同步需要检查"
                        lastSyncAt > 0L -> "家庭同步正常"
                        else -> "等待首次同步"
                    }
                    HomeSyncStatusCard(
                        syncLabel = syncLabel,
                        lastSyncLabel = if (lastSyncAt > 0L) SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(lastSyncAt)) else "查看详情",
                        syncDetailLabel = if (lastSyncAt > 0L) "上次成功：" + SimpleDateFormat("yyyy年M月d日 HH时mm分", Locale.CHINA).format(Date(lastSyncAt)) else "尚无成功同步记录",
                        syncConfigured = syncConfigured,
                        syncing = syncing,
                        showDetails = showSyncDetails,
                        consistency = syncConsistency,
                        syncError = syncError,
                        onToggleDetails = { showSyncDetails = !showSyncDetails },
                        onSyncNow = { SyncScheduler.enqueue(appContext) }
                    )
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
                    val stageProfile = remember(baby.birthTime) { homeStageProfile(baby.birthTime) }
                    val shortcuts = remember(stageProfile) { orderedHomeShortcuts(stageProfile) }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("快捷记录", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(7.dp))
                            Text(stageProfile.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        shortcuts.chunked(2).forEach { rowShortcuts ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowShortcuts.forEach { shortcut ->
                                    ActionCard(
                                        shortcut.emoji,
                                        shortcut.title,
                                        shortcut.subtitle,
                                        Modifier.weight(1f)
                                    ) { addingEvent = shortcut.eventType }
                                }
                                if (rowShortcuts.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (baby != null) {
                    if (page == 0) item {
                        TodaySummary(todayEvents) { category ->
                            selectedEventFilter = category
                            selectedNav = 1
                        }
                    }
                    if (page == 0) item {
                        SleepTimerCard { startedAt, endedAt, durationMinutes ->
                            val now = System.currentTimeMillis()
                            val payload = JSONObject()
                                .put("durationMinutes", durationMinutes)
                                .put("startedAt", startedAt)
                                .put("endedAt", endedAt)
                                .put("source", "timer")
                            val event = BabyEventEntity(UUID.randomUUID().toString(), baby.id, "SLEEP", startedAt, payload.toString(), null, now)
                            scope.launch {
                                dao.upsertEvent(event)
                                if (SyncPreferences.familyId(appContext).isNotBlank()) {
                                    dao.enqueueOperation(SyncOperationEntity(UUID.randomUUID().toString(), "event", event.id, "upsert", eventSyncPayload(event).toString(), event.serverVersion, now))
                                    SyncScheduler.enqueue(appContext)
                                }
                            }
                        }
                    }

                    if (page == 2) item { TrendsSummary(baby.id, dao) }
                    if (page == 2) item { GrowthSection(baby, growthEvents) }
                    if (page == 2) item {
                        GrowthMilestonesSection(baby, milestoneEvents) { milestone ->
                            val now = System.currentTimeMillis()
                            val payload = JSONObject()
                                .put("milestoneId", milestone.id)
                                .put("milestoneTitle", milestone.title)
                                .put("category", milestone.category)
                            val event = BabyEventEntity(
                                UUID.randomUUID().toString(), baby.id, "MILESTONE",
                                now, payload.toString(), null, now
                            )
                            scope.launch {
                                dao.upsertEvent(event)
                                if (SyncPreferences.familyId(appContext).isNotBlank()) {
                                    dao.enqueueOperation(
                                        SyncOperationEntity(
                                            UUID.randomUUID().toString(), "event", event.id, "upsert",
                                            eventSyncPayload(event).toString(), event.serverVersion, now
                                        )
                                    )
                                    SyncScheduler.enqueue(appContext)
                                }
                            }
                        }
                    }
                    if (page == 2) item { FamilyMemorySection(baby, milestoneEvents) }
                    if (page == 2) item { SolidFoodJourneySection(solidFoodEvents) }
                    if (page == 2) item {
                        GrowthReportCard(isExporting = exportingGrowthReport) {
                            scope.launch {
                                exportingGrowthReport = true
                                try {
                                    val reportEvents = withContext(Dispatchers.IO) {
                                        dao.getGrowthReportEvents(baby.id)
                                    }
                                    val reportFile = withContext(Dispatchers.IO) {
                                        createGrowthReportPdf(appContext, baby, reportEvents)
                                    }
                                    shareGrowthReport(appContext, reportFile)
                                } catch (error: Exception) {
                                    Toast.makeText(
                                        appContext,
                                        "生成成长报告失败：${error.message ?: "请稍后重试"}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } finally {
                                    exportingGrowthReport = false
                                }
                            }
                        }
                    }
                    if (page == 3) item {
                        BabyAlbumSection(
                            baby = baby,
                            photos = photos,
                            photoCount = photoCount,
                            onLoadMore = { beforeTakenAt, beforeId, limit ->
                                dao.getPhotoPageAfter(baby.id, beforeTakenAt, beforeId, limit)
                            },
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
                            initialPhoto = selectedTimelinePhoto,
                            onInitialPhotoConsumed = { selectedTimelinePhoto = null },
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
                            events = allEvents,
                            onLinkEvent = { photo, event, linked ->
                                scope.launch {
                                    val now = System.currentTimeMillis()
                                    val updated = PhotoEventAssociation.withPhotoLink(event, photo.id, linked)
                                        .copy(updatedAt = now, syncStatus = "PENDING")
                                    dao.upsertEvent(updated)
                                    if (SyncPreferences.familyId(appContext).isNotBlank()) {
                                        dao.clearPendingOperations("event", updated.id)
                                        dao.enqueueOperation(SyncOperationEntity(UUID.randomUUID().toString(), "event", updated.id, "upsert", eventSyncPayload(updated).toString(), updated.serverVersion, now))
                                        SyncScheduler.enqueue(appContext)
                                    }
                                }
                            },
                            onDelete = { photo ->
                                scope.launch {
                                    val now = System.currentTimeMillis()
                                    allEvents.filter { photo.id in PhotoEventAssociation.photoIds(it) }.forEach { event ->
                                        val updated = PhotoEventAssociation.withPhotoLink(event, photo.id, false)
                                            .copy(updatedAt = now, syncStatus = "PENDING")
                                        dao.upsertEvent(updated)
                                        if (SyncPreferences.familyId(appContext).isNotBlank()) {
                                            dao.clearPendingOperations("event", updated.id)
                                            dao.enqueueOperation(SyncOperationEntity(UUID.randomUUID().toString(), "event", updated.id, "upsert", eventSyncPayload(updated).toString(), updated.serverVersion, now))
                                        }
                                    }
                                    dao.softDeletePhoto(photo.id, now)
                                    if (SyncPreferences.familyId(appContext).isNotBlank()) {
                                        dao.clearPendingOperations("photo", photo.id)
                                        dao.enqueueOperation(SyncOperationEntity(UUID.randomUUID().toString(), "photo", photo.id, "delete", photoSyncPayload(photo, mimeTypeForPath(photo.localPath)).toString(), photo.serverVersion, now))
                                        SyncScheduler.enqueue(appContext)
                                    }
                                    withContext(Dispatchers.IO) { deletePhotoLocalArtifacts(appContext, photo) }
                                }
                            }
                        )
                    }
                    if (page == 1) item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("成长足迹", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("共 ${timelineTotal} 条记录", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            FilledTonalButton(onClick = { showRecordTypePicker = true }) {
                                Text("＋ 记一笔")
                            }
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
                                if (selectedStartDate != null || selectedEndDate != null || selectedEventFilter != "全部") {
                                    TextButton(onClick = {
                                        selectedStartDate = null
                                        selectedEndDate = null
                                        selectedEventFilter = "全部"
                                    }) { Text("清除筛选") }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { selectedStartDate = LocalDate.now(); selectedEndDate = LocalDate.now() }) { Text("今天") }
                                TextButton(onClick = { selectedStartDate = LocalDate.now().minusDays(6); selectedEndDate = LocalDate.now() }) { Text("近7天") }
                                TextButton(onClick = { selectedStartDate = LocalDate.now().minusDays(29); selectedEndDate = LocalDate.now() }) { Text("近30天") }
                                TextButton(onClick = {
                                    selectedStartDate = null
                                    selectedEndDate = null
                                    selectedEventFilter = "全部"
                                }) { Text("全部") }
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf("全部", "喂养", "辅食记录", "补充记录", "大小便", "睡眠", "成长测量", "成长里程碑", "成长足迹", "其他")) { category ->
                                if (selectedEventFilter == category) {
                                    FilledTonalButton(onClick = { selectedEventFilter = category }) { Text(category) }
                                } else {
                                    OutlinedButton(onClick = { selectedEventFilter = category }) { Text(category) }
                                }
                            }
                        }
                    }
                    }
                    if (page == 1 && timelineError != null) item {
                        Card(shape = RoundedCornerShape(18.dp)) {
                            Column(
                                Modifier.fillMaxWidth().padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("暂时无法加载成长足迹", fontWeight = FontWeight.SemiBold)
                                Text(timelineError ?: "加载失败，请重试", color = MaterialTheme.colorScheme.error)
                                OutlinedButton(
                                    enabled = !timelineLoading,
                                    onClick = { timelineRetryToken++ },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("重试") }
                            }
                        }
                    }
                    if (page == 1 && timelineEvents.isEmpty() && !timelineLoading && timelineError == null) {
                        item {
                            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("🌱", style = MaterialTheme.typography.headlineLarge)
                                    val emptyState = timelineEmptyState(
                                        hasDateFilter = selectedStartDate != null || selectedEndDate != null,
                                        selectedFilter = selectedEventFilter
                                    )
                                    Text(emptyState.title, fontWeight = FontWeight.SemiBold)
                                    Text(emptyState.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else if (page == 1) itemsIndexed(timelineEvents, key = { _, event -> event.id }) { index, event ->
                        val zone = ZoneId.systemDefault()
                        val eventDay = java.time.Instant.ofEpochMilli(event.occurredAt).atZone(zone).toLocalDate()
                        val previousDay = timelineEvents.getOrNull(index - 1)?.let { java.time.Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }
                        val dayPhotos = timelinePhotosByDay[eventDay].orEmpty()
                        val isLastEventOfDay = lastTimelineEventIdByDay[eventDay] == event.id
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            if (index == 0 || previousDay != eventDay) TimelineDayHeader(eventDay, timelineDayCounts[eventDay] ?: 1)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                EventCard(event, modifier = Modifier.fillMaxWidth(), onEdit = { editingEvent = event }, onDelete = { deletingEvent = event })
                            }
                            timelineLinkedPhotos[event.id]?.let { linkedPhotos ->
                                TimelinePhotoStrip(linkedPhotos, title = "关联照片") { photo ->
                                    selectedTimelinePhoto = photo
                                    selectedNav = 3
                                }
                            }
                            if (isLastEventOfDay && dayPhotos.isNotEmpty()) {
                                TimelinePhotoStrip(dayPhotos) { photo ->
                                    selectedTimelinePhoto = photo
                                    selectedNav = 3
                                }
                            }
                        }
                    }
                    if (page == 1 && timelineLoadMoreError != null) item {
                        Card(shape = RoundedCornerShape(18.dp)) {
                            Column(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("加载更多记录失败", fontWeight = FontWeight.SemiBold)
                                Text(timelineLoadMoreError ?: "加载失败，请重试", color = MaterialTheme.colorScheme.error)
                                OutlinedButton(
                                    enabled = !timelineLoading,
                                    onClick = { scope.launch { loadMoreTimelinePage() } },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("重试加载更多") }
                            }
                        }
                    }
                    if (page == 1 && timelineEvents.size < timelineTotal) item {
                        OutlinedButton(
                            enabled = !timelineLoading,
                            onClick = { scope.launch { loadMoreTimelinePage() } },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (timelineLoading) "正在加载…" else "加载更多记录（已显示 ${timelineEvents.size} / ${timelineTotal}）") }
                    }
                    if (page == 4) item {
                        val syncConfigured = SyncPreferences.familyId(appContext).isNotBlank()
                        val syncStatus = when {
                            !syncConfigured -> "未配置"
                            syncing -> "同步中"
                            !syncError.isNullOrBlank() -> "需检查"
                            lastSyncAt > 0L -> "正常"
                            else -> "待首次同步"
                        }
                        val syncDescription = when {
                            !syncConfigured -> "配置家庭空间，在设备间安全同步记录"
                            !syncError.isNullOrBlank() -> syncError ?: "同步遇到问题，请检查设置"
                            lastSyncAt > 0L -> "最近同步：" + SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(lastSyncAt))
                            else -> "家庭空间已连接，等待首次同步"
                        }
                        MorePageHeader(
                            syncStatus = syncStatus,
                            syncDescription = syncDescription,
                            syncHasError = !syncError.isNullOrBlank(),
                            onOpenSyncSettings = { showSyncSettings = true }
                        )
                    }
                    if (page == 4) item { VaccinePage(vaccineEvents, birthDate = java.time.Instant.ofEpochMilli(baby.birthTime).atZone(ZoneId.systemDefault()).toLocalDate(), sex = baby.sex, onSave = { existing, payload, timestamp, onComplete ->
                        val now = System.currentTimeMillis()
                        scope.launch {
                            val vaccine = existing?.copy(occurredAt = timestamp, payloadJson = payload.toString(), updatedAt = now, syncStatus = "PENDING")
                                ?: BabyEventEntity(UUID.randomUUID().toString(), baby.id, "VACCINE", timestamp, payload.toString(), null, now)
                            try {
                                dao.upsertEvent(vaccine)
                            } catch (error: Exception) {
                                Toast.makeText(appContext, "保存接种记录失败，请稍后重试", Toast.LENGTH_LONG).show()
                                onComplete(false)
                                return@launch
                            }
                            onComplete(true)
                            if (SyncPreferences.familyId(appContext).isNotBlank()) {
                                try {
                                    dao.clearPendingOperations("event", vaccine.id)
                                    dao.enqueueOperation(SyncOperationEntity(UUID.randomUUID().toString(), "event", vaccine.id, "upsert", eventSyncPayload(vaccine).toString(), vaccine.serverVersion, now))
                                    SyncScheduler.enqueue(appContext)
                                } catch (syncError: Exception) {
                                    Toast.makeText(appContext, "接种记录已保存到本机，但同步任务创建失败，请稍后重试", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }, onDelete = { event -> scope.launch {
                        val now = System.currentTimeMillis()
                        try {
                            dao.softDeleteEvent(event.id, now)
                        } catch (error: Exception) {
                            Toast.makeText(appContext, "删除接种记录失败，请稍后重试", Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        if (SyncPreferences.familyId(appContext).isNotBlank()) {
                            try {
                                val deleted = dao.getEvent(event.id)
                                if (deleted != null) {
                                    dao.clearPendingOperations("event", deleted.id)
                                    dao.enqueueOperation(SyncOperationEntity(UUID.randomUUID().toString(), "event", deleted.id, "delete", eventSyncPayload(deleted).toString(), deleted.serverVersion, now))
                                    SyncScheduler.enqueue(appContext)
                                }
                            } catch (syncError: Exception) {
                                Toast.makeText(appContext, "接种记录已在本机删除，但同步任务创建失败，请稍后重试", Toast.LENGTH_LONG).show()
                            }
                        }
                    } }) }
                } else if (page == 0) item { Text("创建宝宝档案后，就可以开始记录每天的生活点滴。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                        try {
                            dao.softDeleteEvent(target.id, now)
                            if (SyncPreferences.familyId(appContext).isNotBlank()) {
                                try {
                                    val deleted = dao.getEvent(target.id)
                                    if (deleted != null) {
                                        dao.clearPendingOperations("event", deleted.id)
                                        dao.enqueueOperation(SyncOperationEntity(UUID.randomUUID().toString(), "event", deleted.id, "delete", eventSyncPayload(deleted).toString(), deleted.serverVersion, now))
                                        SyncScheduler.enqueue(appContext)
                                    }
                                } catch (syncError: Exception) {
                                    Toast.makeText(appContext, "记录已在本机删除，但同步任务创建失败，请稍后重试", Toast.LENGTH_LONG).show()
                                }
                            }
                            deletingEvent = null
                        } catch (error: Exception) {
                            Toast.makeText(appContext, "删除记录失败，请稍后重试", Toast.LENGTH_LONG).show()
                        }
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

    if (showRecordTypePicker) {
        val recordTypes = listOf(
            "FEEDING" to "喂养",
            "DIAPER" to "日常护理",
            "SLEEP" to "睡眠",
            "SOLID_FOOD" to "辅食",
            "SUPPLEMENT" to "补充",
            "GROWTH" to "成长测量"
        )
        AlertDialog(
            onDismissRequest = { showRecordTypePicker = false },
            title = { Text("选择记录类型") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recordTypes.chunked(2).forEach { rowTypes ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowTypes.forEach { (type, label) ->
                                FilledTonalButton(
                                    onClick = {
                                        showRecordTypePicker = false
                                        addingEvent = type
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text(label) }
                            }
                            if (rowTypes.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRecordTypePicker = false }) { Text("取消") }
            }
        )
    }

    if ((addingEvent != null || editingEvent != null) && baby != null) {
        val currentEvent = editingEvent
        EventEditor(currentEvent?.eventType ?: addingEvent!!, existing = currentEvent, onDismiss = { if (!savingEvent) { addingEvent = null; editingEvent = null } }, saving = savingEvent) { type, payload, occurredAt, keepOpen ->
            if (savingEvent) return@EventEditor
            savingEvent = true
            val now = System.currentTimeMillis()
            scope.launch {
                try {
                    val event = currentEvent?.copy(
                        eventType = type, occurredAt = occurredAt, payloadJson = payload.toString(),
                        updatedAt = now, syncStatus = "PENDING", deletedAt = null
                    ) ?: BabyEventEntity(UUID.randomUUID().toString(), baby.id, type, occurredAt, payload.toString(), null, now)
                    dao.upsertEvent(event)
                    if (SyncPreferences.familyId(appContext).isNotBlank()) {
                        try {
                            dao.clearPendingOperations("event", event.id)
                            dao.enqueueOperation(SyncOperationEntity(UUID.randomUUID().toString(), "event", event.id, "upsert", eventSyncPayload(event).toString(), event.serverVersion, now))
                            SyncScheduler.enqueue(appContext)
                        } catch (syncError: Exception) {
                            Toast.makeText(appContext, "记录已保存到本机，但同步任务创建失败，请稍后重试", Toast.LENGTH_LONG).show()
                        }
                    }
                    if (!keepOpen) {
                        addingEvent = null
                        editingEvent = null
                    }
                } catch (error: Exception) {
                    Toast.makeText(appContext, "保存记录失败，请检查后重试", Toast.LENGTH_LONG).show()
                } finally {
                    savingEvent = false
                }
            }
        }
    }
    }
}

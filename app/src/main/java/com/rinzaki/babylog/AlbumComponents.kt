package com.rinzaki.babylog

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.util.LruCache
import android.content.ContentValues
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rinzaki.babylog.data.BabyEntity
import com.rinzaki.babylog.data.BabyPhotoEntity
import com.rinzaki.babylog.sync.mimeTypeForPath
import com.rinzaki.babylog.sync.SyncPreferences
import com.rinzaki.babylog.sync.SyncHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun BabyAlbumSection(
    baby: BabyEntity,
    photos: List<BabyPhotoEntity>,
    photoCount: Int,
    onLoadMore: suspend (beforeTakenAt: Long, beforeId: String, limit: Int) -> List<BabyPhotoEntity>,
    onAdd: (android.net.Uri, Long) -> Unit,
    onCaption: (BabyPhotoEntity, String) -> Unit,
    onDelete: (BabyPhotoEntity) -> Unit,
    initialPhoto: BabyPhotoEntity? = null,
    onInitialPhotoConsumed: () -> Unit = {}
) {
    var deletingPhoto by remember { mutableStateOf<BabyPhotoEntity?>(null) }
    var viewingPhoto by remember { mutableStateOf<BabyPhotoEntity?>(null) }
    var photoCacheRefreshKey by remember { mutableStateOf(0) }
    LaunchedEffect(viewingPhoto) {
        if (viewingPhoto == null) photoCacheRefreshKey++
    }
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
    var extraPhotos by remember(baby.id, photoCount) { mutableStateOf<List<BabyPhotoEntity>>(emptyList()) }
    var photoPageCursor by remember(baby.id) { mutableStateOf<BabyPhotoEntity?>(null) }
    var loadingMorePhotos by remember { mutableStateOf(false) }
    val loadedPhotos = remember(photos, extraPhotos, initialPhoto) {
        (photos + extraPhotos + listOfNotNull(initialPhoto)).distinctBy { it.id }
            .sortedWith(compareByDescending<BabyPhotoEntity> { it.takenAt }.thenByDescending { it.id })
    }
    LaunchedEffect(photos) {
        extraPhotos = emptyList()
        photoPageCursor = photos.lastOrNull()
    }
    LaunchedEffect(initialPhoto?.id) {
        initialPhoto?.let { target ->
            extraPhotos = (extraPhotos + target).distinctBy { it.id }
            albumFilter = "全部"
            selectedPhotoDate = null
            viewingPhoto = target
            onInitialPhotoConsumed()
        }
    }
    val filteredPhotos = loadedPhotos.filter { photo ->
        val date = java.time.Instant.ofEpochMilli(photo.takenAt).atZone(ZoneId.systemDefault()).toLocalDate()
        when (albumFilter) {
            "近7天" -> photo.takenAt >= System.currentTimeMillis() - 7L * 86400000L
            "近30天" -> photo.takenAt >= System.currentTimeMillis() - 30L * 86400000L
            "本月" -> date.year == LocalDate.now().year && date.month == LocalDate.now().month
            "指定日期" -> selectedPhotoDate == date
            else -> true
        }
    }
    val sortedFilteredPhotos = remember(filteredPhotos) { filteredPhotos.sortedByDescending { it.takenAt } }
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
                    "共 ${photoCount} 张照片 · 按拍摄日期排列",
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.PhoneAndroid, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("本地原图", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.Cached, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("已缓存", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("云端原图", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (albumFilter == "指定日期" && selectedPhotoDate != null) {
            Text("已筛选：${selectedPhotoDate!!.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        if (photoCount == 0) {
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
            val visiblePhotos = sortedFilteredPhotos
            val grouped = visiblePhotos.groupBy {
                SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(it.takenAt))
            }
            if (filteredPhotos.isEmpty()) Text(
                if (loadedPhotos.size < photoCount) "当前已加载的照片中暂无匹配结果，可以继续加载更早的照片。" else "这个日期范围内还没有照片。",
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                                        cacheRefreshKey = photoCacheRefreshKey,
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
            if (filteredPhotos.isEmpty() && loadedPhotos.size < photoCount) {
                Text("当前已加载的照片中暂无匹配结果，可以继续加载更早的照片。", modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            if (loadedPhotos.size < photoCount) {
                OutlinedButton(
                    onClick = {
                        albumScope.launch {
                            loadingMorePhotos = true
                            val cursor = photoPageCursor
                            if (cursor != null) {
                                runCatching { onLoadMore(cursor.takenAt, cursor.id, 60) }
                                    .onSuccess { page ->
                                        extraPhotos = (extraPhotos + page).distinctBy { it.id }
                                        if (page.isNotEmpty()) photoPageCursor = page.last()
                                    }
                                    .onFailure { galleryMessage = "加载照片失败：${it.message ?: "请重试"}" }
                            }
                            loadingMorePhotos = false
                        }
                    },
                    enabled = !loadingMorePhotos && photoPageCursor != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (loadingMorePhotos) "正在加载…" else "加载更多照片（已加载 ${loadedPhotos.size}/${photoCount} 张）")
                }
            }
            galleryMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    viewingPhoto?.let { photo ->
        // Photo navigation follows the active album filter, matching the visible timeline.
        val currentPhotoIndex = sortedFilteredPhotos.indexOfFirst { it.id == photo.id }
        val viewerScope = rememberCoroutineScope()
        // The opening transition belongs to the viewer session, not each pager page.
        // Re-keying these Animatables by photo.id caused a fade/flash after every swipe.
        val openingScale = remember { Animatable(0.96f) }
        val openingAlpha = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            launch { openingScale.animateTo(1f, animationSpec = tween(260)) }
            openingAlpha.animateTo(1f, animationSpec = tween(220))
        }
        val closeViewer: () -> Unit = {
            viewerScope.launch {
                launch { openingScale.animateTo(0.97f, animationSpec = tween(160)) }
                openingAlpha.animateTo(0f, animationSpec = tween(160))
                viewingPhoto = null
            }
        }
        Dialog(
            onDismissRequest = closeViewer,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            var showViewerChrome by remember(photo.id) { mutableStateOf(true) }
            var zoom by remember(photo.id) { mutableStateOf(1f) }
            val zoomAnimation = remember(photo.id) { Animatable(1f) }
            var panX by remember(photo.id) { mutableStateOf(0f) }
            var panY by remember(photo.id) { mutableStateOf(0f) }
            var viewportSize by remember(photo.id) { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
            val pagerState = rememberPagerState(
                initialPage = currentPhotoIndex.coerceAtLeast(0),
                pageCount = { sortedFilteredPhotos.size }
            )
            val activePhoto = sortedFilteredPhotos.getOrNull(pagerState.currentPage) ?: photo
            val photoBitmapCache = remember { mutableStateMapOf<String, android.graphics.Bitmap>() }
            val photoOriginalCache = remember { mutableStateMapOf<String, String>() }

            // Reset gesture state only after a page has settled, and keep the pan bounds
            // derived from the active page's bitmap to avoid a stale/null bitmap race.
            LaunchedEffect(pagerState, sortedFilteredPhotos) {
                snapshotFlow { pagerState.settledPage }
                    .distinctUntilChanged()
                    .collect { page ->
                        sortedFilteredPhotos.getOrNull(page)?.let { settledPhoto ->
                            if (viewingPhoto?.id != settledPhoto.id) viewingPhoto = settledPhoto
                            zoom = 1f
                            panX = 0f
                            panY = 0f
                        }
                    }
            }

            // Decode the visible page and both neighbors concurrently as soon as the pager
            // starts moving, rather than waiting for viewingPhoto/currentPhotoIndex to update.
            LaunchedEffect(pagerState.currentPage, sortedFilteredPhotos) {
                val nearbyIndices = (pagerState.currentPage - 1..pagerState.currentPage + 1)
                    .filter { it in sortedFilteredPhotos.indices }
                val nearbyIds = nearbyIndices.map { sortedFilteredPhotos[it].id }.toSet()
                photoBitmapCache.keys.toList().filterNot { it in nearbyIds }.forEach { photoBitmapCache.remove(it) }
                nearbyIndices.forEach { index ->
                    val neighbor = sortedFilteredPhotos[index]
                    if (neighbor.id !in photoBitmapCache) {
                        launch {
                            val bitmap = withContext(Dispatchers.IO) {
                                runCatching { decodeSampledPhoto(neighbor.localPath, 1600, 2000) }.getOrNull()
                            }
                            if (bitmap != null && neighbor.id !in photoOriginalCache) photoBitmapCache[neighbor.id] = bitmap
                        }
                    }
                }
            }
            // Cloud-only photos show their local preview immediately, then fetch the
            // original in the background for full-resolution viewing when online.
            LaunchedEffect(activePhoto.id, activePhoto.originalOnDevice) {
                if (!activePhoto.originalOnDevice) {
                    val cachedPath = photoOriginalCache[activePhoto.id]?.takeIf { File(it).isFile }
                    val base = SyncPreferences.server(context)
                    val token = SyncPreferences.token(context)
                    val familyId = SyncPreferences.familyId(context)
                    val destination = photoOriginalCacheFile(context.cacheDir, activePhoto)
                    if (cachedPath != null) withContext(Dispatchers.IO) {
                        File(cachedPath).setLastModified(System.currentTimeMillis())
                    }
                    val sourcePath = cachedPath ?: if (base.isNotBlank() && token.isNotBlank() && familyId.isNotBlank()) {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                if (!destination.isFile || destination.length() <= 0L) {
                                    SyncHttp.downloadPhoto(base, familyId, activePhoto.id, token, destination)
                                }
                                destination.setLastModified(System.currentTimeMillis())
                                trimPhotoOriginalCache(context.cacheDir, destination)
                                destination.absolutePath
                            }.getOrNull()
                        }
                    } else null
                    if (sourcePath != null) {
                        photoOriginalCache[activePhoto.id] = sourcePath
                        val fullBitmap = withContext(Dispatchers.IO) {
                            runCatching { decodeSampledPhoto(sourcePath, 2400, 3000) }.getOrNull()
                        }
                        if (fullBitmap != null) photoBitmapCache[activePhoto.id] = fullBitmap
                    }
                }
            }
            val activeBitmap = photoBitmapCache[activePhoto.id]
            fun clampPhotoPan(nextZoom: Float, x: Float, y: Float): Pair<Float, Float> {
                val image = activeBitmap
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
            Surface(
                shape = RoundedCornerShape(0.dp),
                color = Color.Black,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = openingScale.value
                    scaleY = openingScale.value
                    alpha = openingAlpha.value
                }
            ) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(58.dp)) {
                    this@Column.AnimatedVisibility(
                        visible = showViewerChrome,
                        enter = fadeIn(animationSpec = tween(180)),
                        exit = fadeOut(animationSpec = tween(160))
                    ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            AnimatedContent(
                                targetState = activePhoto.takenAt,
                                transitionSpec = {
                                    (slideInHorizontally(animationSpec = tween(180)) { it / 5 } + fadeIn(tween(180))) togetherWith
                                        (slideOutHorizontally(animationSpec = tween(180)) { -it / 5 } + fadeOut(tween(180)))
                                },
                                label = "photo-date-transition"
                            ) { targetTakenAt ->
                                Column {
                                    Text(SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(targetTakenAt)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(SimpleDateFormat("HH时mm分", Locale.CHINA).format(Date(targetTakenAt)), style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                                }
                            }
                        }
                        Text("${pagerState.currentPage + 1} / ${sortedFilteredPhotos.size}", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                        TextButton(onClick = { zoom = 1f; panX = 0f; panY = 0f }) { Text("适应屏幕", color = Color.White) }
                        TextButton(onClick = closeViewer) { Text("关闭", color = Color.White) }
                    }
                    }
                    }
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize().onSizeChanged { viewportSize = it },
                        beyondViewportPageCount = 1,
                        userScrollEnabled = zoom <= 1.01f,
                        key = { page -> sortedFilteredPhotos[page].id }
                    ) { page ->
                        val pagePhoto = sortedFilteredPhotos[page]
                        val pageBitmap = photoBitmapCache[pagePhoto.id]
                        Box(
                            Modifier.fillMaxSize().clipToBounds()
                                .then(
                                    if (page == pagerState.currentPage) {
                                        Modifier
                                            .pointerInput(pagePhoto.id) {
                                                detectTapGestures(
                                                    onTap = { showViewerChrome = !showViewerChrome },
                                                    onDoubleTap = {
                                                        val targetZoom = if (zoom > 1.01f) 1f else 2.5f
                                                        viewerScope.launch {
                                                            zoomAnimation.stop()
                                                            zoomAnimation.snapTo(zoom)
                                                            zoomAnimation.animateTo(
                                                                targetZoom,
                                                                animationSpec = spring(
                                                                    dampingRatio = 0.82f,
                                                                    stiffness = 280f
                                                                )
                                                            ) {
                                                                zoom = value
                                                                val bounded = clampPhotoPan(value, panX, panY)
                                                                panX = bounded.first
                                                                panY = if (value <= 1.01f) 0f else bounded.second
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                            .transformable(zoomState, canPan = { zoom > 1.01f })
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (pageBitmap != null) {
                                Image(
                                    pageBitmap.asImageBitmap(),
                                    contentDescription = "宝宝照片，可双指缩放、拖动查看，双击放大",
                                    modifier = Modifier.fillMaxSize().graphicsLayer {
                                        // Neighbor pages are decoded ahead of time, but must
                                        // never inherit the active page's zoom/pan transform.
                                        val isActivePage = page == pagerState.currentPage
                                        scaleX = if (isActivePage) zoom else 1f
                                        scaleY = if (isActivePage) zoom else 1f
                                        translationX = if (isActivePage) panX else 0f
                                        translationY = if (isActivePage) panY else 0f
                                    },
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Text(
                                    if (pagePhoto.id !in photoBitmapCache) "正在加载照片…" else "照片暂时无法读取",
                                    color = Color.White
                                )
                            }
                        }
                    }
                        if (showViewerChrome) {
                            Text(
                                if (zoom > 1.01f) "轻触照片隐藏工具栏 · 双指缩放 · 拖动查看 · 双击还原"
                                else "轻触照片隐藏工具栏 · 左右滑动切换 · 双指缩放 · 双击放大",
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                                color = Color.LightGray,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(116.dp)) {
                    this@Column.AnimatedVisibility(
                        visible = showViewerChrome,
                        enter = fadeIn(animationSpec = tween(180)),
                        exit = fadeOut(animationSpec = tween(160))
                    ) {
                        Column {
                            Column(Modifier.fillMaxWidth().background(Color(0xFF171717)).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(activePhoto.caption?.takeIf { it.isNotBlank() } ?: "还没有添加照片备注", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                galleryMessage?.let { Text(it, color = Color(0xFFD7C9F4), style = MaterialTheme.typography.bodySmall) }
                            }
                            Row(Modifier.fillMaxWidth().background(Color(0xFF171717)).padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { editingCaption = activePhoto; closeViewer() }) { Text("编辑备注", color = Color.White) }
                                TextButton(onClick = {
                                    albumScope.launch {
                                        galleryMessage = runCatching {
                                            withContext(Dispatchers.IO) { savePhotoToGallery(context, photoOriginalCache[activePhoto.id] ?: activePhoto.localPath) }
                                            "已保存到手机相册"
                                        }.getOrElse { "保存失败：" + (it.message ?: "未知错误") }
                                    }
                                }) { Text("保存到手机", color = Color.White) }
                                TextButton(onClick = { deletingPhoto = activePhoto; closeViewer() }) { Text("删除", color = Color(0xFFFF8A80)) }
                            }
                        }
                    }
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
                    extraPhotos = extraPhotos.map { if (it.id == photo.id) it.copy(caption = caption.trim()) else it }
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
                    extraPhotos = extraPhotos.filterNot { it.id == photo.id }
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
            onDismissRequest = {
                showPhotoDatePicker = false
                if (selectedPhotoDate == null) albumFilter = "全部"
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedPhotoDate = datePickerState.selectedDateMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
                    showPhotoDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
            TextButton(onClick = {
                showPhotoDatePicker = false
                if (selectedPhotoDate == null) albumFilter = "全部"
            }) { Text("取消") }
        }
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
private fun photoOriginalCacheFile(cacheDir: File, photo: BabyPhotoEntity): File {
    val extension = when (photo.photoMimeType) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/heic" -> "heic"
        "image/heif" -> "heif"
        else -> "jpg"
    }
    return File(cacheDir, "babylog_photo_originals/${photo.id}.$extension")
}

private fun trimPhotoOriginalCache(cacheDir: File, keep: File) {
    val folder = File(cacheDir, "babylog_photo_originals")
    val files = folder.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }?.sortedBy { it.lastModified() } ?: return
    var total = files.sumOf { it.length() }
    val limit = 300L * 1024L * 1024L
    for (file in files) {
        if (total <= limit) break
        if (file.absolutePath == keep.absolutePath) continue
        val size = file.length()
        if (file.delete()) total -= size
    }
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

@Composable
internal fun TimelinePhotoStrip(
    photos: List<BabyPhotoEntity>,
    onPhotoClick: (BabyPhotoEntity) -> Unit
) {
    if (photos.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            "当天照片 · ${photos.size} 张",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            photos.take(6).forEach { photo ->
                AlbumPhotoCard(
                    photo = photo,
                    cacheRefreshKey = 0,
                    onClick = { onPhotoClick(photo) },
                    modifier = Modifier.width(66.dp),
                    height = 66.dp
                )
            }
            if (photos.size > 6) {
                Box(
                    Modifier.size(width = 66.dp, height = 66.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+${photos.size - 6}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Shared thumbnail cache for the album grid.
 *
 * The cache is byte-bounded (rather than item-count-bounded), and decoding is
 * limited to two concurrent jobs so opening a large album cannot launch dozens
 * of full disk decodes at once. Cache keys include file metadata so replacing a
 * managed photo at the same path cannot keep showing a stale thumbnail.
 */
internal object AlbumPhotoThumbnailCache {
    private const val MAX_CACHE_BYTES = 12 * 1024 * 1024
    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount.coerceAtLeast(1)
    }
    private val decodeSlots = Semaphore(2)

    suspend fun load(path: String, reqWidth: Int, reqHeight: Int): Bitmap? =
        decodeSlots.withPermit {
            val file = File(path)
            if (!file.isFile || file.length() <= 0L || reqWidth <= 0 || reqHeight <= 0) {
                return@withPermit null
            }
            val key = listOf(
                file.absolutePath,
                file.length().toString(),
                file.lastModified().toString(),
                reqWidth.toString(),
                reqHeight.toString()
            ).joinToString("|")
            synchronized(cache) { cache.get(key) }?.let { return@withPermit it }

            val decoded = withContext(Dispatchers.IO) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    null
                } else {
                    var sampleSize = 1
                    while (bounds.outWidth / sampleSize > reqWidth * 2 ||
                        bounds.outHeight / sampleSize > reqHeight * 2
                    ) {
                        sampleSize *= 2
                    }
                    BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.RGB_565
                    })
                }
            } ?: return@withPermit null

            synchronized(cache) {
                cache.get(key) ?: decoded.also { cache.put(key, it) }
            }
        }

    internal fun clearForTests() {
        synchronized(cache) { cache.evictAll() }
    }
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
    cacheRefreshKey: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 124.dp
) {
    val context = LocalContext.current
    val hasCachedOriginal = remember(photo.id, photo.originalOnDevice, cacheRefreshKey) {
        !photo.originalOnDevice && photoOriginalCacheFile(context.cacheDir, photo).isFile
    }
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null,
        photo.localPath,
        photo.updatedAt
    ) {
        value = runCatching {
            AlbumPhotoThumbnailCache.load(photo.localPath, 360, 360)
        }.getOrNull()
    }
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF2EDF7))
    ) {
        Box(Modifier.fillMaxWidth().height(height)) {
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
            // Keep one compact storage badge in the lower-right corner; the date
            // remains available in the timeline and full-screen viewer.
            val storageIcon = when {
                photo.originalOnDevice -> Icons.Filled.PhoneAndroid
                hasCachedOriginal -> Icons.Filled.Cached
                else -> Icons.Filled.Cloud
            }
            Box(
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(19.dp)
                    .background(Color.Black.copy(alpha = 0.52f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = storageIcon,
                    contentDescription = when {
                        photo.originalOnDevice -> "本地原图"
                        hasCachedOriginal -> "已缓存原图"
                        else -> "云端原图"
                    },
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

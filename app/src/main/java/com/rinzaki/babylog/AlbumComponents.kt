package com.rinzaki.babylog

import android.graphics.BitmapFactory
import android.content.ContentValues
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.animation.AnimatedContent
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    onLoadMore: suspend (offset: Int, limit: Int) -> List<BabyPhotoEntity>,
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
    var extraPhotos by remember(baby.id, photoCount) { mutableStateOf<List<BabyPhotoEntity>>(emptyList()) }
    var loadingMorePhotos by remember { mutableStateOf(false) }
    val loadedPhotos = remember(photos, extraPhotos) {
        (photos + extraPhotos).distinctBy { it.id }.sortedWith(compareByDescending<BabyPhotoEntity> { it.takenAt }.thenByDescending { it.id })
    }
    LaunchedEffect(photos) { extraPhotos = emptyList() }
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
                            runCatching { onLoadMore(loadedPhotos.size, 60) }
                                .onSuccess { page -> extraPhotos = (extraPhotos + page).distinctBy { it.id } }
                                .onFailure { galleryMessage = "加载照片失败：${it.message ?: "请重试"}" }
                            loadingMorePhotos = false
                        }
                    },
                    enabled = !loadingMorePhotos,
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
        val openingScale = remember(photo.id) { Animatable(0.96f) }
        val openingAlpha = remember(photo.id) { Animatable(0f) }
        LaunchedEffect(photo.id) {
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
            var zoom by remember(photo.id) { mutableStateOf(1f) }
            var panX by remember(photo.id) { mutableStateOf(0f) }
            var panY by remember(photo.id) { mutableStateOf(0f) }
            var viewportSize by remember(photo.id) { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
            var bitmapForPan by remember(photo.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
            fun clampPhotoPan(nextZoom: Float, x: Float, y: Float): Pair<Float, Float> {
                val image = bitmapForPan
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
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            AnimatedContent(
                                targetState = photo.takenAt,
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
                        Text("${currentPhotoIndex + 1} / ${sortedFilteredPhotos.size}", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                        TextButton(onClick = { zoom = 1f; panX = 0f; panY = 0f }) { Text("适应屏幕", color = Color.White) }
                        TextButton(onClick = closeViewer) { Text("关闭", color = Color.White) }
                    }
                    Box(
                        Modifier.fillMaxWidth().weight(1f)
                            .onSizeChanged { viewportSize = it }
                            .pointerInput(photo.id) {
                                detectTapGestures(onDoubleTap = {
                                    if (zoom > 1f) { zoom = 1f; panX = 0f; panY = 0f } else { zoom = 2.5f; val bounded = clampPhotoPan(zoom, panX, panY); panX = bounded.first; panY = bounded.second }
                                })
                            }
                            .transformable(zoomState)
                            // Keep the swipe detector inside the transformable modifier in the
                            // pointer-input chain. Otherwise transformable consumes horizontal
                            // drag changes before the gallery navigation detector can observe them.
                            .pointerInput(photo.id, zoom) {
                                if (zoom <= 1.01f) {
                                    var horizontalTravel = 0f
                                    detectHorizontalDragGestures(
                                        onHorizontalDrag = { _, dragAmount -> horizontalTravel += dragAmount },
                                        onDragEnd = {
                                            photoIndexAfterHorizontalSwipe(
                                                currentIndex = currentPhotoIndex,
                                                horizontalDragPx = horizontalTravel,
                                                thresholdPx = 72.dp.toPx(),
                                                photoCount = sortedFilteredPhotos.size
                                            )?.let { targetIndex ->
                                                viewingPhoto = sortedFilteredPhotos.getOrNull(targetIndex)
                                            }
                                            horizontalTravel = 0f
                                        },
                                        onDragCancel = { horizontalTravel = 0f }
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = photo.id,
                            transitionSpec = {
                                val initialIndex = sortedFilteredPhotos.indexOfFirst { it.id == initialState }
                                val targetIndex = sortedFilteredPhotos.indexOfFirst { it.id == targetState }
                                val movingForward = targetIndex > initialIndex
                                val motion = spring<IntOffset>(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                                (slideInHorizontally(animationSpec = motion) { width -> if (movingForward) width else -width } +
                                    fadeIn(tween(130))) togetherWith
                                    (slideOutHorizontally(animationSpec = motion) { width -> if (movingForward) -width else width } +
                                        fadeOut(tween(110)))
                            },
                            label = "photo-swipe-transition"
                        ) { targetPhotoId ->
                            val targetPhoto = sortedFilteredPhotos.firstOrNull { it.id == targetPhotoId } ?: photo
                            val targetBitmapResult by produceState<Result<android.graphics.Bitmap?>?>(initialValue = null, targetPhoto.localPath) {
                                value = withContext(Dispatchers.IO) {
                                    runCatching { decodeSampledPhoto(targetPhoto.localPath, 1600, 2000) }
                                }
                            }
                            val targetBitmap = targetBitmapResult?.getOrNull()
                            LaunchedEffect(targetPhotoId, targetBitmap) {
                                if (targetBitmap != null) bitmapForPan = targetBitmap
                            }
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (targetBitmap != null) {
                                    Image(
                                        targetBitmap.asImageBitmap(),
                                        contentDescription = "宝宝照片，可双指缩放、拖动查看，双击放大",
                                        modifier = Modifier.fillMaxSize().graphicsLayer {
                                            scaleX = zoom
                                            scaleY = zoom
                                            translationX = panX
                                            translationY = panY
                                        },
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Text(
                                        if (targetBitmapResult == null) "正在加载照片…" else "照片暂时无法读取",
                                        color = Color.White
                                    )
                                }
                            }
                        }
                        Text(
                            if (zoom > 1.01f) "双指缩放 · 拖动查看 · 双击还原" else "左右滑动切换 · 双指缩放 · 双击放大",
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                            color = Color.LightGray,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Column(Modifier.fillMaxWidth().background(Color(0xFF171717)).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(photo.caption?.takeIf { it.isNotBlank() } ?: "还没有添加照片备注", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        galleryMessage?.let { Text(it, color = Color(0xFFD7C9F4), style = MaterialTheme.typography.bodySmall) }
                    }
                    Row(Modifier.fillMaxWidth().background(Color(0xFF171717)).padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { editingCaption = photo; closeViewer() }) { Text("编辑备注", color = Color.White) }
                        TextButton(onClick = {
                            albumScope.launch {
                                galleryMessage = runCatching {
                                    withContext(Dispatchers.IO) { savePhotoToGallery(context, photo.localPath) }
                                    "已保存到手机相册"
                                }.getOrElse { "保存失败：${it.message}" }
                            }
                        }) { Text("保存到手机", color = Color.White) }
                        TextButton(onClick = { deletingPhoto = photo; closeViewer() }) { Text("删除", color = Color(0xFFFF8A80)) }
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

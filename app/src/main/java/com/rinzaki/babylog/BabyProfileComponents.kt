package com.rinzaki.babylog

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import com.rinzaki.babylog.data.BabyEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun BabyProfileEditor(
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
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
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

internal suspend fun persistBabyAvatar(context: android.content.Context, source: String?, babyId: String): String? {
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
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(size.dp)) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), contentDescription = "宝宝头像", modifier = Modifier.fillMaxSize())
        else Box(contentAlignment = Alignment.Center) { Text("👶", style = MaterialTheme.typography.headlineMedium) }
    }
}

@Composable
internal fun BabyProfileCard(baby: BabyEntity, onEdit: () -> Unit) {
    val birthDate = java.time.Instant.ofEpochMilli(baby.birthTime).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    val days = java.time.temporal.ChronoUnit.DAYS.between(birthDate, today).coerceAtLeast(0) + 1
    val months = java.time.temporal.ChronoUnit.MONTHS.between(birthDate, today).coerceAtLeast(0)
    val ageText = if (months < 1) "第 $days 天" else "$months 个月"
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
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
    Column(modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), RoundedCornerShape(11.dp)).padding(vertical = 5.dp, horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}



package com.rinzaki.babylog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rinzaki.babylog.data.BabyEventEntity
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun EventEditor(eventType: String, existing: BabyEventEntity? = null, onDismiss: () -> Unit, saving: Boolean = false, onSave: (String, JSONObject, Long, Boolean) -> Unit) {
    val context = LocalContext.current.applicationContext
    val editorStateKey = existing?.id ?: "new"
    val initialPayload = remember(editorStateKey) { runCatching { JSONObject(existing?.payloadJson ?: "{}") }.getOrElse { JSONObject() } }
    val existingFeedingMethod = initialPayload.optString("method", "配方奶").ifBlank { "配方奶" }
    val enabledFeedingMethods = remember { FeedingPreferences.enabledMethods(context) }
    val feedingOptions = remember(editorStateKey, enabledFeedingMethods) {
        (enabledFeedingMethods + if (existing != null && existingFeedingMethod in listOf("母乳", "配方奶", "混合喂养")) listOf(existingFeedingMethod) else emptyList())
            .distinct().ifEmpty { listOf("配方奶") }
    }
    val defaultFeedingMethod = remember(editorStateKey, feedingOptions) {
        resolveDefaultFeedingMethod(
            if (existing == null) null else existingFeedingMethod,
            FeedingPreferences.lastMethod(context),
            feedingOptions
        )
    }
    val initialTime = remember(editorStateKey) {
        existing?.occurredAt?.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() } ?: LocalDateTime.now()
    }
    var date by remember(existing?.id) { mutableStateOf(initialTime.toLocalDate()) }
    var hour by remember(existing?.id) { mutableStateOf(initialTime.hour) }
    var minute by remember(existing?.id) { mutableStateOf(initialTime.minute) }
    var amountText by remember(editorStateKey, defaultFeedingMethod) {
        mutableStateOf(if (existing == null) FeedingPreferences.lastAmountMl(context, defaultFeedingMethod) else initialPayload.optInt("amountMl").takeIf { it > 0 }?.toString().orEmpty())
    }
    var feedingMethod by remember(editorStateKey) { mutableStateOf(defaultFeedingMethod) }
    var breastMinutes by remember(editorStateKey) {
        mutableStateOf(if (existing == null) FeedingPreferences.lastBreastMinutes(context) else initialPayload.optInt("breastMinutes").takeIf { it > 0 }?.toString().orEmpty())
    }
    var urine by remember(existing?.id) { mutableStateOf(if (existing == null) RecordPreferences.getBoolean(context, RecordPreferences.DIAPER_URINE, true) else initialPayload.optBoolean("urine")) }
    var stool by remember(existing?.id) { mutableStateOf(if (existing == null) RecordPreferences.getBoolean(context, RecordPreferences.DIAPER_STOOL, false) else initialPayload.optBoolean("stool")) }
    var stoolShape by remember(existing?.id) {
        mutableStateOf(
            initialPayload.optString("stoolShape", if (existing == null) RecordPreferences.get(context, RecordPreferences.DIAPER_SHAPE, "糊状") else "糊状")
                .let { if (it == "软便") "成形" else it }
        )
    }
    var stoolColor by remember(existing?.id) {
        mutableStateOf(
            initialPayload.optString("stoolColor", if (existing == null) RecordPreferences.get(context, RecordPreferences.DIAPER_COLOR, "黄色") else "黄色")
                .let { if (it == "棕色") "其他" else if (it in listOf("黄色", "绿色", "红色", "其他")) it else "其他" }
        )
    }
    var stoolAmount by remember(existing?.id) { mutableStateOf(initialPayload.optString("stoolAmount", if (existing == null) RecordPreferences.get(context, RecordPreferences.DIAPER_AMOUNT, "中量") else "中量")) }
    var sleepMinutesText by remember(existing?.id) { mutableStateOf((if (existing == null) RecordPreferences.get(context, RecordPreferences.SLEEP_DURATION, "60").toIntOrNull() ?: 60 else initialPayload.optInt("durationMinutes", 60)).toString()) }
    var weightKg by remember(existing?.id) { mutableStateOf(initialPayload.optInt("weightGrams").takeIf { it > 0 }?.div(1000.0)?.toString().orEmpty()) }
    var lengthCm by remember(existing?.id) { mutableStateOf(initialPayload.optDouble("lengthCm").takeIf { it > 0 }?.toString().orEmpty()) }
    var headCm by remember(existing?.id) { mutableStateOf(initialPayload.optDouble("headCircumferenceCm").takeIf { it > 0 }?.toString().orEmpty()) }
    var temperatureC by remember(existing?.id) { mutableStateOf(initialPayload.optDouble("temperatureC").let { value -> if (value > 0) value.toString() else "" }) }
    var temperatureMethod by remember(existing?.id) { mutableStateOf(initialPayload.optString("measurementMethod", "腋温").ifBlank { "腋温" }) }
    val savedSupplementName = initialPayload.optString("supplementName", if (existing == null) RecordPreferences.get(context, RecordPreferences.SUPPLEMENT_NAME, "维生素AD") else "维生素AD")
    var supplementName by remember(existing?.id) { mutableStateOf(if (savedSupplementName in supplementPresets) savedSupplementName else "其他") }
    var customSupplementName by remember(existing?.id) { mutableStateOf(savedSupplementName.takeIf { it !in supplementPresets } ?: "") }
    var supplementAmount by remember(existing?.id) { mutableStateOf(initialPayload.optString("amount")) }
    var supplementUnit by remember(existing?.id) { mutableStateOf(initialPayload.optString("unit", if (existing == null) RecordPreferences.get(context, RecordPreferences.SUPPLEMENT_UNIT, "滴") else "滴")) }
    val savedFoodName = initialPayload.optString("foodName", if (existing == null) RecordPreferences.get(context, RecordPreferences.FOOD_NAME, "米粉") else "米粉")
    var foodName by remember(existing?.id) { mutableStateOf(if (savedFoodName in solidFoodPresets) savedFoodName else "其他") }
    var customFoodName by remember(existing?.id) { mutableStateOf(savedFoodName.takeIf { it !in solidFoodPresets } ?: "") }
    var foodAmount by remember(existing?.id) { mutableStateOf(initialPayload.optString("amount")) }
    var foodUnit by remember(existing?.id) { mutableStateOf(initialPayload.optString("unit", if (existing == null) RecordPreferences.get(context, RecordPreferences.FOOD_UNIT, "g") else "g")) }
    var foodStatus by remember(existing?.id) { mutableStateOf(initialPayload.optString("eatingStatus", if (existing == null) RecordPreferences.get(context, RecordPreferences.FOOD_STATUS, "正常") else "正常")) }
    var foodCategory by remember(existing?.id) { mutableStateOf(initialPayload.optString("foodCategory", if (existing == null) RecordPreferences.get(context, RecordPreferences.FOOD_CATEGORY, "未分类") else "未分类")) }
    var foodStage by remember(existing?.id) { mutableStateOf(initialPayload.optString("foodStage", if (existing == null) RecordPreferences.get(context, RecordPreferences.FOOD_STAGE, "首次尝试") else "未标记")) }
    var foodReaction by remember(existing?.id) { mutableStateOf(initialPayload.optString("foodReaction", if (existing == null) RecordPreferences.get(context, RecordPreferences.FOOD_REACTION, "待观察") else "未记录")) }
    var foodReactionNote by remember(existing?.id) { mutableStateOf(initialPayload.optString("foodReactionNote")) }
    var note by remember(existing?.id) { mutableStateOf(initialPayload.optString("note")) }
    var milestoneTitle by remember(existing?.id) { mutableStateOf(initialPayload.optString("milestoneTitle")) }
    val milestoneId = remember(existing?.id) { initialPayload.optString("milestoneId") }
    var error by remember { mutableStateOf<String?>(null) }
    var showEventTimePicker by remember { mutableStateOf(false) }
    val title = when (eventType) { "FEEDING" -> "喂养记录"; "SOLID_FOOD" -> "辅食记录"; "DIAPER" -> "日常护理"; "SLEEP" -> "睡眠记录"; "SUPPLEMENT" -> "补充记录"; "MILESTONE" -> "成长里程碑"; "TEMPERATURE" -> "体温记录"; else -> "成长记录" }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
            HorizontalDivider()
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("记录时间", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { showEventTimePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(date.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)) + " · %02d时%02d分".format(hour, minute))
                }
                when (eventType) {
                    "FEEDING" -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("喂养方式", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                SelectionRow(feedingOptions, feedingMethod) { feedingMethod = it }
                            }
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("奶量", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                val selectedMilkMl = amountText.toIntOrNull()?.coerceIn(5, 1000) ?: 120
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(if (feedingMethod == "母乳") "奶量（可选）" else "本次奶量", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                    Text("$selectedMilkMl ml", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = selectedMilkMl.toFloat(),
                                    onValueChange = { amountText = ((it / 5f).roundToInt() * 5).coerceIn(5, 1000).toString() },
                                    valueRange = 5f..1000f,
                                    steps = 0
                                )
                                SliderRuler(selectedMilkMl, 5, 1000, 5)
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("每格 5 ml", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { amountText = (selectedMilkMl - 1).coerceAtLeast(5).toString() }) { Text("−1") }
                                    TextButton(onClick = { amountText = (selectedMilkMl + 1).coerceAtMost(1000).toString() }) { Text("+1") }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("5 ml", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("1000 ml", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(if (feedingMethod == "母乳") "亲喂可不填写奶量；瓶喂母乳可滑动设置。" else "滑动按 5 ml 刻度吸附，也可用 ±1 ml 微调。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (feedingMethod != "配方奶") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
                            ) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("亲喂时长", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                    OutlinedTextField(
                                        value = breastMinutes,
                                        onValueChange = { raw -> breastMinutes = raw.filter(Char::isDigit).take(3) },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("时长（分钟，可选）") },
                                        supportingText = { Text("记录亲喂时间；也可以只填写奶量。") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true
                                    )
                                    Text("常用时长 · 分钟", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    SelectionRow(listOf("5", "10", "15", "20", "30"), breastMinutes) { breastMinutes = it }
                                }
                            }
                        }
                    }
                    "DIAPER" -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("护理情况", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (urine) FilledTonalButton(onClick = { urine = false }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("✓ 有小便") }
                                    else OutlinedButton(onClick = { urine = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("有小便") }
                                    if (stool) FilledTonalButton(onClick = { stool = false }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("✓ 有大便") }
                                    else OutlinedButton(onClick = { stool = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("有大便") }
                                }
                                Text("可同时选择小便和大便；至少选择一项。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (stool) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
                            ) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("大便情况", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                    Text("性状", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    SelectionRow(listOf("稀水样", "糊状", "成形", "干硬"), stoolShape) { stoolShape = it }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Text("颜色", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    SelectionRow(listOf("黄色", "绿色", "红色", "棕色/其他"), stoolColor) { stoolColor = if (it == "棕色/其他") "其他" else it }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Text("大便量", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    SelectionRow(listOf("少量", "中量", "大量"), stoolAmount) { stoolAmount = it }
                                }
                            }
                        }
                    }
                    "SLEEP" -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("睡眠时长", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                val selectedSleepMinutes = sleepMinutesText.toIntOrNull()?.coerceIn(10, 720) ?: 60
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text("本次睡眠时长", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                    Text(
                                        if (selectedSleepMinutes >= 60) "${selectedSleepMinutes / 60}小时${selectedSleepMinutes % 60}分" else "${selectedSleepMinutes}分钟",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Slider(
                                    value = selectedSleepMinutes.toFloat(),
                                    onValueChange = { sleepMinutesText = ((it / 5f).roundToInt() * 5).coerceIn(10, 720).toString() },
                                    valueRange = 10f..720f,
                                    steps = 0
                                )
                                SliderRuler(selectedSleepMinutes, 10, 720, 5)
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("每格 5 分钟", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { sleepMinutesText = (selectedSleepMinutes - 1).coerceAtLeast(10).toString() }) { Text("−1") }
                                    TextButton(onClick = { sleepMinutesText = (selectedSleepMinutes + 1).coerceAtMost(720).toString() }) { Text("+1") }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("10 分钟", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("12 小时", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("左右滑动调整，精确到 1 分钟。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    "SOLID_FOOD" -> {
                        SolidFoodEditorFields(
                            foodName = foodName,
                            onFoodNameChange = { foodName = it; if (it != "其他") customFoodName = "" },
                            customFoodName = customFoodName,
                            onCustomFoodNameChange = { customFoodName = it },
                            foodAmount = foodAmount,
                            onFoodAmountChange = { foodAmount = it },
                            foodUnit = foodUnit,
                            onFoodUnitChange = { foodUnit = it },
                            foodCategory = foodCategory,
                            onFoodCategoryChange = { foodCategory = it },
                            foodStage = foodStage,
                            onFoodStageChange = { foodStage = it },
                            foodStatus = foodStatus,
                            onFoodStatusChange = { foodStatus = it },
                            foodReaction = foodReaction,
                            onFoodReactionChange = {
                                foodReaction = it
                                if (it != "出现不适") foodReactionNote = ""
                            },
                            foodReactionNote = foodReactionNote,
                            onFoodReactionNoteChange = { foodReactionNote = it }
                        )
                    }
                    "SUPPLEMENT" -> {
                        Text("补充项目", fontWeight = FontWeight.SemiBold)
                        SelectionRow(supplementPresets, supplementName) {
                            supplementName = it
                            supplementUnit = supplementDefaultUnit(it)
                            if (it != "其他") customSupplementName = ""
                        }
                        if (supplementName == "其他") {
                            OutlinedTextField(
                                value = customSupplementName,
                                onValueChange = { customSupplementName = it.take(30) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("项目名称") },
                                singleLine = true
                            )
                        }
                        Text("补充数量（可选）", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = supplementAmount,
                            onValueChange = { raw -> supplementAmount = raw.filter { it.isDigit() || it == '.' }.take(8) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("数量") },
                            singleLine = true
                        )
                        Text("单位", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SelectionRow(supplementUnits, supplementUnit) { supplementUnit = it }
                        Text("这里只记录实际补充情况，不提供剂量建议；具体用量请遵照医生或产品说明。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    "MILESTONE" -> {
                        OutlinedTextField(
                            value = milestoneTitle,
                            onValueChange = { milestoneTitle = it.take(60) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("里程碑名称") },
                            singleLine = true
                        )
                        Text("记录宝宝第一次做到这件事的日期，也可以补充备注。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    "TEMPERATURE" -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("体温", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(
                                    value = temperatureC,
                                    onValueChange = { raw -> temperatureC = raw.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.') },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("体温（℃）") },
                                    supportingText = { Text("记录实际测量值，例如 36.8 ℃") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true
                                )
                                Text("测量方式", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("腋温", "耳温", "额温").forEach { method ->
                                        FilterChip(selected = temperatureMethod == method, onClick = { temperatureMethod = method }, label = { Text(method) })
                                    }
                                }
                            }
                        }
                        Text("请按实际使用的测量方式记录；不同测量方式的数值不宜直接横向比较。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    "GROWTH" -> {
                        Text(
                            "按本次实际测量填写即可，体重、身长和头围可以单独记录，不必每次都测齐。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("体重", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(
                                    value = weightKg,
                                    onValueChange = { raw -> weightKg = raw.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.') },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("体重（kg）") },
                                    supportingText = { Text("例如 5.8 kg") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true
                                )
                            }
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("身长", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(
                                    value = lengthCm,
                                    onValueChange = { raw -> lengthCm = raw.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.') },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("身长（cm）") },
                                    supportingText = { Text("例如 60.5 cm") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true
                                )
                            }
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("头围", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(
                                    value = headCm,
                                    onValueChange = { raw -> headCm = raw.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.') },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("头围（cm）") },
                                    supportingText = { Text("例如 39.0 cm") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(note, { note = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("备注（可选）") }, minLines = 1, maxLines = 2)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            fun submit(keepOpen: Boolean) {
                
                                val timestamp = date.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                var payload = JSONObject()
                                when (eventType) {
                                    "FEEDING" -> {
                                        payload = try {
                                            buildFeedingPayload(feedingMethod, amountText, breastMinutes).also {
                                                FeedingPreferences.saveLastSelection(context, feedingMethod, amountText, breastMinutes)
                                            }
                                        } catch (validation: IllegalArgumentException) {
                                            error = validation.message
                                            return
                                        }
                                    }
                                    "DIAPER" -> {
                                        if (!hasDiaperRecordSelection(urine, stool)) { error = "请至少选择一项护理情况"; return }
                                        payload.put("urine", urine).put("stool", stool)
                                        if (stool) payload.put("stoolShape", stoolShape).put("stoolColor", stoolColor).put("stoolAmount", stoolAmount)
                                    }
                                    "SLEEP" -> {
                                        val duration = try {
                                            parseSleepDuration(sleepMinutesText)
                                        } catch (validation: IllegalArgumentException) {
                                            error = validation.message
                                            return
                                        }
                                        payload.put("durationMinutes", duration)
                                    }
                                    "SOLID_FOOD" -> {
                                        val name = if (foodName == "其他") customFoodName.trim() else foodName
                                        if (name.isBlank()) { error = "请输入食物名称"; return }
                                        val amount = try {
                                            parseOptionalRecordAmount(foodAmount, "辅食数量", 10000.0)
                                        } catch (validation: IllegalArgumentException) {
                                            error = validation.message
                                            return
                                        }
                                        payload.put("foodName", name)
                                            .put("foodCategory", foodCategory)
                                            .put("foodStage", foodStage)
                                            .put("eatingStatus", foodStatus)
                                            .put("foodReaction", foodReaction)
                                        if (foodReaction == "出现不适" && foodReactionNote.isNotBlank()) {
                                            payload.put("foodReactionNote", foodReactionNote.trim())
                                        }
                                        if (amount != null) payload.put("amount", amount).put("unit", foodUnit)
                                    }
                                    "SUPPLEMENT" -> {
                                        val name = if (supplementName == "其他") customSupplementName.trim() else supplementName
                                        if (name.isBlank()) { error = "请输入补充项目名称"; return }
                                        val amount = try {
                                            parseOptionalRecordAmount(supplementAmount, "补充数量", 100000.0)
                                        } catch (validation: IllegalArgumentException) {
                                            error = validation.message
                                            return
                                        }
                                        payload.put("supplementName", name)
                                        if (amount != null) payload.put("amount", amount)
                                        if (supplementAmount.isNotBlank()) payload.put("unit", supplementUnit)
                                    }
                                    "MILESTONE" -> {
                                        val title = milestoneTitle.trim()
                                        if (title.isBlank()) { error = "请输入里程碑名称"; return }
                                        payload.put("milestoneTitle", title)
                                        if (milestoneId.isNotBlank()) payload.put("milestoneId", milestoneId)
                                    }
                                    "TEMPERATURE" -> {
                                        val value = try {
                                            parseTemperatureC(temperatureC)
                                        } catch (validation: IllegalArgumentException) {
                                            error = validation.message
                                            return
                                        }
                                        payload.put("temperatureC", value).put("measurementMethod", temperatureMethod)
                                    }
                                    "GROWTH" -> {
                                        val measurements = try {
                                            Triple(
                                                parseOptionalMeasurement(weightKg, "体重", 0.1, 80.0),
                                                parseOptionalMeasurement(lengthCm, "身长", 20.0, MAX_LENGTH_HEIGHT_CM),
                                                parseOptionalMeasurement(headCm, "头围", 20.0, 70.0)
                                            )
                                        } catch (validation: IllegalArgumentException) {
                                            error = validation.message
                                            return
                                        }
                                        val (weight, length, head) = measurements
                                        if (weight == null && length == null && head == null) {
                                            error = "请至少填写一项测量数据"
                                            return
                                        }
                                        weight?.let { payload.put("weightGrams", (it * 1000).toInt()) }
                                        length?.let { payload.put("lengthCm", it) }
                                        head?.let { payload.put("headCircumferenceCm", it) }
                                    }
                                }
                                when (eventType) {
                                    "DIAPER" -> RecordPreferences.save(context, mapOf(
                                        RecordPreferences.DIAPER_SHAPE to stoolShape,
                                        RecordPreferences.DIAPER_COLOR to stoolColor,
                                        RecordPreferences.DIAPER_AMOUNT to stoolAmount
                                    ), mapOf(
                                        RecordPreferences.DIAPER_URINE to urine,
                                        RecordPreferences.DIAPER_STOOL to stool
                                    ))
                                    "SLEEP" -> RecordPreferences.save(context, mapOf(RecordPreferences.SLEEP_DURATION to sleepMinutesText))
                                    "SOLID_FOOD" -> RecordPreferences.save(context, mapOf(
                                        RecordPreferences.FOOD_NAME to if (foodName == "其他") customFoodName.trim() else foodName,
                                        RecordPreferences.FOOD_UNIT to foodUnit,
                                        RecordPreferences.FOOD_CATEGORY to foodCategory,
                                        RecordPreferences.FOOD_STAGE to foodStage,
                                        RecordPreferences.FOOD_STATUS to foodStatus,
                                        RecordPreferences.FOOD_REACTION to foodReaction
                                    ))
                                    "SUPPLEMENT" -> RecordPreferences.save(context, mapOf(
                                        RecordPreferences.SUPPLEMENT_NAME to if (supplementName == "其他") customSupplementName.trim() else supplementName,
                                        RecordPreferences.SUPPLEMENT_UNIT to supplementUnit
                                    ))
                                }
                                if (note.isNotBlank()) payload.put("note", note.trim())
                                onSave(eventType, payload, timestamp, keepOpen)
                                if (keepOpen) {
                                    // Keep the user's preferred selections, but clear values that
                                    // belong only to the record just saved.
                                    val nextTime = LocalDateTime.now()
                                    // Keep the selected date for batch backfilling; only refresh the time.
                                    hour = nextTime.hour
                                    minute = nextTime.minute
                                    note = ""
                                    foodAmount = ""
                                    foodReactionNote = ""
                                    supplementAmount = ""
                                    weightKg = ""
                                    lengthCm = ""
                                    headCm = ""
                                    milestoneTitle = ""
                                    error = null
                                }
                            
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (existing == null) {
                            FilledTonalButton(
                                onClick = { submit(true) },
                                enabled = !saving,
                                modifier = Modifier.weight(1f).height(52.dp)
                            ) {
                                Text(if (saving) "保存中…" else "保存并继续")
                            }
                        }
                        Button(
                            onClick = { submit(false) },
                            enabled = !saving,
                            modifier = Modifier.weight(1f).height(52.dp)
                        ) {
                            Text(if (saving) "保存中…" else "保存记录")
                        }
                    }
                }
            }
        }
    }
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
private fun SliderRuler(value: Int, min: Int, max: Int, step: Int) {
    val tickColor = MaterialTheme.colorScheme.outlineVariant
    val activeColor = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Canvas(Modifier.fillMaxWidth().height(12.dp)) {
            val count = ((max - min) / step).coerceAtLeast(1)
            repeat(count + 1) { index ->
                val x = size.width * index / count.toFloat()
                val major = index % 5 == 0
                drawLine(
                    color = if (min + index * step == value) activeColor else tickColor,
                    start = Offset(x, 0f),
                    end = Offset(x, if (major) size.height else size.height * 0.48f),
                    strokeWidth = if (min + index * step == value) 2.dp.toPx() else 1.dp.toPx()
                )
            }
        }
        val labels = listOf(
            (value - step * 2).coerceAtLeast(min),
            (value - step).coerceAtLeast(min),
            value.coerceIn(min, max),
            (value + step).coerceAtMost(max),
            (value + step * 2).coerceAtMost(max)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEachIndexed { index, label ->
                Text(
                    label.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == 2) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (index == 2) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

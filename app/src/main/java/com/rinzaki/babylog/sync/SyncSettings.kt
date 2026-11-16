package com.rinzaki.babylog.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.rinzaki.babylog.AppUpdateSection
import com.rinzaki.babylog.FeedingPreferences
import com.rinzaki.babylog.data.BabyLogDao
import com.rinzaki.babylog.data.SyncOperationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SyncSettingsDialog(dao: BabyLogDao, onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf(SyncPreferences.server(context)) }
    var accountIdentifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("BabyLog 默认离线使用。登录同一个账号，即可在多台设备间自动同步数据。") }
    var busy by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf(SyncPreferences.token(context)) }
    var accountEmail by remember { mutableStateOf(SyncPreferences.accountEmail(context)) }
    var editingServer by remember { mutableStateOf(SyncPreferences.server(context).isBlank()) }
    var showAccountForm by remember { mutableStateOf(SyncPreferences.token(context).isBlank()) }
    var pendingCount by remember { mutableStateOf(0) }
    var conflictCount by remember { mutableStateOf(0) }
    var cloudOnlyPhotoCount by remember { mutableStateOf(0) }
    var conflicts by remember { mutableStateOf(emptyList<SyncOperationEntity>()) }
    var statusLoaded by remember { mutableStateOf(false) }
    var retryingConflictId by remember { mutableStateOf<String?>(null) }
    var showSecurity by remember { mutableStateOf(false) }
    var editedAccountName by remember { mutableStateOf(accountEmail) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var showBreastMilk by remember { mutableStateOf(FeedingPreferences.isEnabled(context, FeedingPreferences.BREAST_MILK)) }
    var showFormula by remember { mutableStateOf(FeedingPreferences.isEnabled(context, FeedingPreferences.FORMULA)) }
    var showMixed by remember { mutableStateOf(FeedingPreferences.isEnabled(context, FeedingPreferences.MIXED)) }
    var cloudPhotoStorage by remember { mutableStateOf(SyncPreferences.cloudPhotoStorageEnabled(context)) }
    var photoStorageExpanded by remember { mutableStateOf(false) }
    var photoTotalCount by remember { mutableStateOf(0) }
    var localOriginalPhotoCount by remember { mutableStateOf(0) }
    var pendingPhotoCount by remember { mutableStateOf(0) }
    var photoCacheBytes by remember { mutableStateOf(0L) }
    var photoCacheFileCount by remember { mutableStateOf(0) }
    var appSectionExpanded by remember { mutableStateOf(false) }
    var feedingSectionExpanded by remember { mutableStateOf(false) }
    var photoSectionExpanded by remember { mutableStateOf(false) }
    var syncSectionExpanded by remember { mutableStateOf(false) }
    var serverAccountExpanded by remember { mutableStateOf(SyncPreferences.token(context).isBlank()) }

    fun updateFeedingMethod(method: String, enabled: Boolean) {
        val enabledCount = listOf(showBreastMilk, showFormula, showMixed).count { it }
        if (!enabled && enabledCount <= 1) {
            message = "至少保留一种喂养方式，才能继续记录喂养。"
            return
        }
        when (method) {
            FeedingPreferences.BREAST_MILK -> showBreastMilk = enabled
            FeedingPreferences.FORMULA -> showFormula = enabled
            FeedingPreferences.MIXED -> showMixed = enabled
        }
        FeedingPreferences.setEnabled(context, method, enabled)
        message = if (enabled) "已显示${method}选项。" else "已隐藏${method}选项；历史记录不会受影响。"
    }

    suspend fun refreshSyncStatus() {
        val snapshot = withContext(Dispatchers.IO) {
            val pending = dao.pendingOperationCount()
            val conflictRows = dao.conflictOperations()
            val conflictTotal = dao.conflictOperationCount()
            val photos = dao.getAllPhotos().filter { it.deletedAt == null }
            val cloudOnlyPhotos = photos.count { !it.originalOnDevice && it.syncStatus == "SYNCED" }
            val localOriginals = photos.count { it.originalOnDevice }
            val pendingPhotos = photos.count { it.syncStatus != "SYNCED" }
            val cacheFiles = File(context.cacheDir, "babylog_photo_originals")
                .listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }.orEmpty()
            val cacheBytes = cacheFiles.sumOf { it.length() }
            Triple(Triple(pending, conflictRows, conflictTotal), Triple(photos.size, localOriginals, pendingPhotos), Triple(cloudOnlyPhotos, cacheBytes, cacheFiles.size))
        }
        pendingCount = snapshot.first.first
        conflicts = snapshot.first.second
        conflictCount = snapshot.first.third
        photoTotalCount = snapshot.second.first
        localOriginalPhotoCount = snapshot.second.second
        pendingPhotoCount = snapshot.second.third
        cloudOnlyPhotoCount = snapshot.third.first
        photoCacheBytes = snapshot.third.second
        photoCacheFileCount = snapshot.third.third
        statusLoaded = true
    }

    LaunchedEffect(Unit) { refreshSyncStatus() }

    fun runAction(action: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            message = try {
                withContext(Dispatchers.IO) { action() }
            } catch (error: Exception) {
                error.message ?: "操作失败，请检查服务器地址和网络"
            } finally {
                busy = false
            }
            token = SyncPreferences.token(context)
            accountEmail = SyncPreferences.accountEmail(context)
            server = SyncPreferences.server(context)
            if (token.isNotBlank()) {
                showAccountForm = false
                editingServer = false
                password = ""
            }
            refreshSyncStatus()
        }
    }

    fun authenticate(path: String, successMessage: String) {
        runAction {
            val base = server.trim().trimEnd('/')
            require(base.startsWith("https://") || base.startsWith("http://10.") ||
                base.startsWith("http://192.168.") || base.startsWith("http://127.0.0.1") ||
                base.startsWith("http://localhost")) { "请先填写有效的 NAS 同步地址" }
            val result = SyncHttp.request(
                base, path, "POST",
                body = JSONObject()
                    .put(if (path == "/api/auth/register" && accountIdentifier.contains("@")) "email" else "username", accountIdentifier.trim())
                    .put("password", password)
            )
            SyncPreferences.saveServer(context, base)
            SyncPreferences.saveAccountEmail(context, accountIdentifier)
            val accessToken = result.getString("access_token")
            val previousFamilyId = SyncPreferences.familyId(context)
            SyncPreferences.saveSession(context, accessToken)
            val profile = SyncHttp.request(base, "/api/me", token = accessToken)
            SyncPreferences.saveAccountEmail(context, profile.optString("username", accountIdentifier.trim()))
            val linkedFamilies = profile.optJSONArray("families")
            require(linkedFamilies != null && linkedFamilies.length() > 0) {
                "账号初始化失败，请检查 NAS 服务端版本后重试。"
            }
            // One account maps to one shared data space. Every device signing in
            // with this account receives the same family ID from /api/me.
            val familyId = linkedFamilies.getJSONObject(0).getString("id")
            SyncPreferences.saveFamily(context, familyId)
            // A profile created while the app was offline has no family owner yet.
            // Claim only these unassigned profiles on the first account link; never
            // reassign records that already belong to another family.
            val unassignedBabies = if (previousFamilyId.isBlank()) {
                dao.getUnassignedBabies(UNASSIGNED_FAMILY_ID)
            } else {
                emptyList()
            }
            val claimedLocalBabies = enqueueUnassignedFamilyData(dao, unassignedBabies, familyId)
            val restoredConflicts = dao.restoreFamilyOwnershipConflicts(
                familyId,
                FAMILY_OWNERSHIP_CONFLICT_REASON
            )
            SyncScheduler.schedule(context)
            val recoveryNotes = buildList {
                if (claimedLocalBabies > 0) add("已将 $claimedLocalBabies 份此前离线创建的档案关联到当前家庭")
                if (restoredConflicts > 0) add("已恢复 $restoredConflicts 条属于当前家庭的冲突记录")
            }
            if (recoveryNotes.isNotEmpty()) "$successMessage ${recoveryNotes.joinToString("；")}，正在重新同步。" else successMessage
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SettingsGroup(
                    title = "应用与版本",
                    subtitle = "版本更新与应用信息",
                    expanded = appSectionExpanded,
                    onToggle = { appSectionExpanded = !appSectionExpanded }
                ) {
                AppUpdateSection()

                }

                SettingsGroup(
                    title = "记录偏好",
                    subtitle = "设置喂养记录中显示的方式",
                    expanded = feedingSectionExpanded,
                    onToggle = { feedingSectionExpanded = !feedingSectionExpanded }
                ) {
                Text("喂养方式", fontWeight = FontWeight.SemiBold)
                Text("关闭不使用的方式后，新增喂养记录时会隐藏对应选项；已有历史记录不会被删除或修改。", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("母乳", modifier = Modifier.weight(1f))
                    Switch(checked = showBreastMilk, onCheckedChange = { updateFeedingMethod(FeedingPreferences.BREAST_MILK, it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("配方奶", modifier = Modifier.weight(1f))
                    Switch(checked = showFormula, onCheckedChange = { updateFeedingMethod(FeedingPreferences.FORMULA, it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("混合喂养", modifier = Modifier.weight(1f))
                    Switch(checked = showMixed, onCheckedChange = { updateFeedingMethod(FeedingPreferences.MIXED, it) })
                }

                }

                SettingsGroup(
                    title = "相册与存储",
                    subtitle = if (cloudPhotoStorage) "已启用 NAS 云端原图 · " + photoCacheFileCount + " 张临时缓存" else "管理照片原图保存位置",
                    expanded = photoSectionExpanded,
                    onToggle = { photoSectionExpanded = !photoSectionExpanded }
                ) {
                Text("相册原图存储", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("NAS 云端保存原图")
                        Text(
                            "开启后，本机同步成功的照片仅保留轻量预览；打开照片时再从 NAS 按需读取原图。仅影响这台手机，NAS 原图不会删除。",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = cloudPhotoStorage,
                        onCheckedChange = { enabled ->
                            if (enabled && (token.isBlank() || server.isBlank() || SyncPreferences.familyId(context).isBlank())) {
                                message = "请先连接 NAS 并登录账号，再开启云端原图存储。"
                            } else {
                                cloudPhotoStorage = enabled
                                SyncPreferences.setCloudPhotoStorageEnabled(context, enabled)
                                SyncScheduler.enqueue(context)
                                message = if (enabled) {
                                    "已开启。后续成功同步的照片会保留轻量预览；已在本机的原图会在确认上传成功后逐步释放。"
                                } else {
                                    "已关闭。之后同步的照片会优先保留原图；已释放的原图仍可在查看时从 NAS 下载。"
                                }
                            }
                        },
                        enabled = token.isNotBlank() && server.isNotBlank() && !busy
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("照片存储详情", fontWeight = FontWeight.SemiBold)
                                Text("查看本机与 NAS 原图状态、临时缓存占用", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { photoStorageExpanded = !photoStorageExpanded }) {
                                Text(if (photoStorageExpanded) "收起" else "展开")
                            }
                        }
                        if (photoStorageExpanded) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("照片总数", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                                    Text("$photoTotalCount 张", fontWeight = FontWeight.Bold)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("云端原图", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                                    Text("$cloudOnlyPhotoCount 张", fontWeight = FontWeight.Bold, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("本地原图", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                                    Text("$localOriginalPhotoCount 张", fontWeight = FontWeight.Bold)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("待同步照片", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                                    Text("$pendingPhotoCount 张", fontWeight = FontWeight.Bold)
                                }
                            }
                            androidx.compose.material3.HorizontalDivider()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("临时原图缓存", fontWeight = FontWeight.Medium)
                                    Text(formatPhotoCacheSize(photoCacheBytes) + " · $photoCacheFileCount 张", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                }
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            val removed = withContext(Dispatchers.IO) {
                                                val folder = File(context.cacheDir, "babylog_photo_originals")
                                                folder.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }
                                                    ?.count { it.delete() } ?: 0
                                            }
                                            refreshSyncStatus()
                                            message = "已释放 $removed 个临时原图缓存文件。NAS 原图、本地原图和照片预览均未删除。"
                                        }
                                    },
                                    enabled = photoCacheFileCount > 0 && !busy
                                ) { Text("释放缓存") }
                            }
                            Text("缓存上限 300 MB，空间不足时优先清理最久未查看的原图。释放缓存后，照片仍可浏览预览；再次查看原图时会从 NAS 重新下载。", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { scope.launch { refreshSyncStatus() } }, modifier = Modifier.align(Alignment.End)) { Text("刷新统计") }
                        }
                    }
                }

                }

                SettingsGroup(
                    title = "同步状态",
                    subtitle = if (pendingCount == 0 && conflictCount == 0) "同步队列正常" else "待同步 " + pendingCount + " 项 · 冲突 " + conflictCount + " 项",
                    expanded = syncSectionExpanded,
                    onToggle = { syncSectionExpanded = !syncSectionExpanded }
                ) {
                Text("同步状态", fontWeight = FontWeight.SemiBold)
                Text(
                    if (pendingCount == 0 && conflictCount == 0) "本机同步队列已清空，没有待处理项目。"
                    else "待同步 $pendingCount 项 · 冲突 $conflictCount 项",
                    color = if (conflictCount > 0) androidx.compose.material3.MaterialTheme.colorScheme.error
                    else androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "记录会先保存在本机；联网后自动同步到 NAS。普通待同步项目需先完成同步；若冲突明确属于其他家庭，队列会保留，登录原家庭账号后才会恢复。",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )

                if (conflicts.isNotEmpty()) {
                    Text("同步冲突详情", fontWeight = FontWeight.SemiBold)
                    Text(
                        "这些项目仍保留在本机，没有被删除。请根据原因选择重试，或切回原家庭账号恢复。",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                    conflicts.take(20).forEach { operation ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(syncConflictTitle(operation), fontWeight = FontWeight.SemiBold)
                                Text(syncConflictSummary(operation), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                Text(
                                    operation.error ?: "同步冲突，尚未上传。",
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "产生时间：${formatSyncOperationTime(operation.createdAt)}",
                                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isFamilyOwnershipConflict(operation.error)) {
                                    Text(
                                        "此项目属于其他家庭空间。可安全退出当前账号并登录原家庭账号；匹配后系统会自动恢复该项目，不会把它上传到当前家庭。",
                                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                                    )
                                } else {
                                    Text(
                                        "重试会保留本机内容；若只是服务器版本冲突，系统会重新核对版本后提交。",
                                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            if (retryingConflictId == null && canRetrySyncConflict(operation.error)) {
                                                retryingConflictId = operation.operationId
                                                scope.launch {
                                                    message = try {
                                                        withContext(Dispatchers.IO) {
                                                            dao.retryConflict(operation.operationId)
                                                        }
                                                        SyncScheduler.enqueue(context)
                                                        "已将这条本机记录重新加入同步队列，正在重试。"
                                                    } catch (error: Exception) {
                                                        error.message ?: "重试失败，请稍后再试。"
                                                    } finally {
                                                        retryingConflictId = null
                                                        refreshSyncStatus()
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !busy && retryingConflictId == null && canRetrySyncConflict(operation.error)
                                    ) {
                                        Text(if (retryingConflictId == operation.operationId) "正在重试…" else "保留本机版本并重试")
                                    }
                                }
                            }
                        }
                    }
                    if (conflicts.size > 20) {
                        Text(
                            "还有 ${conflicts.size - 20} 条冲突未展开，以上显示最近 20 条。",
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                        )
                    }
                }

                }

                SettingsGroup(
                    title = "服务器与账号",
                    subtitle = if (token.isNotBlank()) accountEmail.ifBlank { "已登录 NAS 家庭账号" } else "连接 NAS 并登录以启用家庭同步",
                    expanded = serverAccountExpanded,
                    onToggle = { serverAccountExpanded = !serverAccountExpanded }
                ) {
                Text("NAS 同步服务器", fontWeight = FontWeight.SemiBold)
                if (server.isNotBlank() && !editingServer) {
                    Text(server, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    OutlinedButton(
                        onClick = { editingServer = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy
                    ) { Text("更换服务器地址") }
                } else {
                    OutlinedTextField(
                        value = server,
                        onValueChange = { server = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("服务器地址") },
                        placeholder = { Text("https://sync.example.com") },
                        singleLine = true,
                        enabled = !busy
                    )
                    Button(
                        onClick = {
                            val normalized = server.trim().trimEnd('/')
                            if (!(normalized.startsWith("https://") || normalized.startsWith("http://10.") ||
                                normalized.startsWith("http://192.168.") || normalized.startsWith("http://127.0.0.1") ||
                                normalized.startsWith("http://localhost"))) {
                                message = "请填写 HTTPS 地址；局域网测试可使用内网 HTTP 地址。"
                            } else {
                                val serverChanged = SyncPreferences.server(context).trimEnd('/') != normalized
                                if (serverChanged && cloudOnlyPhotoCount > 0) {
                                    message = "本机还有 $cloudOnlyPhotoCount 张照片只保留预览。请先连接原 NAS、关闭云端原图存储并等待原图恢复，再更换服务器。"
                                } else if (serverChanged && !canSwitchAccountWithConflicts(pendingCount, conflicts.map { it.error })) {
                                    message = if (pendingCount > 0) {
                                        "本机还有待同步项目，请先联网完成同步，再更换服务器。"
                                    } else {
                                        "还有非家庭归属类冲突，请先查看详情并处理后再更换服务器。"
                                    }
                                } else {
                                    SyncPreferences.saveServer(context, normalized)
                                    server = normalized
                                    editingServer = false
                                    if (serverChanged && token.isNotBlank()) {
                                        SyncScheduler.cancel(context)
                                        SyncPreferences.clearSession(context)
                                        token = ""
                                        accountEmail = ""
                                        showAccountForm = true
                                        message = "服务器地址已更换，请重新登录。"
                                    } else {
                                        message = "服务器地址已保存。"
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && statusLoaded
                    ) { Text("保存服务器地址") }
                    if (SyncPreferences.server(context).isNotBlank()) {
                        TextButton(onClick = {
                            server = SyncPreferences.server(context)
                            editingServer = false
                        }) { Text("取消修改") }
                    }
                }

                if (token.isBlank() || showAccountForm) {
                    Text("账号登录", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = accountIdentifier,
                        onValueChange = { accountIdentifier = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("账号名或邮箱") },
                        supportingText = { Text("新账号可使用 3-32 位字母、数字、点、下划线或短横线") },
                        singleLine = true,
                        enabled = !busy
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("密码（至少 10 位）") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !busy
                    )
                    OutlinedButton(
                        onClick = { authenticate("/api/auth/login", "登录成功，已开启后台自动同步。") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && accountIdentifier.isNotBlank() && password.isNotBlank()
                    ) { Text(if (busy) "正在登录…" else "登录已有账号") }
                    TextButton(
                        onClick = { authenticate("/api/auth/register", "注册成功，已登录并开启后台自动同步。") },
                        enabled = !busy && accountIdentifier.isNotBlank() && password.length >= 10
                    ) { Text("注册新账号") }
                } else {
                    Text("当前账号", fontWeight = FontWeight.SemiBold)
                    Text(accountEmail.ifBlank { "已登录" })
                    Text(
                        "已开启自动同步。在其他设备登录同一个账号和密码，即可共享宝宝档案、记录和相册。",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(
                        onClick = { editedAccountName = accountEmail; showSecurity = !showSecurity },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy
                    ) { Text(if (showSecurity) "收起账号与安全" else "账号与安全") }
                    if (showSecurity) {
                        Text("修改账号名", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = editedAccountName,
                            onValueChange = { editedAccountName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("新账号名") },
                            singleLine = true,
                            enabled = !busy
                        )
                        OutlinedTextField(
                            value = currentPassword,
                            onValueChange = { currentPassword = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("当前密码") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            enabled = !busy
                        )
                        Button(
                            onClick = {
                                runAction {
                                    val result = SyncHttp.request(
                                        server.trim().trimEnd('/'), "/api/account/username", "POST", token,
                                        JSONObject().put("username", editedAccountName.trim()).put("current_password", currentPassword)
                                    )
                                    val saved = result.optString("username", editedAccountName.trim())
                                    SyncPreferences.saveAccountEmail(context, saved)
                                    currentPassword = ""
                                    "账号名已更新。其他设备下次登录请使用新账号名。"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy && editedAccountName.trim().length >= 3 && currentPassword.length >= 10
                        ) { Text("保存账号名") }
                        }

                Spacer(Modifier.height(4.dp))
                        Text("修改密码", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("新密码（至少 10 位）") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            enabled = !busy
                        )
                        Button(
                            onClick = {
                                runAction {
                                    SyncHttp.request(
                                        server.trim().trimEnd('/'), "/api/account/password", "POST", token,
                                        JSONObject().put("current_password", currentPassword).put("new_password", newPassword)
                                    )
                                    currentPassword = ""
                                    newPassword = ""
                                    "密码已更新。其他设备下次登录请使用新密码。"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy && currentPassword.length >= 10 && newPassword.length >= 10
                        ) { Text("保存新密码") }
                    }
                    OutlinedButton(
                        onClick = {
                            if (cloudOnlyPhotoCount > 0) {
                                message = "本机还有 $cloudOnlyPhotoCount 张照片只保留预览。请先连接原 NAS、关闭云端原图存储并等待原图恢复，再退出或切换账号，避免离线后无法读取原图。"
                            } else if (!canSwitchAccountWithConflicts(pendingCount, conflicts.map { it.error })) {
                                message = if (pendingCount > 0) {
                                    "本机还有待同步项目，请联网完成同步后再退出登录。"
                                } else {
                                    "还有非家庭归属类冲突，请先查看详情并处理后再切换账号。"
                                }
                            } else {
                                SyncScheduler.cancel(context)
                                SyncPreferences.clearSession(context)
                                token = ""
                                accountEmail = ""
                                showAccountForm = true
                                message = if (conflicts.isNotEmpty()) {
                                    "已退出账号。本机数据和冲突队列均已保留；登录原家庭账号后，匹配的项目会自动恢复。"
                                } else {
                                    "已退出账号。本机数据不会删除。"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && statusLoaded
                    ) { Text("退出登录 / 切换账号") }
                }

                Spacer(Modifier.height(4.dp))
                Text(message, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                if (busy) Text("正在处理…")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}


@Composable
private fun SettingsGroup(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
        )
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (expanded) "收起" else "展开",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
            }
            if (expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )
            }
        }
    }
}

private fun formatPhotoCacheSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format(Locale.CHINA, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> String.format(Locale.CHINA, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.CHINA, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

internal fun syncConflictTitle(operation: SyncOperationEntity): String {
    val entity = when (operation.entityType) {
        "baby" -> "宝宝档案"
        "event" -> "日常记录"
        "photo" -> "成长相册"
        else -> "同步项目"
    }
    val action = if (operation.operation == "delete") "删除" else "更新"
    return "${entity} · ${action}"
}

internal fun syncConflictSummary(operation: SyncOperationEntity): String {
    val payload = runCatching { JSONObject(operation.payloadJson) }.getOrElse { JSONObject() }
    return when (operation.entityType) {
        "baby" -> payload.optString("displayName").ifBlank { "宝宝档案 ${operation.entityId.take(8)}" }
        "event" -> {
            val eventType = payload.optString("eventType")
            val label = when (eventType) {
                "FEEDING" -> "喂养"
                "DIAPER" -> "大小便护理"
                "SLEEP" -> "睡眠"
                "GROWTH" -> "成长测量"
                "VACCINE" -> "疫苗接种"
                else -> "其他记录"
            }
            val timestamp = payload.optLong("occurredAt")
            val time = if (timestamp > 0L) formatSyncOperationTime(timestamp) else ""
            val eventPayload = runCatching {
                JSONObject(payload.optString("payloadJson", "{}"))
            }.getOrElse { JSONObject() }
            val detail = when (eventType) {
                "FEEDING" -> {
                    val method = eventPayload.optString("method", "配方奶").ifBlank { "配方奶" }
                    val amount = eventPayload.optInt("amountMl").takeIf { it > 0 }?.let { "${it} ml" }
                    val duration = eventPayload.optInt("breastMinutes").takeIf { it > 0 }?.let { "亲喂 ${it} 分钟" }
                    listOfNotNull(method, amount, duration).joinToString(" · ")
                }
                "SLEEP" -> eventPayload.optInt("durationMinutes").takeIf { it > 0 }?.let { "${it} 分钟" }.orEmpty()
                "DIAPER" -> buildList {
                    if (eventPayload.optBoolean("urine")) add("小便")
                    if (eventPayload.optBoolean("stool")) add("大便")
                }.joinToString("、")
                else -> ""
            }
            listOf(label, time, detail).filter { it.isNotBlank() }.joinToString(" · ")
        }
        "photo" -> payload.optString("caption").ifBlank { "照片 ${operation.entityId.take(8)}" }
        else -> "项目标识：${operation.entityId.take(12)}"
    }
}

private fun formatSyncOperationTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

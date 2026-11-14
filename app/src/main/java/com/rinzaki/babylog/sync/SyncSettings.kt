package com.rinzaki.babylog.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.rinzaki.babylog.AppUpdateSection
import com.rinzaki.babylog.data.BabyLogDao
import com.rinzaki.babylog.data.SyncOperationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import com.rinzaki.babylog.sync.photoSyncPayload
import com.rinzaki.babylog.sync.mimeTypeForPath

@Composable
fun SyncSettingsDialog(dao: BabyLogDao, onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf(SyncPreferences.server(context)) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var inviteResult by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("未配置服务器时，BabyLog 完全离线运行。") }
    var busy by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf(SyncPreferences.token(context)) }
    var accountEmail by remember { mutableStateOf(SyncPreferences.accountEmail(context)) }
    var editingServer by remember { mutableStateOf(SyncPreferences.server(context).isBlank()) }
    var showAccountForm by remember { mutableStateOf(SyncPreferences.token(context).isBlank()) }
    var familyId by remember { mutableStateOf(SyncPreferences.familyId(context)) }
    var pendingCount by remember { mutableStateOf(0) }
    var conflictCount by remember { mutableStateOf(0) }
    var conflicts by remember { mutableStateOf(emptyList<SyncOperationEntity>()) }
    var queueStateLoaded by remember { mutableStateOf(false) }
    var lastSyncAt by remember { mutableStateOf(SyncPreferences.lastSyncAt(context)) }
    var lastSyncError by remember { mutableStateOf(SyncPreferences.lastSyncError(context)) }
    var showSnapshotConfirm by remember { mutableStateOf(false) }
    var showDeviceResetConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        pendingCount = dao.pendingOperationCount()
        conflictCount = dao.conflictOperationCount()
        conflicts = dao.conflictOperations()
        queueStateLoaded = true
    }

    fun runAction(action: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            message = try { withContext(Dispatchers.IO) { action() } }
            catch (error: Exception) { error.message ?: "操作失败，请检查服务器地址和网络" }
            finally { busy = false }
            token = SyncPreferences.token(context)
            accountEmail = SyncPreferences.accountEmail(context)
            server = SyncPreferences.server(context)
            if (token.isNotBlank()) { showAccountForm = false; editingServer = false; password = "" }
            familyId = SyncPreferences.familyId(context)
            pendingCount = withContext(Dispatchers.IO) { dao.pendingOperationCount() }
            conflictCount = withContext(Dispatchers.IO) { dao.conflictOperationCount() }
            conflicts = withContext(Dispatchers.IO) { dao.conflictOperations() }
            queueStateLoaded = true
            lastSyncAt = SyncPreferences.lastSyncAt(context)
            lastSyncError = SyncPreferences.lastSyncError(context)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().height(520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("默认离线使用。只有填写自己的 NAS 地址并登录后，才会启用同步。")
                AppUpdateSection()
                Text("服务器", fontWeight = FontWeight.SemiBold)
                if (server.isNotBlank() && !editingServer) {
                    Text("已配置服务器", style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                    Text(server, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { editingServer = true }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                        Text("更换服务器地址")
                    }
                } else {
                    OutlinedTextField(
                        value = server, onValueChange = { server = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("NAS 同步地址") }, placeholder = { Text("https://sync.example.com") },
                        singleLine = true, enabled = !busy
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
                                if (serverChanged && pendingCount > 0) {
                                    message = "还有 $pendingCount 项待同步。请先完成同步后再更换服务器；已隔离的冲突数据会继续保留在本机。"
                                } else {
                                    SyncPreferences.saveServer(context, normalized)
                                    server = normalized
                                    editingServer = false
                                    if (serverChanged && token.isNotBlank()) {
                                        SyncPreferences.clearSession(context)
                                        token = ""
                                        accountEmail = ""
                                        familyId = ""
                                        showAccountForm = true
                                        message = "服务器地址已更换，请重新登录该服务器账号。"
                                    } else {
                                        message = "服务器地址已保存。"
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(), enabled = !busy && queueStateLoaded
                    ) { Text("保存服务器地址") }
                    if (SyncPreferences.server(context).isNotBlank()) {
                        TextButton(onClick = { server = SyncPreferences.server(context); editingServer = false }) {
                            Text("取消修改")
                        }
                    }
                }

                if (token.isBlank() || showAccountForm) {
                    Text("账号登录", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("邮箱") }, singleLine = true, enabled = !busy
                    )
                    OutlinedTextField(
                        value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("密码（至少 10 位）") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), enabled = !busy
                    )
                    OutlinedButton(
                        onClick = {
                            runAction {
                                val base = server.trim().trimEnd('/')
                                require(base.startsWith("https://") || base.startsWith("http://10.") ||
                                    base.startsWith("http://192.168.") || base.startsWith("http://127.0.0.1") ||
                                    base.startsWith("http://localhost")) { "请先填写有效的服务器地址" }
                                val result = SyncHttp.request(base, "/api/auth/register", "POST", body =
                                    JSONObject().put("email", email.trim()).put("password", password))
                                SyncPreferences.saveServer(context, base)
                                SyncPreferences.saveAccountEmail(context, email)
                                val accessToken = result.getString("access_token")
                                SyncPreferences.saveSession(context, accessToken)
                                val profile = SyncHttp.request(base, "/api/me", token = accessToken)
                                val linkedFamilies = profile.optJSONArray("families")
                                if (linkedFamilies != null && linkedFamilies.length() == 1) {
                                    SyncPreferences.saveFamily(context, linkedFamilies.getJSONObject(0).getString("id"))
                                    SyncScheduler.schedule(context)
                                }
                                "注册成功，账号已登录。"
                            }
                        }, modifier = Modifier.fillMaxWidth(), enabled = !busy && queueStateLoaded && email.isNotBlank() && password.length >= 10
                    ) { Text("注册新账号") }
                    OutlinedButton(
                        onClick = {
                            runAction {
                                val base = server.trim().trimEnd('/')
                                require(base.isNotBlank()) { "请先填写服务器地址" }
                                val result = SyncHttp.request(base, "/api/auth/login", "POST", body =
                                    JSONObject().put("email", email.trim()).put("password", password))
                                SyncPreferences.saveServer(context, base)
                                SyncPreferences.saveAccountEmail(context, email)
                                val accessToken = result.getString("access_token")
                                SyncPreferences.saveSession(context, accessToken)
                                val profile = SyncHttp.request(base, "/api/me", token = accessToken)
                                val linkedFamilies = profile.optJSONArray("families")
                                if (linkedFamilies != null && linkedFamilies.length() == 1) {
                                    SyncPreferences.saveFamily(context, linkedFamilies.getJSONObject(0).getString("id"))
                                    SyncScheduler.schedule(context)
                                }
                                "登录成功。"
                            }
                        }, modifier = Modifier.fillMaxWidth(), enabled = !busy && queueStateLoaded && email.isNotBlank() && password.isNotBlank()
                    ) { Text("登录已有账号") }
                } else {
                    Text("账号", fontWeight = FontWeight.SemiBold)
                    Text(accountEmail.ifBlank { "已登录账户（邮箱信息未保存）" },
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    Text("账号已登录", style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                    OutlinedButton(
                        onClick = {
                            SyncPreferences.clearSession(context)
                            token = ""
                            accountEmail = ""
                            familyId = ""
                            showAccountForm = true
                            message = "已退出同步账号。本机数据不会删除。"
                        }, modifier = Modifier.fillMaxWidth(), enabled = !busy && queueStateLoaded && pendingCount == 0
                    ) { Text("退出登录 / 切换账号") }
                    if (pendingCount > 0) {
                        Text("为保护本机数据，请先完成待同步项目后再退出或切换账号。冲突数据会保留在本机，可切回原家庭后重新尝试。", color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                }

                if (token.isNotBlank()) {
                    Text("家庭空间", fontWeight = FontWeight.SemiBold)
                    Text(if (familyId.isBlank()) "当前账号尚未加入家庭空间" else "已加入家庭空间")
                    OutlinedButton(
                        onClick = { showSnapshotConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && familyId.isNotBlank() && queueStateLoaded
                    ) { Text("以本机数据覆盖家庭云端") }
                    Text("仅家庭创建者使用。将以这台手机的宝宝档案、记录、照片和头像为准，覆盖云端当前业务数据。", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        onClick = { showDeviceResetConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && familyId.isNotBlank() && queueStateLoaded
                    ) { Text("清空本机数据并重新下载") }
                    Text("用于另一台手机：清除本机业务数据后，从家庭云端完整重新下载。不会删除账号或家庭。", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = {
                            runAction {
                                val base = SyncPreferences.server(context)
                                val access = SyncPreferences.token(context)
                                val result = SyncHttp.request(base, "/api/families", "POST", access, JSONObject().put("name", "我的家庭"))
                                val fid = result.getString("family_id")
                                SyncPreferences.saveFamily(context, fid)
                                val babies = dao.getAllBabies()
                                for (baby in babies) {
                                    val updated = baby.copy(familyId = fid, serverVersion = null)
                                    dao.upsertBaby(updated)
                                    dao.enqueueOperation(SyncOperationEntity(UUID.randomUUID().toString(), "baby", updated.id, "upsert", babySyncPayload(updated).toString(), null, System.currentTimeMillis()))
                                }
                                for (event in dao.getAllEvents().filter { it.deletedAt == null }) {
                                    dao.enqueueOperation(SyncOperationEntity(UUID.randomUUID().toString(), "event", event.id, "upsert", eventSyncPayload(event).toString(), null, System.currentTimeMillis()))
                                }
                                for (photo in dao.getAllPhotos().filter { it.deletedAt == null }) {
                                    dao.enqueueOperation(SyncOperationEntity(UUID.randomUUID().toString(), "photo", photo.id, "upsert", photoSyncPayload(photo, mimeTypeForPath(photo.localPath)).toString(), photo.serverVersion, System.currentTimeMillis()))
                                }
                                SyncScheduler.schedule(context)
                                "家庭创建成功，现有宝宝档案和记录已加入首次同步队列。"
                            }
                        }, modifier = Modifier.fillMaxWidth(), enabled = !busy && queueStateLoaded && familyId.isBlank() && pendingCount == 0 && conflictCount == 0
                    ) { Text("创建家庭并同步本机数据") }
                    OutlinedTextField(inviteCode, { inviteCode = it }, Modifier.fillMaxWidth(), label = { Text("家庭邀请码") }, singleLine = true)
                    OutlinedButton(
                        onClick = {
                            runAction {
                                val result = SyncHttp.request(
                                    SyncPreferences.server(context), "/api/families/join", "POST",
                                    SyncPreferences.token(context), JSONObject().put("code", inviteCode.trim())
                                )
                                SyncPreferences.saveFamily(context, result.getString("family_id"))
                                SyncScheduler.schedule(context)
                                "加入家庭成功，正在拉取家庭数据。"
                            }
                        }, modifier = Modifier.fillMaxWidth(), enabled = !busy && queueStateLoaded && familyId.isBlank() && inviteCode.isNotBlank() && pendingCount == 0 && conflictCount == 0
                    ) { Text("使用邀请码加入家庭") }
                    OutlinedButton(
                        onClick = {
                            runAction {
                                val fid = SyncPreferences.familyId(context)
                                val result = SyncHttp.request(
                                    SyncPreferences.server(context), "/api/families/$fid/invites",
                                    "POST", SyncPreferences.token(context)
                                )
                                inviteResult = result.getString("invite_code")
                                "邀请码已生成，有效期 72 小时，请发给另一位家长。"
                            }
                        }, modifier = Modifier.fillMaxWidth(), enabled = !busy && familyId.isNotBlank()
                    ) { Text("生成家庭邀请码") }
                    if (inviteResult.isNotBlank()) {
                        Text("邀请码：$inviteResult", fontWeight = FontWeight.Bold)
                    }
                    Text("同步状态", fontWeight = FontWeight.SemiBold)
                    Text("待同步 $pendingCount 项 · 冲突 $conflictCount 项")
                    if (conflicts.isNotEmpty()) {
                        Text("需要核对的项目", fontWeight = FontWeight.SemiBold)
                        conflicts.take(5).forEach { item ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("${item.entityType} · ${item.operation} · ${item.error ?: "同步冲突"}",
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            dao.retryConflict(item.operationId)
                                            pendingCount = withContext(Dispatchers.IO) { dao.pendingOperationCount() }
                                            conflictCount = withContext(Dispatchers.IO) { dao.conflictOperationCount() }
                                            conflicts = withContext(Dispatchers.IO) { dao.conflictOperations() }
                                            if (familyId.isNotBlank()) SyncScheduler.enqueue(context)
                                        }
                                    },
                                    enabled = !busy && queueStateLoaded
                                ) { Text("重新尝试") }
                            }
                        }
                        if (conflicts.size > 5) Text("另有 ${conflicts.size - 5} 项冲突，请稍后继续查看。", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = {
                            runAction {
                                require(SyncPreferences.server(context).isNotBlank()) { "请先配置 NAS 同步地址" }
                                require(SyncPreferences.familyId(context).isNotBlank()) { "请先创建家庭或加入已有家庭" }
                                SyncScheduler.enqueue(context)
                                "已发起手动同步。同步会在后台执行，可稍后刷新状态查看结果。"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && queueStateLoaded && familyId.isNotBlank()
                    ) { Text(if (busy) "正在处理…" else "立即同步") }
                    Text(if (lastSyncAt > 0L) "上次成功同步：" + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastSyncAt)) else "尚未成功同步")
                    if (!lastSyncError.isNullOrBlank()) Text("最近错误：$lastSyncError")
                    Text("记录变更和每次打开 App 时都会自动同步；离线时会保留在本机，联网后自动补传。", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        scope.launch {
                            pendingCount = withContext(Dispatchers.IO) { dao.pendingOperationCount() }
                            conflictCount = withContext(Dispatchers.IO) { dao.conflictOperationCount() }
                            conflicts = withContext(Dispatchers.IO) { dao.conflictOperations() }
                            lastSyncAt = SyncPreferences.lastSyncAt(context)
                            lastSyncError = SyncPreferences.lastSyncError(context)
                        }
                    }) { Text("刷新同步状态") }
                }
                Spacer(Modifier.height(4.dp))
                Text(message)
                if (busy) Text("正在处理…")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )

    if (showSnapshotConfirm) {
        AlertDialog(
            onDismissRequest = { if (!busy) showSnapshotConfirm = false },
            title = { Text("覆盖家庭云端数据？") },
            text = { Text("将以当前手机的全部宝宝档案、喂养护理睡眠成长记录、疫苗记录、相册和头像为准。云端及另一台手机的独有数据会被替换或删除。请确认当前手机是你要保留的第一台手机。") },
            confirmButton = {
                Button(onClick = {
                    showSnapshotConfirm = false
                    runAction {
                        SyncScheduler.cancel(context)
                        try {
                            submitAuthoritativeSnapshot(context, dao)
                        } finally {
                            SyncScheduler.schedule(context)
                        }
                    }
                }, enabled = !busy) { Text("确认覆盖") }
            },
            dismissButton = { TextButton(onClick = { showSnapshotConfirm = false }, enabled = !busy) { Text("取消") } }
        )
    }
    if (showDeviceResetConfirm) {
        AlertDialog(
            onDismissRequest = { if (!busy) showDeviceResetConfirm = false },
            title = { Text("清空本机并重新下载？") },
            text = { Text("这会删除当前手机上的宝宝档案、记录、照片缓存和未同步队列，然后从家庭云端重新下载。不会删除账号、家庭或云端数据。请只在第一台手机完成云端覆盖后，在第二台手机执行。") },
            confirmButton = {
                Button(onClick = {
                    showDeviceResetConfirm = false
                    runAction {
                        SyncScheduler.cancel(context)
                        try {
                            dao.clearAllSyncOperations()
                            dao.clearAllPhotos()
                            dao.clearAllEvents()
                            dao.clearAllBabies()
                            File(context.filesDir, "baby_album").deleteRecursively()
                            File(context.filesDir, "baby_avatars").deleteRecursively()
                            SyncPreferences.resetCursor(context)
                            "本机业务数据已清空，正在从家庭云端重新下载。请保持网络连接并稍后刷新同步状态。"
                        } finally {
                            SyncScheduler.schedule(context)
                        }
                    }
                }, enabled = !busy) { Text("确认清空并下载") }
            },
            dismissButton = { TextButton(onClick = { showDeviceResetConfirm = false }, enabled = !busy) { Text("取消") } }
        )
    }
}

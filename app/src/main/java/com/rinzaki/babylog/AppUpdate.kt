package com.rinzaki.babylog

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.rinzaki.babylog.sync.SyncPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val RELEASE_API = "https://api.github.com/repos/rinzaki007/BabyLog/releases/latest"
private const val UPDATE_PREFS = "babylog_update_preferences"
private const val CHECK_INTERVAL = 86_400_000L
private data class BabyRelease(
    val tag: String, val version: String, val notes: String, val url: String,
    val size: Long, val sha256: String?, val source: String
)

private object BabyUpdater {
    fun shouldCheck(context: Context) = System.currentTimeMillis() -
        context.getSharedPreferences(UPDATE_PREFS, 0).getLong("last_check", 0L) >= CHECK_INTERVAL

    fun nasStatus(context: Context): String {
        val base = SyncPreferences.server(context).trim().trimEnd('/')
        if (base.isBlank()) return "NAS 更新镜像：尚未配置 NAS 地址"
        val connection = URL("$base/api/app-update/status").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 5000
            connection.readTimeout = 7000
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val error = json.optString("last_error")
            when {
                error.isNotBlank() && error != "null" -> "NAS 更新镜像同步异常：$error"
                json.optBoolean("available") -> "NAS 已缓存 v${json.optString("version")}，手机可走内网高速下载"
                else -> "NAS 更新镜像尚未准备好安装包，后台会自动检查"
            }
        } catch (error: Exception) {
            "NAS 更新镜像暂不可用：${error.message ?: "连接失败"}"
        } finally { connection.disconnect() }
    }

    fun currentVersion(context: Context): String {
        @Suppress("DEPRECATION")
        return context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
    }

    fun newer(latest: String, current: String): Boolean {
        val a = latest.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    suspend fun check(context: Context): BabyRelease = withContext(Dispatchers.IO) {
        val current = currentVersion(context)
        val base = SyncPreferences.server(context).trim().trimEnd('/')
        var nasFailure: String? = null
        if (base.isNotBlank()) {
            try {
                val connection = URL("$base/api/app-update/latest").openConnection() as HttpURLConnection
                val nasRelease = try {
                    connection.connectTimeout = 7000
                    connection.readTimeout = 10000
                    connection.setRequestProperty("Accept", "application/json")
                    check(connection.responseCode == 200) { "NAS 更新接口 HTTP ${connection.responseCode}" }
                    val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    if (json.optBoolean("available")) {
                        val tag = json.optString("tag")
                        val version = json.optString("version", tag.removePrefix("v").removePrefix("V"))
                        val path = json.optString("download_path", "/api/app-update/download")
                        require(tag.isNotBlank() && path == "/api/app-update/download") { "NAS 更新信息无效" }
                        BabyRelease(
                            tag, version, json.optString("notes").ifBlank { "本次更新未提供说明。" },
                            base + path, json.optLong("size"),
                            json.optString("sha256").takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) },
                            "NAS 本地镜像"
                        )
                    } else null
                } finally { connection.disconnect() }
                if (nasRelease != null && newer(nasRelease.version, current)) {
                    context.getSharedPreferences(UPDATE_PREFS, 0).edit()
                        .putLong("last_check", System.currentTimeMillis()).apply()
                    return@withContext nasRelease
                }
            } catch (error: Exception) {
                nasFailure = error.message ?: "NAS 更新源暂时不可用"
            }
        }

        val release = try {
            fetchOfficialRelease()
        } catch (error: Exception) {
            val detail = error.message ?: "官方更新检查失败"
            throw IllegalStateException(
                if (nasFailure != null) "NAS 更新源不可用（$nasFailure）；官方更新源也暂时无法访问（$detail）"
                else detail
            )
        }
        context.getSharedPreferences(UPDATE_PREFS, 0).edit()
            .putLong("last_check", System.currentTimeMillis()).apply()
        release
    }

    private fun fetchOfficialRelease(): BabyRelease {
        val release = try {
            val connection = URL(RELEASE_API).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 12000; connection.readTimeout = 15000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "BabyLog-Android")
                check(connection.responseCode == 200) { "GitHub API HTTP ${connection.responseCode}" }
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val assets = json.optJSONArray("assets") ?: error("Release 没有附件")
                val asset = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk", true) } ?: error("最新 Release 尚未附带 APK")
                val tag = json.optString("tag_name")
                BabyRelease(
                    tag, tag.removePrefix("v").removePrefix("V"),
                    json.optString("body").ifBlank { "本次更新未提供说明。" },
                    asset.optString("browser_download_url"), asset.optLong("size"),
                    asset.optString("digest").removePrefix("sha256:").takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) },
                    "官方 GitHub"
                )
            } finally { connection.disconnect() }
        } catch (_: Exception) {
            fetchPublicReleasePage()
        }
        require(release.version.isNotBlank() && release.url.startsWith("https://")) { "Release 信息无效" }
        return release
    }

    private fun fetchPublicReleasePage(): BabyRelease {
        val connection = URL("https://github.com/rinzaki007/BabyLog/releases/latest").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 12000; connection.readTimeout = 20000
            connection.setRequestProperty("User-Agent", "BabyLog-Android")
            check(connection.responseCode == 200) { "更新检查失败（HTTP ${connection.responseCode}），请稍后重试" }
            val html = connection.inputStream.bufferedReader().use { it.readText() }
            val tag = Regex("/releases/tag/([^\\\"?#]+)").find(html)?.groupValues?.get(1).orEmpty()
            require(tag.isNotBlank()) { "暂时没有可用的正式 Release" }
            val pattern = Regex("href=\\\"([^\\\"]*/releases/download/" + Regex.escape(tag) + "/[^\\\"]+\\.apk)", RegexOption.IGNORE_CASE)
            val assetPath = pattern.find(html)?.groupValues?.get(1)?.replace("&amp;", "&") ?: error("最新 Release 尚未附带 APK")
            val url = if (assetPath.startsWith("http")) assetPath else "https://github.com" + assetPath
            BabyRelease(tag, tag.removePrefix("v").removePrefix("V"), "可从 GitHub Releases 查看本次更新说明。", url, 0L, null, "官方 GitHub")
        } finally { connection.disconnect() }
    }

    suspend fun download(context: Context, release: BabyRelease, progress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val file = File(dir, "BabyLog-${release.tag}.apk")
            val temp = File(dir, file.name + ".part")
            val c = URL(release.url).openConnection() as HttpURLConnection
            val digest = MessageDigest.getInstance("SHA-256")
            try {
                c.connectTimeout = 15000; c.readTimeout = 30000
                c.setRequestProperty("User-Agent", "BabyLog-Android")
                check(c.responseCode == 200) { "下载失败（HTTP ${c.responseCode}）" }
                val total = c.contentLengthLong.takeIf { it > 0 } ?: release.size
                var count = 0L
                c.inputStream.use { input ->
                    temp.outputStream().buffered().use { output ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            val n = input.read(buffer); if (n < 0) break
                            output.write(buffer, 0, n); digest.update(buffer, 0, n); count += n
                            if (total > 0) withContext(Dispatchers.Main) { progress((count.toFloat() / total).coerceIn(0f, 1f)) }
                        }
                    }
                }
                if (release.size > 0 && count != release.size) error("下载文件大小校验失败")
                release.sha256?.let { expected ->
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!actual.equals(expected, true)) error("SHA-256 完整性校验失败")
                }
                require(count > 0) { "下载文件为空" }
                if (!temp.renameTo(file)) error("无法保存更新安装包")
                withContext(Dispatchers.Main) { progress(1f) }
                file
            } catch (error: Exception) {
                temp.delete()
                file.delete()
                throw error
            } finally { c.disconnect() }
        }

    fun install(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}

@Composable
internal fun AppUpdateSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<BabyRelease?>(null) }
    var downloaded by remember { mutableStateOf<File?>(null) }
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var message by remember { mutableStateOf("优先从 NAS 本地获取更新；NAS 不可用时自动回退官方渠道。") }
    var nasStatus by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val current = remember { BabyUpdater.currentVersion(context) }
    val hasUpdate = release?.let { BabyUpdater.newer(it.version, current) } == true

    fun checkUpdate() {
        if (checking || downloading) return
        scope.launch {
            checking = true; error = null
            message = if (SyncPreferences.server(context).isBlank()) "正在检查官方更新源…" else "正在优先检查 NAS 更新源…"
            try {
                nasStatus = withContext(Dispatchers.IO) { BabyUpdater.nasStatus(context) }
                release = BabyUpdater.check(context); downloaded = null
                message = if (release?.let { BabyUpdater.newer(it.version, current) } == true)
                    "发现新版本 v${release?.version}" else "当前已是最新版本 v$current"
            } catch (e: Exception) { error = e.message ?: "检查失败，请稍后重试"; message = "暂时无法获取更新信息" }
            finally { checking = false }
        }
    }

    LaunchedEffect(Unit) { if (BabyUpdater.shouldCheck(context)) checkUpdate() }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("应用更新", style = MaterialTheme.typography.titleMedium)
                Text("v$current", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            nasStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (hasUpdate) {
                Text("最新版本：v${release!!.version}", color = MaterialTheme.colorScheme.primary)
                Text("更新来源：${release!!.source}", style = MaterialTheme.typography.labelMedium)
                Text(release!!.notes.take(500), style = MaterialTheme.typography.bodySmall)
            }
            if (downloading) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text("正在下载 ${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { checkUpdate() }, enabled = !checking && !downloading) {
                    Text(if (checking) "检查中…" else "检查更新")
                }
                if (hasUpdate && downloaded == null) {
                    Button(enabled = !checking && !downloading, onClick = {
                        val target = release ?: return@Button
                        scope.launch {
                            downloading = true; progress = 0f; error = null
                            try {
                                downloaded = BabyUpdater.download(context, target) { progress = it }
                                message = "安装包已下载并完成完整性校验。"
                            } catch (e: Exception) { error = e.message ?: "下载失败，请重试" }
                            finally { downloading = false }
                        }
                    }) { Text("下载更新") }
                }
                downloaded?.let { file ->
                    Button(onClick = {
                        runCatching { BabyUpdater.install(context, file) }
                            .onFailure { error = it.message ?: "无法启动系统安装器" }
                    }) { Text("安装更新") }
                }
            }
            Text("更新包优先来自已配置的 NAS 本地镜像，无法访问时自动使用官方 GitHub Releases。安装需经 Android 系统确认，不会静默安装，也不会主动删除本地记录。",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

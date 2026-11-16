package com.rinzaki.babylog.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rinzaki.babylog.data.BabyEntity
import com.rinzaki.babylog.data.BabyEventEntity
import com.rinzaki.babylog.data.BabyLogDatabase
import com.rinzaki.babylog.data.BabyLogDao
import com.rinzaki.babylog.data.SyncOperationEntity
import com.rinzaki.babylog.data.BabyPhotoEntity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import java.io.DataOutputStream
import android.graphics.BitmapFactory
import java.util.concurrent.TimeUnit
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object SyncPreferences {
    private const val FILE = "babylog_sync"
    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    fun server(context: Context) = prefs(context).getString("server", "") ?: ""
    fun cloudPhotoStorageEnabled(context: Context) = prefs(context).getBoolean("cloud_photo_storage", false)
    fun setCloudPhotoStorageEnabled(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean("cloud_photo_storage", enabled).apply() }
    fun token(context: Context, encryptLegacy: (String) -> String = SecureTokenStore::encrypt): String {
        val preferences = prefs(context)
        val encrypted = preferences.getString("token_encrypted", null)
        if (!encrypted.isNullOrBlank()) {
            return runCatching { SecureTokenStore.decrypt(encrypted) }.getOrElse {
                // A Keystore key can become unavailable after device security changes.
                // Fail closed and require a fresh login instead of using an unreadable token.
                // Keep the encrypted value for diagnostics/retry; a fresh login can
                // safely overwrite it. Never erase credential material on read failure.
                ""
            }
        }
        val legacy = preferences.getString("token", "").orEmpty()
        if (legacy.isBlank()) return ""
        // One-time, lossless migration from the previous plaintext preference.
        runCatching {
            val migrated = encryptLegacy(legacy)
            check(preferences.edit().putString("token_encrypted", migrated).remove("token").commit())
        }
        return legacy
    }
    fun accountEmail(context: Context) = prefs(context).getString("account_email", "") ?: ""
    fun saveAccountEmail(context: Context, value: String) { prefs(context).edit().putString("account_email", value.trim().lowercase()).apply() }
    fun familyId(context: Context) = prefs(context).getString("family_id", "") ?: ""
    fun cursor(context: Context) = prefs(context).getLong("cursor", 0L)
    fun saveServer(context: Context, value: String) { prefs(context).edit().putString("server", value.trim().trimEnd('/')).apply() }
    fun saveSession(context: Context, token: String) {
        val encrypted = SecureTokenStore.encrypt(token)
        check(prefs(context).edit().putString("token_encrypted", encrypted).remove("token")
            .remove("family_id").putLong("cursor", 0L).commit()) { "无法安全保存登录凭据，请重试" }
    }
    fun saveFamily(context: Context, id: String) { prefs(context).edit().putString("family_id", id).putLong("cursor", 0L).apply() }
    fun saveCursor(context: Context, value: Long) { prefs(context).edit().putLong("cursor", value).apply() }
    fun resetCursor(context: Context) { prefs(context).edit().putLong("cursor", 0L).apply() }
    fun recoveryInProgress(context: Context) = prefs(context).getBoolean("recovery_in_progress", false)
    fun setRecoveryInProgress(context: Context, value: Boolean) { prefs(context).edit().putBoolean("recovery_in_progress", value).apply() }
    fun clearSession(context: Context) { prefs(context).edit().remove("token").remove("token_encrypted").remove("family_id").remove("account_email").putLong("cursor", 0L).apply() }
    fun lastSyncAt(context: Context) = prefs(context).getLong("last_sync_at", 0L)
    fun lastSyncError(context: Context) = prefs(context).getString("last_sync_error", null)
    fun lastConsistency(context: Context) = prefs(context).getString("last_consistency", null)
    fun saveConsistency(context: Context, value: String) { prefs(context).edit().putString("last_consistency", value).apply() }
    fun isSyncing(context: Context): Boolean {
        val preferences = prefs(context)
        val startedAt = preferences.getLong("sync_started_at", 0L)
        val active = preferences.getBoolean("sync_in_progress", false) &&
            startedAt > 0L && System.currentTimeMillis() - startedAt < TimeUnit.MINUTES.toMillis(10)
        if (!active) preferences.edit().putBoolean("sync_in_progress", false).apply()
        return active
    }
    fun markSyncStarted(context: Context) {
        prefs(context).edit().putBoolean("sync_in_progress", true)
            .putLong("sync_started_at", System.currentTimeMillis()).apply()
    }
    fun markSyncSuccess(context: Context) {
        prefs(context).edit().putLong("last_sync_at", System.currentTimeMillis())
            .putBoolean("sync_in_progress", false).remove("sync_started_at").remove("last_sync_error").apply()
    }
    fun markSyncFailure(context: Context, reason: String) {
        prefs(context).edit().putBoolean("sync_in_progress", false).remove("sync_started_at")
            .putString("last_sync_error", reason.take(240)).apply()
    }
}

class SyncHttpException(val statusCode: Int, message: String) : IllegalStateException(message)

internal const val FAMILY_OWNERSHIP_CONFLICT_REASON =
    "这条数据属于其他家庭空间，已暂停上传。请切回原家庭完成同步，或先核对本机数据归属。"

internal const val UNASSIGNED_FAMILY_ID = "00000000-0000-0000-0000-000000000000"

internal suspend fun enqueueUnassignedFamilyData(
    dao: BabyLogDao,
    unassignedBabies: List<BabyEntity>,
    familyId: String
): Int {
    require(familyId.isNotBlank()) { "当前家庭空间标识无效" }
    if (unassignedBabies.isEmpty()) return 0

    val claimedCount = dao.assignUnassignedBabiesToFamily(familyId, UNASSIGNED_FAMILY_ID)
    if (claimedCount == 0) return 0

    val claimedBabyIds = unassignedBabies.map { it.id }.toSet()
    val now = System.currentTimeMillis()
    unassignedBabies.forEach { original ->
        val baby = dao.getBaby(original.id) ?: return@forEach
        val babyPayload = babySyncPayload(baby).toString()
        if (dao.hasLocalOperation("baby", baby.id)) {
            // A pre-login queued payload may still carry the unassigned placeholder ID.
            // Refresh it before the worker uploads this profile to the newly linked family.
            dao.updateBabyOperationPayload(baby.id, babyPayload)
        } else {
            dao.enqueueOperation(SyncOperationEntity(
                java.util.UUID.randomUUID().toString(), "baby", baby.id, "upsert",
                babyPayload, baby.serverVersion, now
            ))
        }
    }
    dao.getAllEvents().filter { it.babyId in claimedBabyIds }.forEach { event ->
        if (!dao.hasLocalOperation("event", event.id)) {
            dao.enqueueOperation(SyncOperationEntity(
                java.util.UUID.randomUUID().toString(), "event", event.id,
                if (event.deletedAt == null) "upsert" else "delete",
                eventSyncPayload(event).toString(), event.serverVersion, now
            ))
        }
    }
    dao.getAllPhotos().filter { it.babyId in claimedBabyIds }.forEach { photo ->
        if (!dao.hasLocalOperation("photo", photo.id)) {
            dao.enqueueOperation(SyncOperationEntity(
                java.util.UUID.randomUUID().toString(), "photo", photo.id,
                if (photo.deletedAt == null) "upsert" else "delete",
                photoSyncPayload(photo, mimeTypeForPath(photo.localPath)).toString(),
                photo.serverVersion, now
            ))
        }
    }
    return claimedCount
}

internal fun isFamilyOwnershipConflict(reason: String?): Boolean =
    reason == FAMILY_OWNERSHIP_CONFLICT_REASON

internal fun canRetrySyncConflict(reason: String?): Boolean =
    !isFamilyOwnershipConflict(reason)

internal fun canSwitchAccountWithConflicts(
    pendingCount: Int,
    conflictReasons: List<String?>
): Boolean = pendingCount == 0 && conflictReasons.all(::isFamilyOwnershipConflict)

internal fun canSubmitAuthoritativeSnapshot(
    babyFamilyIds: List<String>,
    activeFamilyId: String
): Boolean = activeFamilyId.isNotBlank() && babyFamilyIds.isNotEmpty() &&
    babyFamilyIds.all { it == activeFamilyId }

internal fun shouldRetrySyncStatus(statusCode: Int): Boolean =
    statusCode !in 400..499 || statusCode in setOf(408, 425, 429)

private object SyncExecutionMutex {
    val mutex = Mutex()
}

private fun replaceDownloadedFile(temp: File, destination: File) {
    try {
        Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

object SyncHttp {
    fun request(base: String, path: String, method: String = "GET", token: String? = null, body: JSONObject? = null): JSONObject {
        val connection = (URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("HTTP $code: " + runCatching { JSONObject(response).optString("detail") }.getOrDefault(response.take(180)))
            return if (response.isBlank()) JSONObject() else JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    fun uploadAvatar(base: String, familyId: String, babyId: String, token: String, file: File) {
        val boundary = "BabyLogAvatar-${java.util.UUID.randomUUID()}"
        val connection = (URL(base.trimEnd('/') + "/api/babies/$babyId/avatar?family_id=$familyId").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setChunkedStreamingMode(64 * 1024)
        }
        try {
            DataOutputStream(connection.outputStream).use { output ->
                output.writeBytes("--$boundary\r\n")
                output.writeBytes("Content-Disposition: form-data; name=" + 34.toChar() + "file" + 34.toChar() + "; filename=" + 34.toChar() + "avatar.jpg" + 34.toChar() + "\r\n")
                output.writeBytes("Content-Type: image/jpeg\r\n\r\n")
                file.inputStream().use { it.copyTo(output) }
                output.writeBytes("\r\n--$boundary--\r\n")
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                val response = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw SyncHttpException(code, "头像上传失败 HTTP $code: $response")
            }
        } finally { connection.disconnect() }
    }

    fun downloadAvatar(base: String, familyId: String, babyId: String, token: String, destination: File) {
        val connection = (URL(base.trimEnd('/') + "/api/babies/$babyId/avatar?family_id=$familyId").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 60000
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val response = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw SyncHttpException(code, "头像下载失败 HTTP $code: $response")
            }
            destination.parentFile?.mkdirs()
            val temp = File(destination.parentFile, destination.name + ".part")
            try {
                connection.inputStream.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                if (temp.length() <= 0L) throw java.io.IOException("头像下载内容为空")
                replaceDownloadedFile(temp, destination)
            } catch (error: Exception) {
                temp.delete()
                throw error
            }
        } finally { connection.disconnect() }
    }

    fun deleteAvatar(base: String, familyId: String, babyId: String, token: String) {
        val connection = (URL(base.trimEnd('/') + "/api/babies/$babyId/avatar?family_id=$familyId").openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            connectTimeout = 12000
            readTimeout = 20000
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw SyncHttpException(code, "头像删除失败 HTTP $code")
        } finally { connection.disconnect() }
    }

    fun uploadPhoto(base: String, familyId: String, photoId: String, token: String, file: File, mimeType: String) {
        val boundary = "BabyLog-${java.util.UUID.randomUUID()}"
        val connection = (URL(base.trimEnd('/') + "/api/photos/upload?family_id=$familyId&photo_id=$photoId").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setChunkedStreamingMode(64 * 1024)
        }
        try {
            DataOutputStream(connection.outputStream).use { output ->
                output.writeBytes("--$boundary\r\n")
                output.writeBytes("Content-Disposition: form-data; name=" + 34.toChar() + "file" + 34.toChar() + "; filename=" + 34.toChar() + file.name + 34.toChar() + "\r\n")
                output.writeBytes("Content-Type: $mimeType\r\n\r\n")
                file.inputStream().use { it.copyTo(output) }
                output.writeBytes("\r\n--$boundary--\r\n")
                output.flush()
            }
            val code = connection.responseCode
            val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw SyncHttpException(code, "照片上传失败 HTTP $code: " + runCatching { JSONObject(response).optString("detail") }.getOrDefault(response.take(160)))
        } finally { connection.disconnect() }
    }

    fun downloadPhoto(base: String, familyId: String, photoId: String, token: String, destination: File) {
        val connection = (URL(base.trimEnd('/') + "/api/photos/$photoId?family_id=$familyId").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 60000
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val message = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw SyncHttpException(code, "照片下载失败 HTTP $code: $message")
            }
            destination.parentFile?.mkdirs()
            val temp = File(destination.parentFile, destination.name + ".part")
            try {
                connection.inputStream.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                val expectedLength = connection.contentLengthLong
                if (expectedLength >= 0L && temp.length() != expectedLength) {
                    throw java.io.IOException("照片下载不完整：期望 ${expectedLength} 字节，实际 ${temp.length()} 字节")
                }
                if (temp.length() <= 0L) throw java.io.IOException("照片下载内容为空")
                replaceDownloadedFile(temp, destination)
            } catch (error: Exception) {
                temp.delete()
                throw error
            }
        } finally { connection.disconnect() }
    }
}

/**
 * Builds a lightweight JPEG preview without touching the source. The caller must
 * persist the new path in Room before deleting the original file.
 */
internal fun createPhotoPreview(source: File, destination: File): Boolean {
    if (!source.isFile || source.length() <= 0L) return false
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(source.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
    var sample = 1
    while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 2000) sample *= 2
    val bitmap = BitmapFactory.decodeFile(source.absolutePath, BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
    }) ?: return false
    return try {
        destination.parentFile?.mkdirs()
        val temp = File(destination.parentFile, destination.name + ".part")
        val saved = temp.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 78, it) }
        if (!saved || !temp.isFile || temp.length() <= 0L) {
            temp.delete()
            false
        } else {
            replaceDownloadedFile(temp, destination)
            true
        }
    } catch (_: Exception) {
        false
    } finally {
        bitmap.recycle()
    }
}

private suspend fun releasePhotoOriginalIfEnabled(
    context: Context,
    dao: BabyLogDao,
    photoId: String
) {
    if (!SyncPreferences.cloudPhotoStorageEnabled(context)) return
    val photo = dao.getPhoto(photoId) ?: return
    if (!photo.originalOnDevice || photo.deletedAt != null) return
    val original = File(photo.localPath)
    if (!original.isFile) return
    val mime = photo.photoMimeType ?: mimeTypeForPath(photo.localPath)
    val preview = File(context.filesDir, "baby_album_previews/$photoId.jpg")
    if (!createPhotoPreview(original, preview)) return
    // Commit the preview path first. A crash before this write leaves the original intact.
    dao.upsertPhoto(photo.copy(localPath = preview.absolutePath, originalOnDevice = false, photoMimeType = mime))
    if (original.absolutePath != preview.absolutePath) original.delete()
}

object SyncScheduler {
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    fun enqueue(context: Context) {
        if (SyncPreferences.recoveryInProgress(context)) return
        val request = OneTimeWorkRequestBuilder<BabyLogSyncWorker>().setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniqueWork("babylog-sync-now", ExistingWorkPolicy.KEEP, request)
    }
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<BabyLogSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("babylog-sync-periodic", ExistingPeriodicWorkPolicy.UPDATE, request)
        enqueue(context)
    }
    fun cancel(context: Context) {
        val manager = WorkManager.getInstance(context)
        runCatching { manager.cancelUniqueWork("babylog-sync-now").result.get() }
        runCatching { manager.cancelUniqueWork("babylog-sync-periodic").result.get() }
    }
}


suspend fun submitAuthoritativeSnapshot(context: Context, dao: com.rinzaki.babylog.data.BabyLogDao): String {
    val base = SyncPreferences.server(context)
    val token = SyncPreferences.token(context)
    val familyId = SyncPreferences.familyId(context)
    require(base.isNotBlank() && token.isNotBlank() && familyId.isNotBlank()) { "请先登录并加入家庭空间" }
    // Preserve operations created while the snapshot is being uploaded; only the
    // queue that existed before this snapshot may be cleared after it succeeds.
    val snapshotStartedAt = System.currentTimeMillis()
    val babies = dao.getAllBabies()
    require(canSubmitAuthoritativeSnapshot(babies.map { it.familyId }, familyId)) {
        "本机宝宝档案包含其他家庭或尚未归属的本地数据，已停止全量覆盖；请切回对应家庭账号并核对数据归属。"
    }
    val babyIds = babies.map { it.id }.toSet()
    val events = dao.getAllEvents().filter { it.babyId in babyIds }
    val photos = dao.getAllPhotos().filter { it.babyId in babyIds }
    val items = JSONArray()

    for (baby in babies) {
        val avatar = baby.avatarUri?.takeIf { it.isNotBlank() }?.let(::File)
        if (avatar != null) {
            require(avatar.isFile) { "宝宝头像文件不存在，已停止覆盖：" + baby.displayName }
            SyncHttp.uploadAvatar(base, familyId, baby.id, token, avatar)
        }
        items.put(JSONObject().put("operation_id", java.util.UUID.randomUUID().toString())
            .put("entity_type", "baby").put("entity_id", baby.id)
            .put("operation", "upsert").put("payload", babySyncPayload(baby)))
    }
    for (event in events) {
        items.put(JSONObject().put("operation_id", java.util.UUID.randomUUID().toString())
            .put("entity_type", "event").put("entity_id", event.id)
            .put("operation", if (event.deletedAt == null) "upsert" else "delete")
            .put("payload", eventSyncPayload(event)))
    }
    for (photo in photos) {
        val operation = if (photo.deletedAt == null) "upsert" else "delete"
        if (operation == "upsert") {
            val file = File(photo.localPath)
            if (!file.isFile) SyncHttp.downloadPhoto(base, familyId, photo.id, token, file)
            require(file.isFile && file.length() > 0L) { "照片文件不可用，已停止覆盖：" + photo.id }
            if (photo.originalOnDevice) SyncHttp.uploadPhoto(base, familyId, photo.id, token, file, photo.photoMimeType ?: mimeTypeForPath(photo.localPath))
        }
        items.put(JSONObject().put("operation_id", java.util.UUID.randomUUID().toString())
            .put("entity_type", "photo").put("entity_id", photo.id)
            .put("operation", operation)
            .put("payload", photoSyncPayload(photo, mimeTypeForPath(photo.localPath))))
    }
    val result = SyncHttp.request(base, "/api/sync/authoritative-snapshot", "POST", token,
        JSONObject().put("family_id", familyId).put("items", items))
    val versions = result.optJSONArray("versions") ?: JSONArray()
    for (i in 0 until versions.length()) {
        val item = versions.getJSONObject(i)
        val id = item.optString("entity_id")
        val version = item.optLong("version", -1L)
        if (version < 0L) continue
        when (item.optString("entity_type")) {
            "baby" -> dao.setBabyServerVersion(id, version)
            "event" -> dao.setEventServerVersion(id, version)
            "photo" -> {
                dao.setPhotoServerVersion(id, version, "SYNCED")
                releasePhotoOriginalIfEnabled(context, dao, id)
            }
        }
    }
    dao.clearSyncOperationsForFamily(familyId, snapshotStartedAt)
    SyncPreferences.saveCursor(context, result.optLong("cursor", 0L))
    SyncPreferences.markSyncSuccess(context)
    return "覆盖完成：已将本机 " + babies.size + " 份档案、" + events.size + " 条记录、" +
        photos.count { it.deletedAt == null } + " 张照片设为家庭云端权威数据。"
}

internal fun syncOperationBelongsToFamily(ownerFamilyId: String?, activeFamilyId: String): Boolean =
    activeFamilyId.isNotBlank() && ownerFamilyId != null && ownerFamilyId == activeFamilyId

class BabyLogSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = SyncExecutionMutex.mutex.withLock { runSync() }

    private suspend fun runSync(): Result {
        val base = SyncPreferences.server(applicationContext)
        val token = SyncPreferences.token(applicationContext)
        val familyId = SyncPreferences.familyId(applicationContext)
        if (base.isBlank() || token.isBlank() || familyId.isBlank()) return Result.success()
        SyncPreferences.markSyncStarted(applicationContext)
        return try {
            val dao = RoomAccess.database(applicationContext).babyLogDao()
            var batch = 0
            while (batch < 10) {
                val pending = dao.pendingOperations()
                if (pending.isEmpty()) break
                val changes = JSONArray()
                pending.forEach { operation ->
                    val ownerFamilyId = when (operation.entityType) {
                        "baby" -> dao.getBaby(operation.entityId)?.familyId
                        "event" -> dao.getEvent(operation.entityId)?.let { dao.getBaby(it.babyId)?.familyId }
                        "photo" -> dao.getPhoto(operation.entityId)?.let { dao.getBaby(it.babyId)?.familyId }
                        else -> familyId
                    }
                    if (!syncOperationBelongsToFamily(ownerFamilyId, familyId)) {
                        dao.markOperationConflict(operation.operationId, FAMILY_OWNERSHIP_CONFLICT_REASON)
                        return@forEach
                    }
                    if (operation.entityType == "baby" && operation.operation == "upsert") {
                        val baby = dao.getBaby(operation.entityId)
                        if (baby != null) {
                            val avatarFile = baby.avatarUri?.let { File(it) }
                            if (avatarFile != null && avatarFile.isFile) {
                                SyncHttp.uploadAvatar(base, familyId, baby.id, token, avatarFile)
                            } else {
                                SyncHttp.deleteAvatar(base, familyId, baby.id, token)
                            }
                        }
                    }
                    if (operation.entityType == "photo" && operation.operation == "upsert") {
                        val photo = dao.getPhoto(operation.entityId)
                        if (photo == null) {
                            dao.markOperationConflict(operation.operationId, "照片记录不存在，无法上传")
                            return@forEach
                        }
                        val localFile = File(photo.localPath)
                        if (!photo.originalOnDevice) {
                            // The NAS already owns the original; metadata-only updates must not upload a preview.
                        } else if (!localFile.isFile) {
                            // A previous upload may already exist on the NAS even if the local cache was removed.
                            // Restore it before deciding that this queued operation is unrecoverable.
                            try {
                                SyncHttp.downloadPhoto(base, familyId, photo.id, token, localFile)
                            } catch (restoreError: Exception) {
                                dao.markOperationConflict(operation.operationId, "本地照片和 NAS 备份均不可用：${restoreError.message ?: "照片不存在"}")
                                dao.setPhotoServerVersion(photo.id, -1L, "CONFLICT")
                                return@forEach
                            }
                        }
                        try {
                            if (photo.originalOnDevice) SyncHttp.uploadPhoto(base, familyId, photo.id, token, localFile, photo.photoMimeType ?: mimeTypeForPath(photo.localPath))
                        } catch (error: SyncHttpException) {
                            if (error.statusCode in 400..499) {
                                dao.markOperationConflict(operation.operationId, error.message ?: "照片上传被服务器拒绝")
                                dao.setPhotoServerVersion(photo.id, -1L, "CONFLICT")
                                return@forEach
                            }
                            throw error
                        }
                    }
                    val item = JSONObject()
                        .put("operation_id", operation.operationId)
                        .put("entity_type", operation.entityType)
                        .put("entity_id", operation.entityId)
                        .put("operation", operation.operation)
                        .put("payload", JSONObject(operation.payloadJson))
                    operation.baseVersion?.let { item.put("base_version", it) }
                    changes.put(item)
                }
                if (changes.length() == 0) {
                    batch++
                    continue
                }
                val submittedIds = (0 until changes.length()).map { changes.getJSONObject(it).getString("operation_id") }.toSet()
                val result = SyncHttp.request(base, "/api/sync/push", "POST", token,
                    JSONObject().put("family_id", familyId).put("changes", changes))
                val acknowledgedIds = mutableSetOf<String>()
                val accepted = result.optJSONArray("accepted") ?: JSONArray()
                for (i in 0 until accepted.length()) {
                    val item = accepted.getJSONObject(i)
                    val opId = item.optString("operation_id")
                    require(opId.isNotBlank() && opId in submittedIds) {
                        "同步响应包含未知的已接收操作，已保留本机待同步队列"
                    }
                    require(acknowledgedIds.add(opId)) {
                        "同步响应重复确认同一操作，已保留本机待同步队列"
                    }
                    val version = item.optLong("version", -1L)
                    require(version >= 0L) {
                        "服务器已接收操作但未返回有效版本号，已保留本机待同步队列"
                    }
                    val operation = pending.first { it.operationId == opId }
                    when (operation.entityType) {
                        "baby" -> dao.setBabyServerVersion(operation.entityId, version)
                        "event" -> dao.setEventServerVersion(operation.entityId, version)
                        "photo" -> {
                            dao.setPhotoServerVersion(operation.entityId, version, "SYNCED")
                            releasePhotoOriginalIfEnabled(applicationContext, dao, operation.entityId)
                        }
                    }
                    dao.removeOperation(opId)
                }
                val conflicts = result.optJSONArray("conflicts") ?: JSONArray()
                for (i in 0 until conflicts.length()) {
                    val item = conflicts.getJSONObject(i)
                    val operationId = item.optString("operation_id")
                    require(operationId.isNotBlank() && operationId in submittedIds) {
                        "同步响应包含未知的冲突操作，已保留本机待同步队列"
                    }
                    require(acknowledgedIds.add(operationId)) {
                        "同步响应重复确认同一操作，已保留本机待同步队列"
                    }
                    val operation = pending.first { it.operationId == operationId }
                    val serverVersion = item.optLong("server_version", -1L)
                    if (serverVersion >= 0L) {
                        // Rebase the local write onto the server's current version.
                        // Keep the local payload; never replace it with stale remote data.
                        when (operation.entityType) {
                            "baby" -> dao.setBabyServerVersion(operation.entityId, serverVersion)
                            "event" -> dao.updateEventServerVersionForRetry(operation.entityId, serverVersion)
                            "photo" -> dao.setPhotoServerVersion(operation.entityId, serverVersion, "PENDING")
                        }
                        dao.rebaseConflict(operationId, serverVersion)
                    } else {
                        dao.markOperationConflict(operationId, item.optString("reason", "sync conflict"))
                    }
                }
                val unacknowledged = submittedIds - acknowledgedIds
                require(unacknowledged.isEmpty()) {
                    "服务器未确认本批 ${unacknowledged.size} 项待同步记录，已安全保留本机数据，稍后将重试"
                }
                batch++
            }
            var cursor = SyncPreferences.cursor(applicationContext)
            var page = 0
            while (page < 10) {
                val pulled = SyncHttp.request(base, "/api/sync/pull?family_id=$familyId&cursor=$cursor&limit=500", token = token)
                val items = pulled.optJSONArray("items") ?: JSONArray()
                val next = pulled.optLong("next_cursor", cursor)
                if (next <= cursor) break
                for (i in 0 until items.length()) applyRemote(dao, items.getJSONObject(i), familyId)
                cursor = next
                SyncPreferences.saveCursor(applicationContext, cursor)
                page++
                // The server may filter obsolete photo-upserts while retaining
                // their cursor positions. Use scanned_count, not visible items,
                // to decide whether another page is available.
                if (pulled.optInt("scanned_count", items.length()) < 500) break
            }
            val remainingPending = dao.pendingOperationCount()
            require(remainingPending == 0) {
                "仍有 $remainingPending 项待同步记录未完成上传；本机数据已保留，稍后将安全重试"
            }
            val cloudStatus = SyncHttp.request(base, "/api/sync/status?family_id=$familyId", token = token)
            val localBabies = dao.getAllBabies().filter { it.familyId == familyId }
            val activeBabyIds = localBabies.map { it.id }.toSet()
            val localCanonical = buildList {
                localBabies.forEach { add("baby:${it.id}:${it.serverVersion ?: 0L}") }
                dao.getAllEvents().filter { it.babyId in activeBabyIds && it.deletedAt == null }
                    .forEach { add("event:${it.id}:${it.serverVersion ?: 0L}") }
                dao.getAllPhotos().filter { it.babyId in activeBabyIds && it.deletedAt == null }
                    .forEach { add("photo:${it.id}:${it.serverVersion ?: 0L}") }
            }.sorted().joinToString("\n")
            val localDigest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(localCanonical.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            val cloudDigest = cloudStatus.optString("digest")
            require(cloudDigest.matches(Regex("[a-fA-F0-9]{64}"))) {
                "云端一致性校验响应无效"
            }
            val consistent = localDigest.equals(cloudDigest, ignoreCase = true)
            SyncPreferences.saveConsistency(
                applicationContext,
                if (consistent) "一致" else "存在差异，请再次同步核对"
            )
            if (consistent) {
                SyncPreferences.markSyncSuccess(applicationContext)
            } else {
                // Do not present a completed transfer as a fully healthy sync when
                // the server-side record fingerprint still differs from this device.
                SyncPreferences.markSyncFailure(applicationContext, "同步传输已完成，但本机与云端数据摘要不一致；请再次同步核对")
            }
            Result.success()
        } catch (error: java.net.SocketTimeoutException) {
            SyncPreferences.markSyncFailure(applicationContext, error.message ?: "连接超时")
            Result.retry()
        } catch (error: java.io.IOException) {
            SyncPreferences.markSyncFailure(applicationContext, error.message ?: "网络连接失败")
            Result.retry()
        } catch (error: Exception) {
            SyncPreferences.markSyncFailure(applicationContext, error.message ?: "同步失败")
            val message = error.message.orEmpty()
            val statusCode = Regex("HTTP (\\d{3})").find(message)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (statusCode != null && !shouldRetrySyncStatus(statusCode)) Result.failure() else Result.retry()
        }
    }

    private suspend fun applyRemote(dao: com.rinzaki.babylog.data.BabyLogDao, item: JSONObject, familyId: String) {
        val type = item.optString("entity_type")
        val id = item.optString("entity_id")
        val operation = item.optString("operation")
        val payload = item.optJSONObject("payload") ?: JSONObject()
        val version = item.optLong("version", 0L)
        if (dao.hasLocalOperation(type, id)) return
        when (type) {
            "baby" -> {
                if (operation == "delete") {
                    dao.deleteBabyFromServer(id)
                    File(applicationContext.filesDir, "baby_avatars/$id.jpg").delete()
                    return
                }
                dao.upsertBaby(BabyEntity(
                    id = payload.optString("id", id),
                    familyId = familyId,
                    displayName = payload.optString("displayName", "宝宝"),
                    birthTime = payload.optLong("birthTime"),
                    createdAt = payload.optLong("createdAt"),
                    updatedAt = payload.optLong("updatedAt"),
                    sex = payload.optString("sex").takeIf { it.isNotBlank() && it != "null" },
                    avatarUri = if (payload.optBoolean("hasAvatar", false)) {
                        val avatarFile = File(applicationContext.filesDir, "baby_avatars/$id.jpg")
                        SyncHttp.downloadAvatar(SyncPreferences.server(applicationContext), familyId, id,
                            SyncPreferences.token(applicationContext), avatarFile)
                        avatarFile.absolutePath
                    } else {
                        File(applicationContext.filesDir, "baby_avatars/$id.jpg").delete()
                        null
                    },
                    birthWeightGrams = if (payload.isNull("birthWeightGrams")) null else payload.optInt("birthWeightGrams"),
                    birthLengthCm = if (payload.isNull("birthLengthCm")) null else payload.optDouble("birthLengthCm"),
                    birthHeadCircumferenceCm = if (payload.isNull("birthHeadCircumferenceCm")) null else payload.optDouble("birthHeadCircumferenceCm"),
                    serverVersion = version
                ))
            }
            "event" -> {
                if (operation == "delete") {
                    dao.softDeleteEventFromServer(id, item.optLong("created_at", System.currentTimeMillis() / 1000L) * 1000L, version)
                } else {
                    dao.upsertEvent(BabyEventEntity(
                        id = payload.optString("id", id),
                        babyId = payload.optString("babyId"),
                        eventType = payload.optString("eventType"),
                        occurredAt = payload.optLong("occurredAt"),
                        payloadJson = payload.optString("payloadJson", "{}"),
                        createdBy = payload.optString("createdBy").takeIf { it.isNotBlank() && it != "null" },
                        updatedAt = payload.optLong("updatedAt"),
                        syncStatus = "SYNCED",
                        serverVersion = version,
                        deletedAt = if (payload.isNull("deletedAt")) null else payload.optLong("deletedAt")
                    ))
                }
            }
            "photo" -> {
                val existing = dao.getPhoto(id)
                if (operation == "delete") {
                    dao.softDeletePhotoFromServer(id, item.optLong("created_at", System.currentTimeMillis() / 1000L) * 1000L, version)
                    existing?.let { File(it.localPath).delete() }
                } else {
                    val mime = payload.optString("mimeType", "image/jpeg")
                    val extension = extensionForMime(mime)
                    val unchanged = existing != null && existing.serverVersion == version
                    var storedPath = if (unchanged) existing!!.localPath else ""
                    var originalOnDevice = unchanged && existing!!.originalOnDevice
                    if (!unchanged) {
                        val original = File(applicationContext.filesDir, "baby_album_originals/$id$extension")
                        try {
                            SyncHttp.downloadPhoto(SyncPreferences.server(applicationContext), familyId, id,
                                SyncPreferences.token(applicationContext), original)
                        } catch (error: SyncHttpException) {
                            if (error.statusCode == 404) {
                                dao.softDeletePhotoFromServer(id, item.optLong("created_at", System.currentTimeMillis() / 1000L) * 1000L, version)
                                return
                            }
                            throw error
                        }
                        if (SyncPreferences.cloudPhotoStorageEnabled(applicationContext)) {
                            val preview = File(applicationContext.filesDir, "baby_album_previews/$id.jpg")
                            if (createPhotoPreview(original, preview)) {
                                storedPath = preview.absolutePath
                                originalOnDevice = false
                                original.delete()
                            } else {
                                storedPath = original.absolutePath
                                originalOnDevice = true
                            }
                        } else {
                            storedPath = original.absolutePath
                            originalOnDevice = true
                        }
                    }
                    dao.upsertPhoto(BabyPhotoEntity(
                        id = id,
                        babyId = payload.optString("babyId"),
                        localPath = storedPath,
                        caption = payload.optString("caption").takeIf { it.isNotBlank() && it != "null" },
                        takenAt = payload.optLong("takenAt"),
                        createdAt = payload.optLong("createdAt"),
                        updatedAt = payload.optLong("updatedAt"),
                        syncStatus = "SYNCED",
                        remotePath = "/api/photos/$id",
                        deletedAt = null,
                        serverVersion = version,
                        originalOnDevice = originalOnDevice,
                        photoMimeType = mime
                    ))
                }
            }
        }
    }
}

private object RoomAccess {
    @Volatile private var instance: BabyLogDatabase? = null
    fun database(context: Context): BabyLogDatabase = instance ?: synchronized(this) {
        instance ?: androidx.room.Room.databaseBuilder(context, BabyLogDatabase::class.java, "babylog.db")
            .addMigrations(BabyLogDatabase.MIGRATION_1_2, BabyLogDatabase.MIGRATION_2_3, BabyLogDatabase.MIGRATION_3_4, BabyLogDatabase.MIGRATION_4_5, BabyLogDatabase.MIGRATION_5_6)
            .enableMultiInstanceInvalidation().build().also { instance = it }
    }
}

fun babySyncPayload(baby: BabyEntity): JSONObject = JSONObject()
    .put("id", baby.id).put("familyId", baby.familyId).put("displayName", baby.displayName)
    .put("birthTime", baby.birthTime).put("createdAt", baby.createdAt).put("updatedAt", baby.updatedAt)
    .put("sex", baby.sex)
    .put("hasAvatar", !baby.avatarUri.isNullOrBlank())
    .put("birthWeightGrams", baby.birthWeightGrams)
    .put("birthLengthCm", baby.birthLengthCm)
    .put("birthHeadCircumferenceCm", baby.birthHeadCircumferenceCm)

fun eventSyncPayload(event: BabyEventEntity): JSONObject = JSONObject()
    .put("id", event.id).put("babyId", event.babyId).put("eventType", event.eventType)
    .put("occurredAt", event.occurredAt).put("payloadJson", event.payloadJson)
    .put("createdBy", event.createdBy).put("updatedAt", event.updatedAt)
    .put("deletedAt", event.deletedAt)

fun mimeTypeForPath(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "webp" -> "image/webp"
    "heic" -> "image/heic"
    "heif" -> "image/heif"
    else -> "image/jpeg"
}

fun extensionForMime(mime: String): String = when (mime.lowercase()) {
    "image/png" -> ".png"
    "image/webp" -> ".webp"
    "image/heic" -> ".heic"
    "image/heif" -> ".heif"
    else -> ".jpg"
}

fun photoSyncPayload(photo: BabyPhotoEntity, mimeType: String): JSONObject = JSONObject()
    .put("id", photo.id)
    .put("babyId", photo.babyId)
    .put("caption", photo.caption)
    .put("takenAt", photo.takenAt)
    .put("createdAt", photo.createdAt)
    .put("updatedAt", photo.updatedAt)
    .put("mimeType", mimeType)

package com.rinzaki.babylog.sync

import android.content.Context
import com.rinzaki.babylog.data.BabyPhotoEntity
import java.io.File

/**
 * Removes every app-managed local copy of a photo after its deletion is committed locally
 * or received from the family server. Never follows arbitrary paths from photo metadata.
 */
internal fun deletePhotoLocalArtifacts(context: Context, photo: BabyPhotoEntity) {
    val managedDirectories = listOf(
        File(context.filesDir, "baby_album"),
        File(context.filesDir, "baby_album_originals"),
        File(context.filesDir, "baby_album_previews"),
        File(context.cacheDir, "babylog_photo_originals")
    )
    val targets = linkedSetOf<File>()

    if (photo.id.isNotBlank()) {
        managedDirectories.forEach { directory ->
            directory.listFiles()
                ?.filter { it.isFile && it.name.startsWith("${photo.id}.") }
                ?.let(targets::addAll)
        }
    }

    val localFile = File(photo.localPath)
    val localCanonical = runCatching { localFile.canonicalFile }.getOrNull()
    if (localCanonical != null && managedDirectories.any { directory ->
            val root = runCatching { directory.canonicalPath }.getOrNull() ?: return@any false
            localCanonical.path.startsWith(root + File.separator)
        }
    ) {
        targets += localCanonical
    }

    targets.forEach { file -> runCatching { file.delete() } }
}


/**
 * Removes a superseded photo file only after its replacement has been committed to Room.
 * Both paths are constrained to BabyLog-managed directories; external files are never touched.
 */
internal fun deleteReplacedPhotoLocalArtifact(context: Context, previousPath: String, currentPath: String) {
    if (previousPath.isBlank() || currentPath.isBlank()) return
    val managedDirectories = listOf(
        File(context.filesDir, "baby_album"),
        File(context.filesDir, "baby_album_originals"),
        File(context.filesDir, "baby_album_previews"),
        File(context.cacheDir, "babylog_photo_originals")
    )
    val previous = runCatching { File(previousPath).canonicalFile }.getOrNull() ?: return
    val current = runCatching { File(currentPath).canonicalFile }.getOrNull() ?: return
    if (previous == current) return
    val isManaged = managedDirectories.any { directory ->
        val root = runCatching { directory.canonicalPath }.getOrNull() ?: return@any false
        previous.path.startsWith(root + File.separator)
    }
    if (isManaged) runCatching { previous.delete() }
}

package com.rinzaki.babylog.sync

import androidx.test.core.app.ApplicationProvider
import com.rinzaki.babylog.data.BabyPhotoEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhotoFileCleanupTest {
    @Test
    fun deletionRemovesEveryManagedCopyButPreservesOtherPhotosAndExternalFiles() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val id = "photo-to-delete"
        val managedDirectories = listOf(
            File(context.filesDir, "baby_album"),
            File(context.filesDir, "baby_album_originals"),
            File(context.filesDir, "baby_album_previews"),
            File(context.cacheDir, "babylog_photo_originals")
        )
        val ownedFiles = managedDirectories.mapIndexed { index, directory ->
            directory.mkdirs()
            File(directory, "$id.${listOf("jpg", "png", "jpg", "webp")[index]}").apply {
                writeBytes(byteArrayOf(1, 2, 3))
            }
        }
        val unrelated = File(managedDirectories.first(), "another-photo.jpg").apply {
            writeBytes(byteArrayOf(4, 5, 6))
        }
        val external = File(context.cacheDir, "user-owned-photo.jpg").apply {
            writeBytes(byteArrayOf(7, 8, 9))
        }
        val photo = BabyPhotoEntity(
            id = id,
            babyId = "baby",
            localPath = ownedFiles.first().absolutePath,
            caption = null,
            takenAt = 1L,
            createdAt = 1L,
            updatedAt = 1L
        )

        deletePhotoLocalArtifacts(context, photo)

        ownedFiles.forEach { assertFalse("Expected managed copy to be deleted: $it", it.exists()) }
        assertTrue(unrelated.isFile)
        assertTrue(external.isFile)
    }

    @Test
    fun deletionDoesNotFollowAnExternalLocalPath() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val external = File(context.cacheDir, "do-not-delete.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val photo = BabyPhotoEntity(
            id = "unknown-photo",
            babyId = "baby",
            localPath = external.absolutePath,
            caption = null,
            takenAt = 1L,
            createdAt = 1L,
            updatedAt = 1L
        )

        deletePhotoLocalArtifacts(context, photo)

        assertTrue(external.isFile)
    }
}

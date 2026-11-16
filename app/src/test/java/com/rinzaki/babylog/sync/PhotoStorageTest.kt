package com.rinzaki.babylog.sync

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhotoStorageTest {
    @Test
    fun cloudPhotoStorageIsDisabledByDefaultAndStoredPerDevice() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("babylog_sync", android.content.Context.MODE_PRIVATE).edit().clear().commit()

        assertFalse(SyncPreferences.cloudPhotoStorageEnabled(context))
        SyncPreferences.setCloudPhotoStorageEnabled(context, true)
        assertTrue(SyncPreferences.cloudPhotoStorageEnabled(context))
        SyncPreferences.setCloudPhotoStorageEnabled(context, false)
        assertFalse(SyncPreferences.cloudPhotoStorageEnabled(context))
    }

    @Test
    fun previewCreationDoesNotModifyOrDeleteOriginal() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val directory = File(context.cacheDir, "photo-storage-test").apply { mkdirs() }
        val original = File(directory, "original.png")
        val preview = File(directory, "preview.jpg")
        val bitmap = Bitmap.createBitmap(2400, 3000, Bitmap.Config.ARGB_8888)
        try {
            original.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        val originalSize = original.length()

        assertTrue(createPhotoPreview(original, preview))
        assertTrue(original.isFile)
        assertTrue(original.length() == originalSize)
        assertTrue(preview.isFile && preview.length() > 0L)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(preview.absolutePath, bounds)
        assertTrue(bounds.outWidth <= 1600)
        assertTrue(bounds.outHeight <= 2000)
        original.delete()
        preview.delete()
    }
}

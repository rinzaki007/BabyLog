package com.rinzaki.babylog

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlbumPhotoThumbnailCacheTest {
    @Test
    fun sampledThumbnailIsReusedFromTheSharedCache() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "album-thumbnail-${UUID.randomUUID()}.jpg")
        val source = Bitmap.createBitmap(1800, 1200, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { output ->
                assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 90, output))
            }
        } finally {
            source.recycle()
        }

        try {
            AlbumPhotoThumbnailCache.clearForTests()
            val first = AlbumPhotoThumbnailCache.load(file.absolutePath, 360, 360)
            assertNotNull(first)
            assertTrue(first!!.width <= 720)
            assertTrue(first.height <= 720)

            val second = AlbumPhotoThumbnailCache.load(file.absolutePath, 360, 360)
            assertSame(first, second)
        } finally {
            file.delete()
            AlbumPhotoThumbnailCache.clearForTests()
        }
    }

    @Test
    fun replacingAFileAtTheSamePathInvalidatesItsCachedThumbnail() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "album-thumbnail-${UUID.randomUUID()}.jpg")
        fun writeImage(width: Int, height: Int) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                file.outputStream().use { output ->
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
                }
            } finally {
                bitmap.recycle()
            }
        }

        try {
            AlbumPhotoThumbnailCache.clearForTests()
            writeImage(1800, 1200)
            val firstModified = file.lastModified()
            val first = AlbumPhotoThumbnailCache.load(file.absolutePath, 360, 360)
            assertNotNull(first)

            writeImage(360, 240)
            file.setLastModified(firstModified + 10_000L)
            val second = AlbumPhotoThumbnailCache.load(file.absolutePath, 360, 360)
            assertNotNull(second)
            assertNotSame(first, second)
            assertTrue(second!!.width < first!!.width)
        } finally {
            file.delete()
            AlbumPhotoThumbnailCache.clearForTests()
        }
    }
}

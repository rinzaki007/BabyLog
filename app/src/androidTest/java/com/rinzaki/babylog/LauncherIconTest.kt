package com.rinzaki.babylog

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherIconTest {

    @Test
    fun applicationAndLauncherActivityIconsLoadAndRender() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageManager = context.packageManager
        val applicationInfo = packageManager.getApplicationInfo(context.packageName, 0)

        assertNotEquals("Application icon resource must be declared", 0, applicationInfo.icon)
        assertRendersVisiblePixels(packageManager.getApplicationIcon(applicationInfo))

        val activity = ComponentName(context, MainActivity::class.java)
        val activityInfo = packageManager.getActivityInfo(activity, 0)
        assertNotEquals("Launcher activity icon resource must be declared", 0, activityInfo.icon)
        assertRendersVisiblePixels(packageManager.getActivityIcon(activity))

        val roundIconId = context.resources.getIdentifier(
            "ic_launcher_round",
            "mipmap",
            context.packageName
        )
        assertNotEquals("Round launcher icon resource must be packaged", 0, roundIconId)
        val roundBitmap = BitmapFactory.decodeResource(context.resources, roundIconId)
        assertNotNull("Round launcher bitmap must decode", roundBitmap)
        assertBitmapHasVisiblePixels(roundBitmap!!)
        roundBitmap.recycle()
    }

    private fun assertRendersVisiblePixels(drawable: Drawable) {
        val width = maxOf(drawable.intrinsicWidth, 192)
        val height = maxOf(drawable.intrinsicHeight, 192)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(Canvas(bitmap))
        assertBitmapHasVisiblePixels(bitmap)
        bitmap.recycle()
    }

    private fun assertBitmapHasVisiblePixels(bitmap: Bitmap) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val visiblePixels = pixels.count { Color.alpha(it) > 0 }
        assertTrue("Launcher icon decoded but rendered no visible pixels", visiblePixels > 100)
    }
}

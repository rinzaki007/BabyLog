package com.rinzaki.babylog

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotEquals
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
        val applicationIcon = packageManager.getApplicationIcon(applicationInfo)
        assertRendersVisiblePixels(applicationIcon)

        val activity = ComponentName(context, MainActivity::class.java)
        val activityInfo = packageManager.getActivityInfo(activity, 0)
        assertNotEquals("Launcher activity icon resource must be declared", 0, activityInfo.icon)
        val activityIcon = packageManager.getActivityIcon(activity)
        assertRendersVisiblePixels(activityIcon)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            assertTrue(
                "Android 8+ application icon should resolve as an adaptive icon",
                applicationIcon is AdaptiveIconDrawable
            )
        }

        val roundIconId = context.resources.getIdentifier(
            "ic_launcher_round",
            "mipmap",
            context.packageName
        )
        assertNotEquals("Round launcher icon resource must be packaged", 0, roundIconId)

        // Load through the app's Resources. PackageManager.getDrawable() may return null
        // for resource-backed adaptive icons even when the resource is present in the APK.
        val roundIcon = context.resources.getDrawable(roundIconId, context.theme)
        assertRendersVisiblePixels(roundIcon)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            assertTrue(
                "Android 8+ round launcher icon should resolve as an adaptive icon",
                roundIcon is AdaptiveIconDrawable
            )
        }
    }

    private fun assertRendersVisiblePixels(drawable: Drawable) {
        val width = maxOf(drawable.intrinsicWidth, 192)
        val height = maxOf(drawable.intrinsicHeight, 192)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val visiblePixels = pixels.count { Color.alpha(it) > 0 }
        assertTrue("Launcher icon decoded but rendered no visible pixels", visiblePixels > 100)
        bitmap.recycle()
    }
}

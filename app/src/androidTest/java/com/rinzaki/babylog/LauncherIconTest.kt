package com.rinzaki.babylog

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
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
                "Android 8+ launcher icon should resolve as an adaptive icon",
                applicationIcon is AdaptiveIconDrawable
            )
        }

        val roundIconId = context.resources.getIdentifier(
            "ic_launcher_round",
            "mipmap",
            context.packageName
        )
        assertNotEquals("Round launcher icon resource must be packaged", 0, roundIconId)
        val roundIcon = packageManager.getDrawable(context.packageName, roundIconId, applicationInfo)
        assertTrue("Round launcher icon resource must decode", roundIcon != null)
        assertRendersVisiblePixels(roundIcon!!)
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

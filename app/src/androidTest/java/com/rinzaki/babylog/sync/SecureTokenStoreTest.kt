package com.rinzaki.babylog.sync

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureTokenStoreTest {
    @Test
    fun encryptsTokensWithFreshIvAndRejectsTampering() {
        val token = "babylog-test-access-token"
        val first = SecureTokenStore.encrypt(token)
        val second = SecureTokenStore.encrypt(token)

        assertNotEquals(first, second)
        assertTrue(first != token)
        assertEquals(token, SecureTokenStore.decrypt(first))
        val tampered = first.dropLast(2) + if (first.last() == 'A') "B=" else "A="
        assertTrue(runCatching { SecureTokenStore.decrypt(tampered) }.isFailure)
    }

    @Test
    fun migratesLegacyPlaintextPreferenceWithoutChangingToken() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("babylog_sync", Context.MODE_PRIVATE)
        preferences.edit().clear().putString("token", "legacy-access-token").commit()

        assertEquals("legacy-access-token", SyncPreferences.token(context))
        assertNull(preferences.getString("token", null))
        val encrypted = preferences.getString("token_encrypted", null)
        assertTrue(!encrypted.isNullOrBlank())
        assertEquals("legacy-access-token", SecureTokenStore.decrypt(encrypted!!))

        preferences.edit().clear().commit()
    }
}

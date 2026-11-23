package com.rinzaki.babylog.sync

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SecureTokenStoreTest {
    private fun testKey(): SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun encryptsTokensWithFreshIvAndRejectsTampering() {
        val token = "babylog-test-access-token"
        val key = testKey()
        val first = SecureTokenStore.encrypt(token) { key }
        val second = SecureTokenStore.encrypt(token) { key }

        assertNotEquals(first, second)
        assertTrue(first != token)
        assertEquals(token, SecureTokenStore.decrypt(first) { key })

        val payload = Base64.decode(first, Base64.NO_WRAP)
        payload[payload.lastIndex] = (payload.last().toInt() xor 1).toByte()
        val tampered = Base64.encodeToString(payload, Base64.NO_WRAP)
        assertTrue(runCatching { SecureTokenStore.decrypt(tampered) { key } }.isFailure)
    }

    @Test
    fun migratesLegacyPlaintextPreferenceWithoutChangingToken() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("babylog_sync", Context.MODE_PRIVATE)
        val key = testKey()
        preferences.edit().clear().putString("token", "legacy-access-token").commit()

        val encryptLegacy: (String) -> String = { value -> SecureTokenStore.encrypt(value) { key } }
        assertEquals("legacy-access-token", SyncPreferences.token(context, encryptLegacy))
        assertNull(preferences.getString("token", null))
        val encrypted = preferences.getString("token_encrypted", null)
        assertTrue(!encrypted.isNullOrBlank())
        assertEquals("legacy-access-token", SecureTokenStore.decrypt(encrypted!!) { key })

        preferences.edit().clear().commit()
    }
    @Test
    fun savesValidatedServerAccountAndFamilyAsOneSessionSnapshot() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("babylog_sync", Context.MODE_PRIVATE)
        preferences.edit().clear().putLong("cursor", 42L).commit()

        try {
            SyncPreferences.saveAuthenticatedSession(
                context = context,
                server = " https://nas.example.com/ ",
                token = "new-access-token",
                accountEmail = "Baby@Example.com",
                familyId = "family-b"
            ) { "encrypted-test-token" }

            assertEquals("https://nas.example.com", preferences.getString("server", null))
            assertEquals("encrypted-test-token", preferences.getString("token_encrypted", null))
            assertNull(preferences.getString("token", null))
            assertEquals("baby@example.com", preferences.getString("account_email", null))
            assertEquals("family-b", preferences.getString("family_id", null))
            assertEquals(0L, preferences.getLong("cursor", -1L))
        } finally {
            preferences.edit().clear().commit()
        }
    }

}

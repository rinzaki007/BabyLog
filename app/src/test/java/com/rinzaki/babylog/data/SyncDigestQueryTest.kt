package com.rinzaki.babylog.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncDigestQueryTest {
    @Test
    fun canonicalRowsMatchServerOrderingAndExcludeDeletedOrForeignRecords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, BabyLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.babyLogDao()
            dao.upsertBaby(BabyEntity("baby-b", "family-a", "B", 1L, 1L, 1L, serverVersion = 4L))
            dao.upsertBaby(BabyEntity("baby-a", "family-a", "A", 1L, 1L, 1L, serverVersion = null))
            dao.upsertBaby(BabyEntity("foreign", "family-b", "Foreign", 1L, 1L, 1L, serverVersion = 8L))
            dao.upsertEvent(BabyEventEntity("event-z", "baby-a", "FEEDING", 10L, "{}", null, 10L, serverVersion = 2L))
            dao.upsertEvent(BabyEventEntity("event-deleted", "baby-a", "FEEDING", 11L, "{}", null, 11L, deletedAt = 12L, serverVersion = 9L))
            dao.upsertEvent(BabyEventEntity("event-foreign", "foreign", "FEEDING", 13L, "{}", null, 13L, serverVersion = 5L))
            dao.upsertPhoto(BabyPhotoEntity("photo-a", "baby-b", "/tmp/a.jpg", null, 20L, 20L, 20L, serverVersion = 3L))
            dao.upsertPhoto(BabyPhotoEntity("photo-deleted", "baby-b", "/tmp/deleted.jpg", null, 21L, 21L, 21L, deletedAt = 22L, serverVersion = 6L))

            assertEquals(
                listOf(
                    "baby:baby-a:0",
                    "baby:baby-b:4",
                    "event:event-z:2",
                    "photo:photo-a:3"
                ),
                dao.getActiveSyncCanonicalRows("family-a")
            )
            assertEquals(emptyList<String>(), dao.getActiveSyncCanonicalRows("missing-family"))
        } finally {
            database.close()
        }
    }
}

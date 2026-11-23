package com.rinzaki.babylog.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rinzaki.babylog.data.BabyEntity
import com.rinzaki.babylog.data.BabyEventEntity
import com.rinzaki.babylog.data.BabyLogDatabase
import com.rinzaki.babylog.data.BabyPhotoEntity
import com.rinzaki.babylog.data.SyncOperationEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncConflictRecoveryTest {
    @Test
    fun restoresOnlyOwnershipConflictsWhoseEntitiesBelongToTheActiveFamily() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, BabyLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val dao = database.babyLogDao()
            dao.upsertBaby(BabyEntity("baby-a", "family-a", "A 宝宝", 1_000L, 1_000L, 1_000L))
            dao.upsertBaby(BabyEntity("baby-b", "family-b", "B 宝宝", 1_000L, 1_000L, 1_000L))
            dao.upsertEvent(BabyEventEntity("event-a", "baby-a", "FEEDING", 2_000L, "{}", null, 2_000L))
            dao.upsertPhoto(BabyPhotoEntity("photo-a", "baby-a", "/tmp/photo-a.jpg", null, 3_000L, 3_000L, 3_000L))

            fun conflict(operationId: String, entityType: String, entityId: String, reason: String) =
                SyncOperationEntity(operationId, entityType, entityId, "upsert", "{}", null, 4_000L, "CONFLICT", reason)

            dao.enqueueOperation(conflict("baby-a-op", "baby", "baby-a", FAMILY_OWNERSHIP_CONFLICT_REASON))
            dao.enqueueOperation(conflict("event-a-op", "event", "event-a", FAMILY_OWNERSHIP_CONFLICT_REASON))
            dao.enqueueOperation(conflict("photo-a-op", "photo", "photo-a", FAMILY_OWNERSHIP_CONFLICT_REASON))
            dao.enqueueOperation(conflict("baby-b-op", "baby", "baby-b", FAMILY_OWNERSHIP_CONFLICT_REASON))
            dao.enqueueOperation(conflict("event-a-version-op", "event", "event-a", "HTTP 409: version conflict"))

            val restored = dao.restoreFamilyOwnershipConflicts("family-a", FAMILY_OWNERSHIP_CONFLICT_REASON)

            assertEquals(3, restored)
            assertEquals(
                setOf("baby-a-op", "event-a-op", "photo-a-op"),
                dao.pendingOperations().map { it.operationId }.toSet()
            )
            assertEquals(
                setOf("baby-b-op", "event-a-version-op"),
                dao.conflictOperations().map { it.operationId }.toSet()
            )

            // A local edit made after the snapshot began must survive snapshot cleanup.
            dao.enqueueOperation(SyncOperationEntity(
                "new-event-op", "event", "event-a", "upsert", "{}", null, 5_000L
            ))
            assertEquals(4, dao.clearSyncOperationsForFamily("family-a", 4_500L))
            assertEquals(setOf("baby-b-op"), dao.conflictOperations().map { it.operationId }.toSet())
            assertEquals(setOf("new-event-op"), dao.pendingOperations().map { it.operationId }.toSet())
            assertEquals(1, dao.pendingOperationCount())
        } finally {
            database.close()
        }
    }

    @Test
    fun firstAccountLinkClaimsOnlyUnassignedOfflineProfiles() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, BabyLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val dao = database.babyLogDao()
            dao.upsertBaby(BabyEntity("offline-placeholder", UNASSIGNED_FAMILY_ID, "离线宝宝", 1_000L, 1_000L, 1_000L))
            dao.upsertBaby(BabyEntity("offline-empty", "", "未归属宝宝", 1_000L, 1_000L, 1_000L))
            dao.upsertBaby(BabyEntity("offline-not-in-snapshot", UNASSIGNED_FAMILY_ID, "稍后创建的宝宝", 1_000L, 1_000L, 1_000L))
            dao.upsertBaby(BabyEntity("other-family", "family-b", "其他家庭宝宝", 1_000L, 1_000L, 1_000L))

            assertEquals(
                2,
                dao.assignUnassignedBabiesToFamily(
                    "family-a",
                    UNASSIGNED_FAMILY_ID,
                    listOf("offline-placeholder", "offline-empty")
                )
            )
            assertEquals("family-a", dao.getBaby("offline-placeholder")?.familyId)
            assertEquals("family-a", dao.getBaby("offline-empty")?.familyId)
            assertEquals(UNASSIGNED_FAMILY_ID, dao.getBaby("offline-not-in-snapshot")?.familyId)
            assertEquals("family-b", dao.getBaby("other-family")?.familyId)
        } finally {
            database.close()
        }
    }

    @Test
    fun firstFamilyLinkQueuesUnassignedBabyEventsAndPhotosForSync() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, BabyLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val dao = database.babyLogDao()
            val localBaby = BabyEntity("local-baby", UNASSIGNED_FAMILY_ID, "离线宝宝", 1_000L, 1_000L, 1_000L)
            dao.upsertBaby(localBaby)
            dao.enqueueOperation(SyncOperationEntity(
                "old-baby-op", "baby", "local-baby", "upsert",
                babySyncPayload(localBaby).toString(), null, 1_500L
            ))
            dao.upsertEvent(BabyEventEntity("local-event", "local-baby", "FEEDING", 2_000L, "{\"amountMl\":120}", null, 2_000L))
            dao.upsertPhoto(BabyPhotoEntity("local-photo", "local-baby", "/tmp/local-photo.jpg", null, 3_000L, 3_000L, 3_000L))
            dao.upsertBaby(BabyEntity("foreign-baby", "family-b", "其他家庭宝宝", 1_000L, 1_000L, 1_000L))

            val unassigned = dao.getUnassignedBabies(UNASSIGNED_FAMILY_ID)
            assertEquals(1, enqueueUnassignedFamilyData(dao, unassigned, "family-a"))
            assertEquals("family-a", dao.getBaby("local-baby")?.familyId)
            assertEquals("family-b", dao.getBaby("foreign-baby")?.familyId)
            assertEquals(3, dao.pendingOperationCount())
            val queued = dao.pendingOperations()
            assertEquals(setOf("baby", "event", "photo"), queued.map { it.entityType }.toSet())
            assertTrue(queued.first { it.entityType == "baby" }.payloadJson.contains("\"familyId\":\"family-a\""))
        } finally {
            database.close()
        }
    }
}

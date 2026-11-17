package com.rinzaki.babylog.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BabyLogDaoPaginationTest {
    private lateinit var database: BabyLogDatabase
    private lateinit var dao: BabyLogDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, BabyLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.babyLogDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun timelinePageAppliesDateTypeAndOffsetFilters() = runBlocking {
        dao.upsertBaby(BabyEntity("baby", "family", "宝宝", 1L, 1L, 1L))
        dao.upsertEvent(BabyEventEntity("feed-1", "baby", "FEEDING", 100L, "{}", null, 100L))
        dao.upsertEvent(BabyEventEntity("diaper", "baby", "DIAPER", 150L, "{}", null, 150L))
        dao.upsertEvent(BabyEventEntity("feed-2", "baby", "FEEDING", 200L, "{}", null, 200L))
        dao.upsertEvent(BabyEventEntity("note", "baby", "NOTE", 180L, "{}", null, 180L))
        dao.upsertEvent(BabyEventEntity("vaccine", "baby", "VACCINE", 170L, "{}", null, 170L))

        val firstPage = dao.getTimelinePage("baby", 100L, 201L, "FEEDING", 1, 0)
        val secondPage = dao.getTimelinePage("baby", 100L, 201L, "FEEDING", 1, 1)

        assertEquals(listOf("feed-2"), firstPage.map { it.id })
        assertEquals(listOf("feed-1"), secondPage.map { it.id })
        assertEquals(2, dao.countTimelineEvents("baby", 100L, 201L, "FEEDING"))
    }

    @Test
    fun photoPagesUseStableNewestFirstOrderAndExcludeDeletedPhotos() = runBlocking {
        dao.upsertBaby(BabyEntity("baby", "family", "宝宝", 1L, 1L, 1L))
        dao.upsertPhoto(BabyPhotoEntity("photo-1", "baby", "/tmp/1.jpg", null, 100L, 100L, 100L))
        dao.upsertPhoto(BabyPhotoEntity("photo-2", "baby", "/tmp/2.jpg", null, 300L, 300L, 300L))
        dao.upsertPhoto(BabyPhotoEntity("photo-3", "baby", "/tmp/3.jpg", null, 200L, 200L, 200L))

        val firstPage = dao.getPhotoPage("baby", 2, 0)
        val secondPage = dao.getPhotoPage("baby", 2, 2)
        assertEquals(listOf("photo-2", "photo-3"), firstPage.map { it.id })
        assertEquals(listOf("photo-1"), secondPage.map { it.id })
        assertEquals(3, dao.observePhotoCount("baby").first())

        dao.softDeletePhoto("photo-3", 400L)
        assertEquals(2, dao.observePhotoCount("baby").first())
        assertEquals(listOf("photo-2", "photo-1"), dao.getPhotoPage("baby", 10, 0).map { it.id })
    }

    @Test
    fun otherCategoryExcludesVaccineEvents() = runBlocking {
        dao.upsertBaby(BabyEntity("baby", "family", "宝宝", 1L, 1L, 1L))
        dao.upsertEvent(BabyEventEntity("note", "baby", "NOTE", 180L, "{}", null, 180L))
        dao.upsertEvent(BabyEventEntity("vaccine", "baby", "VACCINE", 170L, "{}", null, 170L))

        val page = dao.getTimelinePage("baby", null, null, "OTHER", 100, 0)

        assertEquals(listOf("note"), page.map { it.id })
        assertEquals(1, dao.countTimelineEvents("baby", null, null, "OTHER"))
    }

    @Test
    fun supplementCategoryIsSeparateFromOtherRecords() = runBlocking {
        dao.upsertBaby(BabyEntity("baby", "family", "宝宝", 1L, 1L, 1L))
        dao.upsertEvent(BabyEventEntity("supplement", "baby", "SUPPLEMENT", 220L, """{"supplementName":"维生素AD"}""", null, 220L))
        dao.upsertEvent(BabyEventEntity("solid-food", "baby", "SOLID_FOOD", 200L, """{"foodName":"米粉"}""", null, 200L))
        dao.upsertEvent(BabyEventEntity("note", "baby", "NOTE", 180L, "{}", null, 180L))
        dao.upsertEvent(BabyEventEntity("vaccine", "baby", "VACCINE", 170L, "{}", null, 170L))

        val supplements = dao.getTimelinePage("baby", null, null, "SUPPLEMENT", 100, 0)
        val solidFoods = dao.getTimelinePage("baby", null, null, "SOLID_FOOD", 100, 0)
        val others = dao.getTimelinePage("baby", null, null, "OTHER", 100, 0)

        assertEquals(listOf("supplement"), supplements.map { it.id })
        assertEquals(listOf("solid-food"), solidFoods.map { it.id })
        assertEquals(listOf("note"), others.map { it.id })
        assertEquals(1, dao.countTimelineEvents("baby", null, null, "SUPPLEMENT"))
        assertEquals(1, dao.countTimelineEvents("baby", null, null, "SOLID_FOOD"))
    }

    @Test
    fun scopedEventFeedsOnlyLoadMatchingBabyTypeAndDateRange() = runBlocking {
        dao.upsertBaby(BabyEntity("baby", "family", "宝宝", 1L, 1L, 1L))
        dao.upsertBaby(BabyEntity("other-baby", "family", "另一个宝宝", 1L, 1L, 1L))
        dao.upsertEvent(BabyEventEntity("today-feed", "baby", "FEEDING", 200L, "{}", null, 200L))
        dao.upsertEvent(BabyEventEntity("old-feed", "baby", "FEEDING", 50L, "{}", null, 50L))
        dao.upsertEvent(BabyEventEntity("growth", "baby", "GROWTH", 180L, "{}", null, 180L))
        dao.upsertEvent(BabyEventEntity("vaccine", "baby", "VACCINE", 190L, "{}", null, 190L))
        dao.upsertEvent(BabyEventEntity("deleted-feed", "baby", "FEEDING", 210L, "{}", null, 210L, deletedAt = 220L))
        dao.upsertEvent(BabyEventEntity("other-baby-feed", "other-baby", "FEEDING", 205L, "{}", null, 205L))

        assertEquals(
            listOf("today-feed", "growth"),
            dao.observeNonVaccineEventsInRange("baby", 100L, 200L).first().map { it.id }
        )
        assertEquals(
            listOf("growth"),
            dao.observeEventsByType("baby", "GROWTH").first().map { it.id }
        )
        assertEquals("5:210", dao.observeEventRevision("baby").first())

        dao.upsertEvent(BabyEventEntity("old-feed", "baby", "FEEDING", 50L, "{}", null, 300L))
        assertEquals("5:300", dao.observeEventRevision("baby").first())
    }
}

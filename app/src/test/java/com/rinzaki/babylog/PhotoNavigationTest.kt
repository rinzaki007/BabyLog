package com.rinzaki.babylog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoNavigationTest {
    @Test
    fun swipeLeftMovesToOlderPhotoAndSwipeRightMovesToNewerPhoto() {
        assertEquals(3, photoIndexAfterHorizontalSwipe(2, -120f, 72f, 5))
        assertEquals(1, photoIndexAfterHorizontalSwipe(2, 120f, 72f, 5))
    }

    @Test
    fun shortSwipesAndGalleryBoundariesDoNotChangePhoto() {
        assertNull(photoIndexAfterHorizontalSwipe(2, -60f, 72f, 5))
        assertNull(photoIndexAfterHorizontalSwipe(0, 120f, 72f, 5))
        assertNull(photoIndexAfterHorizontalSwipe(4, -120f, 72f, 5))
    }

    @Test
    fun invalidIndicesAndEmptyGalleryAreIgnored() {
        assertNull(photoIndexAfterHorizontalSwipe(-1, -120f, 72f, 5))
        assertNull(photoIndexAfterHorizontalSwipe(0, -120f, 72f, 0))
    }
}

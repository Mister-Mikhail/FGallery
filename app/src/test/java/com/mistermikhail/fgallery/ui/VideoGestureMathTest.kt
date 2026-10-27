package com.mistermikhail.fgallery.ui

import org.junit.Assert.*
import org.junit.Test

class VideoGestureMathTest {
    @Test fun centralVerticalSwipeOnlyAndPagerHorizontalPrecedence() {
        val edge = 28f
        assertEquals(VideoAdjustmentSide.BRIGHTNESS, videoGestureSide(100f, 350f, 1080f, 1000f, edge))
        assertEquals(VideoAdjustmentSide.VOLUME, videoGestureSide(850f, 350f, 1080f, 1000f, edge))
        assertNull(videoGestureSide(10f, 500f, 1080f, 1000f, edge))
        assertNull(videoGestureSide(300f, 100f, 1080f, 1000f, edge))
        assertNull(videoGestureSide(300f, 810f, 1080f, 1000f, edge))
        assertTrue(isUnambiguousVertical(8f, 40f, 12f))
        assertFalse(isUnambiguousVertical(40f, 8f, 12f))
        assertFalse(isUnambiguousVertical(8f, 10f, 12f))
    }

    @Test fun adjustmentIsRelativeCumulativeAndHalfRangePerFullZone() {
        assertEquals(.75f, videoAdjustmentFraction(.25f, -600f, 1000f), .0001f)
        assertEquals(.5f, videoAdjustmentFraction(.75f, 300f, 1000f), .0001f)
        assertEquals(1f, videoAdjustmentFraction(.8f, -600f, 1000f), .0001f)
        assertEquals(0f, videoAdjustmentFraction(.1f, 600f, 1000f), .0001f)
    }

    @Test fun timestampsRemainCompactAndCorrect() {
        assertEquals("0:00", formatVideoTimestamp(0))
        assertEquals("1:05", formatVideoTimestamp(65000))
        assertEquals("1:01:05", formatVideoTimestamp(3665000))
    }
}

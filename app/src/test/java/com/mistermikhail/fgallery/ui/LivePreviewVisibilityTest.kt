package com.mistermikhail.fgallery.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LivePreviewVisibilityTest {
    @Test fun fullyVisible() {
        assertEquals(1f, visibleHeightFraction(100, 200, 0, 500), 0.0001f)
    }

    @Test fun halfVisibleAtTopAndBottom() {
        assertEquals(0.5f, visibleHeightFraction(-100, 200, 0, 500), 0.0001f)
        assertEquals(0.5f, visibleHeightFraction(400, 200, 0, 500), 0.0001f)
    }

    @Test fun lessThanHalfStopsPreview() {
        assert(visibleHeightFraction(-101, 200, 0, 500) < 0.5f)
        assert(visibleHeightFraction(401, 200, 0, 500) < 0.5f)
    }

    @Test fun fullyClippedOrInvalidHeight() {
        assertEquals(0f, visibleHeightFraction(-300, 200, 0, 500), 0.0001f)
        assertEquals(0f, visibleHeightFraction(600, 200, 0, 500), 0.0001f)
        assertEquals(0f, visibleHeightFraction(0, 0, 0, 500), 0.0001f)
    }
}

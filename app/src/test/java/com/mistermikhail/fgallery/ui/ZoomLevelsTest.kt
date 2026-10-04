package com.mistermikhail.fgallery.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomLevelsTest {
    @Test fun firstStopTracksFitScaleForDslrAndSmallImages() {
        for (fit in listOf(0.08f, 0.25f, 1f, 2f)) {
            val maximum = 8f
            val result = firstDoubleTapZoom(fit, maximum)
            assertEquals(1.30f, result / fit, 0.0001f)
        }
    }
    @Test fun firstStopCannotExceedMaximumWhenImageIsAlreadyLarger() {
        assertEquals(10f, firstDoubleTapZoom(10f, 8f), 0.0001f)
    }

    @Test fun pinchBackToFitRestartsTheCycleForEverySourceSize() {
        for (fit in listOf(.08f, .25f, 1f, 2f)) {
            assertEquals(fit * 1.3f, nextDoubleTapZoom(fit, fit, 8f), .0001f)
            assertEquals(7.2f, nextDoubleTapZoom(fit * 1.3f, fit, 8f), .0001f)
            assertEquals(fit, nextDoubleTapZoom(7.2f, fit, 8f), .0001f)
            assertEquals(fit * 1.3f, nextDoubleTapZoom(fit, fit, 8f), .0001f)
        }
    }
}

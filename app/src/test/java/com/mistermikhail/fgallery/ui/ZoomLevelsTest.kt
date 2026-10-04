package com.mistermikhail.fgallery.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomLevelsTest {
    @Test fun firstStopTracksFitScaleForDslrAndSmallImages() {
        for (fit in listOf(0.08f, 0.25f, 1f, 2f)) {
            val maximum = 8f
            val result = firstDoubleTapZoom(fit, maximum)
            assertEquals(.30f, (result - fit) / (maximum - fit), .0001f)
        }
    }
    @Test fun firstStopCannotExceedMaximumWhenImageIsAlreadyLarger() {
        assertEquals(10f, firstDoubleTapZoom(10f, 8f), 0.0001f)
    }

    @Test fun pinchBackToFitRestartsTheCycleForEverySourceSize() {
        for (fit in listOf(.08f, .25f, 1f, 2f)) {
            assertEquals(fit + (8f - fit) * .3f, nextDoubleTapZoom(fit, fit, 8f), .0001f)
            assertEquals(fit + (8f - fit) * .9f, nextDoubleTapZoom(fit + (8f - fit) * .3f, fit, 8f), .0001f)
            assertEquals(fit, nextDoubleTapZoom(fit + (8f - fit) * .9f, fit, 8f), .0001f)
            assertEquals(fit + (8f - fit) * .3f, nextDoubleTapZoom(fit, fit, 8f), .0001f)
        }
    }
    @Test fun nativePixelRangeUsesImageFitInsteadOfOversamplingMaximum() {
        for (fit in listOf(.08f, .25f, .8f)) {
            val first = nextDoubleTapZoom(fit, fit, 1f)
            val second = nextDoubleTapZoom(first, fit, 1f)
            assertEquals(.30f, (first - fit) / (1f - fit), .0001f)
            assertEquals(.90f, (second - fit) / (1f - fit), .0001f)
            assertEquals(fit, nextDoubleTapZoom(second, fit, 1f), .0001f)
        }
        assertEquals(2f, nextDoubleTapZoom(2f, 2f, 2f), .0001f)
    }

}

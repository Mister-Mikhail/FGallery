package com.mistermikhail.fgallery.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomLevelsTest {
    @Test fun firstTapDoublesFittedLinearSizeAcrossSourceSizes() {
        for (fit in listOf(.08f, .25f, 1f, 2f)) {
            val maximum = maxOf(8f, fit * 8f)
            assertEquals(fit * 2f, firstDoubleTapZoom(fit, maximum), .0001f)
        }
    }
    @Test fun largeImageSecondStopUsesTheSameCeilingAsPinch() {
        for (fit in listOf(.08f, .25f, .49f)) {
            val maximum = maxOf(8f, fit * 8f)
            val first = nextDoubleTapZoom(fit, fit, maximum)
            val second = nextDoubleTapZoom(first, fit, maximum)
            assertEquals(.90f, (second - fit) / (maximum - fit), .0001f)
            assertEquals(fit, nextDoubleTapZoom(second, fit, maximum), .0001f)
        }
    }
    @Test fun smallImagesUseTwoStopsEvenWhenPinchAllowsMore() {
        for (fit in listOf(.5f, 1f, 2f)) {
            val maximum = maxOf(8f, fit * 8f)
            val first = nextDoubleTapZoom(fit, fit, maximum, smallImage = true)
            assertEquals(fit * 2f, first, .0001f)
            assertEquals(fit, nextDoubleTapZoom(first, fit, maximum, smallImage = true), .0001f)
        }
    }
    @Test fun pinchBackToFitRestartsInsteadOfAdvancingAnOldTapCounter() {
        val fit = .08f
        val first = nextDoubleTapZoom(fit, fit, 8f)
        nextDoubleTapZoom(first, fit, 8f)
        assertEquals(first, nextDoubleTapZoom(fit, fit, 8f), .0001f)
        assertEquals(first, nextDoubleTapZoom(fit * 1.01f, fit, 8f), .0001f)
    }
    @Test fun malformedOrAlreadyFittedLimitsStayFiniteAndDoNotShrink() {
        assertEquals(10f, firstDoubleTapZoom(10f, 8f), .0001f)
        assertEquals(2f, nextDoubleTapZoom(Float.NaN, 1f, Float.NaN), .0001f)
    }
}

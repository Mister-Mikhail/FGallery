package com.mistermikhail.fgallery.ui

import org.junit.Assert.assertEquals
import kotlin.math.pow
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
            assertEquals(.85f, kotlin.math.ln(second / fit) / kotlin.math.ln(maximum / fit), .0001f)
            assertEquals(secondDoubleTapZoom(fit, maximum), second, .0001f)
            assertEquals(true, second < maximum)
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
    @Test fun diagnosticPowerSelectionMovesSecondTapThroughDoublings() {
        val fit = .124f
        val maximum = 8f
        val first = fit * 2f
        val a = nextDoubleTapZoom(first, fit, maximum, power = .80f)
        val b = nextDoubleTapZoom(first, fit, maximum, power = .85f)
        val c = nextDoubleTapZoom(first, fit, maximum, power = .90f)
        assertEquals(fit * (maximum / fit).toDouble().pow(.85).toFloat(), b, .0001f)
        assertEquals(true, a < b && b < c && c < maximum)
        assertEquals(fit, nextDoubleTapZoom(c, fit, maximum, power = .90f), .0001f)
    }

    @Test fun pinchCanContinueBeyondSecondTapToTheExistingMaximum() {
        for (p in listOf(.80f, .85f, .90f)) {
            val f = .124f
            val m = 8f
            val second = nextDoubleTapZoom(f * 2f, f, m, power = p)
            assertEquals(true, second < m)
            assertEquals(true, m / second > 1.2f)
        }
    }

}

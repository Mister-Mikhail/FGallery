package com.mistermikhail.fgallery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class RawOpeningIndicatorGeometryTest {
    private val circle = (2 * PI).toFloat()

    @Test fun trackIsOneAndHalfTimesMeasuredCaptionWidth() {
        assertEquals(1.5f, RAW_INDICATOR_TRACK_MULTIPLIER, 0f)
        assertEquals(34, RAW_INDICATOR_HEIGHT_DP)
        assertEquals("OPEN RAW", RAW_INDICATOR_TEXT)
        val width = 100f
        for (i in RAW_INDICATOR_TEXT.indices) {
            for (p in 0..240) {
                val x = rawLetterChaseOffset(circle * p / 240f, i, width)
                assertTrue("Glyph must remain inside extra 50% track", x in 0f..(width * .5f))
            }
        }
    }

    @Test fun directionReversesSmoothlyTwicePerCycle() {
        val width = 100f
        val leftMiddle = rawLetterChaseOffset(0f, 0, width)
        val right = rawLetterChaseOffset((PI / 2).toFloat(), 0, width)
        val center = rawLetterChaseOffset(PI.toFloat(), 0, width)
        val left = rawLetterChaseOffset((3 * PI / 2).toFloat(), 0, width)
        assertEquals(25f, leftMiddle, .0001f)
        assertEquals(50f, right, .0001f)
        assertEquals(25f, center, .0001f)
        assertEquals(0f, left, .0001f)
        assertTrue(rawLetterChaseOffset(.4f, 0, width) > leftMiddle)
        assertTrue(rawLetterChaseOffset(2.3f, 0, width) < right)
        assertEquals(leftMiddle, rawLetterChaseOffset(circle, 0, width), .0001f)
    }

    @Test fun eachLetterChasesWithSmallPhaseLag() {
        val width = 100f
        val a = rawLetterChaseOffset(.8f, 0, width)
        val b = rawLetterChaseOffset(.8f, 1, width)
        val c = rawLetterChaseOffset(.8f, 2, width)
        assertTrue("Right-going text: later characters follow", a > b && b > c)
        assertTrue("Only a subtle delay, not flying characters", abs(a - c) < width * .10f)
        val leftGoing = (3 * PI / 2).toFloat() + .4f
        assertTrue(rawLetterChaseOffset(leftGoing, 0, width) <
            rawLetterChaseOffset(leftGoing, 1, width))
    }

    @Test fun invalidWidthIsSafe() {
        assertEquals(0f, rawLetterChaseOffset(1f, 4, 0f), 0f)
        assertEquals(0f, rawLetterChaseOffset(1f, 4, -1f), 0f)
    }
}

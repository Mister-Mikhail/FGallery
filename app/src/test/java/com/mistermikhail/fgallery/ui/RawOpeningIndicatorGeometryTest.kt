package com.mistermikhail.fgallery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RawOpeningIndicatorGeometryTest {
    @Test fun lettersFollowForwardWithoutOvertaking() {
        for (rotation in listOf(0f, 35f, 90f, 175f, 270f, 359f)) {
            val angles = (0..7).map { rawLetterOrbitAngle(rotation, it) }
            angles.zipWithNext().forEach { (first, next) ->
                assertTrue("Glyph order must be forward, not reversed", next > first)
                assertTrue("Orbit glyphs need separation", next - first > 13f)
                assertTrue("Glyph spacing should stay compact", next - first < 27f)
            }
        }
    }

    @Test fun rotationLoopsSmoothlyAndCircleIsLessThanHalfOriginalSize() {
        for (index in 0..7) {
            assertEquals(
                rawLetterOrbitAngle(0f, index) + 360f,
                rawLetterOrbitAngle(360f, index),
                .0001f,
            )
        }
        assertTrue(RAW_INDICATOR_SIZE_DP <= 62)
    }

    @Test fun waveProducesVisibleLetterCatchUpWithoutOvertaking() {
        val firstGap = rawLetterOrbitAngle(0f, 1) - rawLetterOrbitAngle(0f, 0)
        val laterGap = rawLetterOrbitAngle(75f, 1) - rawLetterOrbitAngle(75f, 0)
        assertTrue("Relative glyph spacing must change during chase", kotlin.math.abs(firstGap - laterGap) > .5f)
    }
}

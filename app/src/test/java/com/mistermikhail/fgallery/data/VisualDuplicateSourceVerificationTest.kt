package com.mistermikhail.fgallery.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualDuplicateSourceVerificationTest {
    @Test fun sourceDecodeBoundsRejectUnknownOrOversizedImages() {
        assertFalse(safeSourceDimensions(0, 100))
        assertFalse(safeSourceDimensions(-1, 100))
        assertFalse(safeSourceDimensions(100, 0))
        assertFalse(safeSourceDimensions(5000, 5000))
        assertTrue(safeSourceDimensions(3000, 4000))
    }

    @Test fun sourceDecodeBoundsUseLongArithmetic() {
        assertFalse(safeSourceDimensions(Int.MAX_VALUE, Int.MAX_VALUE))
        assertTrue(safeSourceDimensions(1, 1))
        assertFalse(safeSourceDimensions(2, 2, 3))
    }
}

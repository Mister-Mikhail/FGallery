package com.mistermikhail.fgallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintCandidateWindowTest {
    @Test fun rotatesPastUnreadablePrefixInsteadOfRetryingItForever() {
        val candidates = (0 until 150).toList()
        val first = fingerprintCandidateWindow(candidates, 0, 64)
        val second = fingerprintCandidateWindow(candidates, first.size, 64)
        val third = fingerprintCandidateWindow(candidates, first.size + second.size, 64)
        assertEquals((0 until 64).toList(), first)
        assertEquals((64 until 128).toList(), second)
        assertTrue(149 in third)
        assertEquals(0, third[22])
    }

    @Test fun respectsBudgetsAndWrapsAfterCandidatesShrink() {
        assertTrue(fingerprintCandidateWindow(emptyList<Int>(), 5, 10).isEmpty())
        assertTrue(fingerprintCandidateWindow(listOf(1, 2), 0, 0).isEmpty())
        assertEquals(listOf(3, 1, 2), fingerprintCandidateWindow(listOf(1, 2, 3), 5, 10))
        assertEquals(listOf(2, 3), fingerprintCandidateWindow(listOf(1, 2, 3), 1, 2))
    }
}

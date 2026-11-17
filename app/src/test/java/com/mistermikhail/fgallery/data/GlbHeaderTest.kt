package com.mistermikhail.fgallery.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlbHeaderTest {
    private fun header(total: Int = 24, jsonLength: Int = 4): ByteArray =
        ByteArray(20).also { bytes ->
            fun put(offset: Int, value: Int) {
                for (i in 0..3) bytes[offset + i] = (value ushr (i * 8)).toByte()
            }
            put(0, 0x46546C67)
            put(4, 2)
            put(8, total)
            put(12, jsonLength)
            put(16, 0x4E4F534A)
        }

    @Test fun acceptsMinimalValidPrefix() {
        assertTrue(GlbHeader.isValidPrefix(header(), 24))
    }

    @Test fun rejectsTruncatedOrInconsistentHeaders() {
        assertFalse(GlbHeader.isValidPrefix(ByteArray(11), 24))
        assertFalse(GlbHeader.isValidPrefix(header(), 20))
        assertFalse(GlbHeader.isValidPrefix(header(total = 28), 24))
        assertFalse(GlbHeader.isValidPrefix(header(jsonLength = 8), 24))
        assertFalse(GlbHeader.isValidPrefix(header(jsonLength = 0), 24))
        assertFalse(GlbHeader.isValidPrefix(header(total = 23), 23))
        assertFalse(GlbHeader.isValidPrefix(header().also { it[0] = 0 }, 24))
        assertFalse(GlbHeader.isValidPrefix(header().also { it[4] = 1 }, 24))
        assertFalse(GlbHeader.isValidPrefix(header().also { it[16] = 0 }, 24))
    }
}

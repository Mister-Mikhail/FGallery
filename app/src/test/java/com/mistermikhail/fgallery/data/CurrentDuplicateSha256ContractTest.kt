package com.mistermikhail.fgallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.IOException
import java.security.MessageDigest

/**
 * Contract exercised by the Android URI adapter: digest all bytes and fail
 * closed on a provider stream whose size differs from the catalogue row.
 */
class CurrentDuplicateSha256ContractTest {
    private val bytes = "original media bytes".toByteArray()

    @Test fun streamingDigestMatchesSha256OfOriginalBytes() {
        val expected = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals(expected, DuplicateFingerprints.full(
            ByteArrayInputStream(bytes), expectedBytes = bytes.size.toLong()))
    }

    @Test fun truncatedProviderStreamCannotAuthorizeTrash() {
        assertThrows(EOFException::class.java) {
            DuplicateFingerprints.full(ByteArrayInputStream(bytes),
                expectedBytes = bytes.size.toLong() + 1)
        }
    }

    @Test fun grownProviderStreamCannotAuthorizeTrash() {
        assertThrows(IOException::class.java) {
            DuplicateFingerprints.full(ByteArrayInputStream(bytes),
                expectedBytes = bytes.size.toLong() - 1)
        }
    }

    @Test fun cancelledReadCannotAuthorizeTrash() {
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            DuplicateFingerprints.full(ByteArrayInputStream(bytes),
                checkActive = { throw java.util.concurrent.CancellationException("cancelled") },
                expectedBytes = bytes.size.toLong())
        }
    }
}

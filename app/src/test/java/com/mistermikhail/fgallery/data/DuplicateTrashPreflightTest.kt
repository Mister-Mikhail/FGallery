package com.mistermikhail.fgallery.data

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateTrashPreflightTest {
    @Test fun refusesEntireBatchIfLaterItemFails() = runBlocking {
        val checked = mutableListOf<String>()
        val allowed = preflightDuplicateTrash(listOf("copyA", "copyB", "copyC"), { item ->
            checked += item
            item != "copyB"
        }, sourceIdentity = { it })
        assertFalse(allowed)
        assertEquals(listOf("copyA", "copyB"), checked)
    }

    @Test fun rejectsEmptyBatchAndAllowsFullyVerifiedBatch() = runBlocking {
        assertFalse(preflightDuplicateTrash(emptyList<String>(), { true }, sourceIdentity = { it }))
        assertTrue(preflightDuplicateTrash(listOf("a", "b"), { true }, sourceIdentity = { it }))
    }

    @Test fun duplicateSourceRejectedBeforeVerifierRuns() = runBlocking {
        var checks = 0
        assertFalse(preflightDuplicateTrash(listOf("same", "same"), {
            checks++
            true
        }, sourceIdentity = { it }))
        assertEquals(0, checks)
    }

    @Test fun blankSourceIdentityRejected() = runBlocking {
        assertFalse(preflightDuplicateTrash(listOf("a"), { true }, sourceIdentity = { "" }))
    }

    @Test fun distinctSourceIdentitiesAllowVerification() = runBlocking {
        assertTrue(preflightDuplicateTrash(listOf("uri-a", "uri-b"), { true }, sourceIdentity = { it }))
    }


    @Test fun differentUrisAliasingSamePhysicalFileAreRejectedBeforeReads() = runBlocking {
        var reads = 0
        val allowed = preflightDuplicateTrash(
            listOf("content://a/1", "content://b/2"),
            { reads++; true },
            { it },
            { "/storage/emulated/0/DCIM/original.jpg" },
        )
        assertFalse(allowed)
        assertEquals(0, reads)
    }

    @Test fun missingPhysicalPathsAreNotMistakenForAliases() = runBlocking {
        val allowed = preflightDuplicateTrash(
            listOf("content://saf/a", "content://saf/b"),
            { true },
            { it },
            { null },
        )
        assertTrue(allowed)
    }

    @Test fun distinctKnownPhysicalPathsAllowVerification() = runBlocking {
        val allowed = preflightDuplicateTrash(
            listOf("a", "b"),
            { true },
            { it },
            { "/storage/a/${it}" },
        )
        assertTrue(allowed)
    }

    @Test fun lexicalPhysicalAliasesAreRejectedBeforeAnyRead() = runBlocking {
        var reads = 0
        val allowed = preflightDuplicateTrash(
            listOf("content://provider/one", "content://provider/two"),
            { reads++; true },
            { it },
            { uri ->
                if (uri.endsWith("one")) "/storage/emulated/0/DCIM/./photo.jpg"
                else "/storage/emulated/0/Pictures/../DCIM/photo.jpg"
            },
        )
        assertFalse(allowed)
        assertEquals(0, reads)
    }

    @Test fun blankKnownPhysicalPathFailsClosed() = runBlocking {
        var reads = 0
        assertFalse(preflightDuplicateTrash(
            listOf("content://provider/one"),
            { reads++; true },
            { it },
            { "" },
        ))
        assertEquals(0, reads)
    }

    @Test fun cancellationAfterVerifierCannotAuthorizeBatch() = runBlocking {
        val worker = async {
            preflightDuplicateTrash(listOf("copyA", "copyB"), { item ->
                if (item == "copyA") {
                    // Simulate a provider returning after the scan was cancelled.
                    kotlinx.coroutines.currentCoroutineContext().cancel()
                }
                true
            }, sourceIdentity = { it })
        }
        var cancelled = false
        try {
            worker.await()
        } catch (_: java.util.concurrent.CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

    @Test fun verifierExceptionNeverAuthorizesBatch() = runBlocking {
        val checked = mutableListOf<String>()
        val allowed = preflightDuplicateTrash(listOf("a", "b", "c"), {
            checked += it
            if (it == "b") throw java.io.IOException("SAF access revoked")
            true
        }, sourceIdentity = { it })
        assertFalse(allowed)
        assertEquals(listOf("a", "b"), checked)
    }

    @Test fun verifierCancellationPropagatesWithoutCheckingNextSource() = runBlocking {
        val checked = mutableListOf<String>()
        var cancelled = false
        try {
            preflightDuplicateTrash(listOf("a", "b"), {
                checked += it
                throw java.util.concurrent.CancellationException("cancelled")
            }, sourceIdentity = { it })
        } catch (_: java.util.concurrent.CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertEquals(listOf("a"), checked)
    }

    @Test fun verifierInterruptionPropagatesAndPreservesFlag() = runBlocking {
        var interrupted = false
        try {
            preflightDuplicateTrash(listOf("a"), {
                throw InterruptedException("provider interrupted")
            }, sourceIdentity = { it })
        } catch (_: InterruptedException) {
            interrupted = Thread.currentThread().isInterrupted
        } finally {
            Thread.interrupted()
        }
        assertTrue(interrupted)
    }
}

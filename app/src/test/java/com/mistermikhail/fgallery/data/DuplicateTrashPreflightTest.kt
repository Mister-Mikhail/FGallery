package com.mistermikhail.fgallery.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateTrashPreflightTest {
    @Test fun refusesEntireBatchIfLaterItemFails() = runBlocking {
        val checked = mutableListOf<String>()
        val allowed = preflightDuplicateTrash(listOf("copyA", "copyB", "copyC")) { item ->
            checked += item
            item != "copyB"
        } { it }
        assertFalse(allowed)
        assertEquals(listOf("copyA", "copyB"), checked)
    }

    @Test fun rejectsEmptyBatchAndAllowsFullyVerifiedBatch() = runBlocking {
        assertFalse(preflightDuplicateTrash(emptyList<String>(), { true }) { it })
        assertTrue(preflightDuplicateTrash(listOf("a", "b"), { true }) { it })
    }

    @Test fun duplicateSourceRejectedBeforeVerifierRuns() = runBlocking {
        var checks = 0
        assertFalse(preflightDuplicateTrash(listOf("same", "same"), {
            checks++
            true
        }) { it })
        assertEquals(0, checks)
    }

    @Test fun blankSourceIdentityRejected() = runBlocking {
        assertFalse(preflightDuplicateTrash(listOf("a"), { true }) { "" })
    }

    @Test fun distinctSourceIdentitiesAllowVerification() = runBlocking {
        assertTrue(preflightDuplicateTrash(listOf("uri-a", "uri-b"), { true }) { it })
    }

    @Test fun verifierExceptionNeverAuthorizesBatch() = runBlocking {
        var reached = false
        try {
            preflightDuplicateTrash(listOf("a", "b"), {
                if (it == "b") error("SAF access revoked")
                true
            }) { it }
        } catch (_: IllegalStateException) {
            reached = true
        }
        assertTrue(reached)
    }
}

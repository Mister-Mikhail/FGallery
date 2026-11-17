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
        }
        assertFalse(allowed)
        assertEquals(listOf("copyA", "copyB"), checked)
    }

    @Test fun rejectsEmptyBatchAndAllowsFullyVerifiedBatch() = runBlocking {
        assertFalse(preflightDuplicateTrash(emptyList<String>()) { true })
        assertTrue(preflightDuplicateTrash(listOf("a", "b")) { true })
    }

    @Test fun verifierExceptionNeverAuthorizesBatch() = runBlocking {
        var reached = false
        try {
            preflightDuplicateTrash(listOf("a", "b")) {
                if (it == "b") error("SAF access revoked")
                true
            }
        } catch (_: IllegalStateException) {
            reached = true
        }
        assertTrue(reached)
    }
}

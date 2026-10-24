package com.mistermikhail.fgallery.data

import org.junit.Assert.*
import org.junit.Test

class MediaStoreWriteBatchTest {
    @Test fun permissionFlowWith2501ItemsOnlyRequestsRemainingItems() {
        val originals = (1..2501).toList()
        val initial = MediaStoreWriteBatch.next(originals)
        assertEquals(2000, initial.size)
        assertEquals((1..2000).toList(), initial)
        // Model 2000 granted successful moves, leaving only the failed subset.
        val stillPresent = originals.filterNot(initial.toHashSet()::contains)
        val next = MediaStoreWriteBatch.next(stillPresent)
        assertEquals((2001..2501).toList(), next)
        assertTrue(next.intersect(initial.toSet()).isEmpty())
        assertEquals(2501, (initial + next).toSet().size)
    }

    @Test fun rejectedPermissionDoesNotEraseSelectionOrChangeOriginals() {
        val originals = listOf("a", "b", "c")
        assertEquals(originals, MediaStoreWriteBatch.next(originals))
        assertEquals(originals, originals.toList())
        assertTrue(MediaStoreWriteBatch.next(emptyList<String>()).isEmpty())
    }
}

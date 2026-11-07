package com.mistermikhail.fgallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreDeltaTest {
    @Test fun unchangedGenerationWithNewMediaIdForcesFullVolumeReconciliation() {
        assertTrue(hasNewMediaStoreIds(setOf(11L, 12L, 13L), setOf(11L, 12L)))
    }

    @Test fun unchangedGenerationWithDeletedOrTrashedIdNeedsOnlyCheapFiltering() {
        assertFalse(hasNewMediaStoreIds(setOf(11L), setOf(11L, 12L)))
    }

    @Test fun unchangedGenerationWithIdenticalIdsAvoidsFullMetadataQuery() {
        assertFalse(hasNewMediaStoreIds(setOf(11L, 12L), setOf(11L, 12L)))
        assertFalse(hasNewMediaStoreIds(emptySet(), emptySet()))
    }
    @Test fun missingMediaStoreVolumeInventoryCannotEraseCommittedRoomRows() {
        assertThrows(IllegalStateException::class.java) {
            checkedMediaStoreVolumes(emptySet())
        }
    }

    @Test fun mountedVolumeInventoryRemainsUsableWhenItHasNoFiles() {
        assertEquals(setOf("external_primary"),
            checkedMediaStoreVolumes(setOf("external_primary")))
    }

    private data class Entry(val id: Long, val uri: String, val name: String)

    @Test fun changedItemDeletedDuringDeltaQueryMustNotBeResurrected() {
        val previous = listOf(Entry(1, "uri/1", "old"), Entry(2, "uri/2", "old"))
        val changed = listOf(Entry(1, "uri/1", "new"), Entry(3, "uri/3", "deleted"))
        val result = reconcileMediaStoreDelta(previous, changed, setOf(1L),
            id = { it.id }, key = { it.uri })
        assertEquals(listOf(Entry(1, "uri/1", "new")), result)
    }

    @Test fun deltaMetadataOverridesOldAndRetainsSurvivingUnchangedItems() {
        val previous = listOf(Entry(1, "uri/1", "old"), Entry(2, "uri/2", "untouched"))
        val changed = listOf(Entry(1, "uri/1", "updated"), Entry(3, "uri/3", "added"))
        val result = reconcileMediaStoreDelta(previous, changed, setOf(1L, 2L, 3L),
            id = { it.id }, key = { it.uri })
        assertEquals(listOf(
            Entry(1, "uri/1", "updated"),
            Entry(2, "uri/2", "untouched"),
            Entry(3, "uri/3", "added"),
        ), result)
    }

    @Test fun trashedAndDeletedIdsDisappearEvenIfDeltaIsEmpty() {
        val previous = listOf(Entry(1, "uri/1", "present"), Entry(2, "uri/2", "trashed"))
        assertEquals(listOf(Entry(1, "uri/1", "present")),
            reconcileMediaStoreDelta(previous, emptyList(), setOf(1L),
                id = { it.id }, key = { it.uri }))
    }

}

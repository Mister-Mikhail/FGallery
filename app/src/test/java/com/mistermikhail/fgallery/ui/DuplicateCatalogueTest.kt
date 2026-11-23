package com.mistermikhail.fgallery.ui

import com.mistermikhail.fgallery.data.CatalogMediaRow
import com.mistermikhail.fgallery.data.DuplicateSearchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateCatalogueTest {
    @Test fun snapshotCountsOnlyTheExactGroupsAndIndexesOnlySpareCopies() {
        val catalogue = DuplicateCatalogue(DuplicateSearchInput(
            rows = listOf(row("original"), row("copy"), row("other", hash = "b".repeat(64))),
            disconnectedStorages = listOf("USB"),
            pendingFingerprints = 2,
        ))
        assertTrue(catalogue.valid)
        assertEquals(setOf("original"), catalogue.review.protectedUris)
        assertEquals(setOf("copy"), catalogue.selectableUris)
        assertEquals(2, catalogue.duplicateFileCount)
        assertEquals(1024L, catalogue.reclaimableBytes)
        assertEquals(3, catalogue.visualRows.size)
        assertEquals(listOf("USB"), catalogue.disconnectedStorages)
        assertEquals(2, catalogue.pendingFingerprints)
    }

    @Test fun ambiguousCatalogueCannotAppearAsAValidEmptySearchOrEnableSelection() {
        val catalogue = DuplicateCatalogue(DuplicateSearchInput(listOf(
            row("original"), row("alias").copy(sourcePath = "/fixtures/original.jpg"),
        )))
        assertFalse(catalogue.valid)
        assertTrue(catalogue.selectableUris.isEmpty())
        assertTrue(catalogue.cards.isEmpty())
    }

    @Test fun totalPotentialReclaimableSpaceCannotOverflow() {
        val catalogue = DuplicateCatalogue(DuplicateSearchInput(
            listOf(row("original"), row("one"), row("two")).map { it.copy(sizeBytes = Long.MAX_VALUE) },
        ))
        assertTrue(catalogue.valid)
        assertEquals(3, catalogue.duplicateFileCount)
        assertNull(catalogue.reclaimableBytes)
    }

    private fun row(id: String, hash: String = "a".repeat(64)) = CatalogMediaRow(
        uri = id, mediaId = 0, name = "$id.jpg", mimeType = "image/jpeg", kind = "IMAGE",
        dateTakenMillis = if (id == "original") 1 else 2, width = 200, height = 200,
        durationMillis = 0, album = "Pictures", relativePath = "Pictures", sizeBytes = 1024,
        dateModifiedMillis = 1, storageId = "external_primary", storageName = "Телефон",
        folderTarget = "/fixtures", sourcePath = "/fixtures/$id.jpg", fullHash = hash,
    )
}

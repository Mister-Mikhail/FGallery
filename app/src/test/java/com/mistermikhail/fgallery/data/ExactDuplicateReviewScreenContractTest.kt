package com.mistermikhail.fgallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contract for the Stage 6 Compose review: scope changes must discard consent. */
class ExactDuplicateReviewScreenContractTest {
    private fun row(uri: String, storage: String, folder: String) = CatalogMediaRow(
        uri = uri, mediaId = 1, name = "$uri.jpg", mimeType = "image/jpeg",
        kind = "IMAGE", dateTakenMillis = if (uri == "original") 1 else 2, width = 200, height = 200,
        durationMillis = 0, album = folder, relativePath = folder,
        sizeBytes = 100, dateModifiedMillis = 1, storageId = storage,
        storageName = storage, folderTarget = folder, sourcePath = "/test/$uri.jpg",
        quickHash = "edge", fullHash = "full-digest", recycled = false,
    )

    @Test fun changingStorageScopeCannotCarrySelectionOrConfirmation() {
        val rows = listOf(row("original", "phone", "Camera"), row("copy", "phone", "Camera"),
            row("usb", "usb", "Camera"))
        val initial = planExactDuplicateReview(rows, requestedUris = setOf("copy"))
        assertEquals(1, initial.selectedCount)
        val scoped = planExactDuplicateReview(rows, includedStorageIds = setOf("phone"))
        assertTrue(scoped.selectedUris.isEmpty())
        assertNull(exactDuplicateTrashPrompt(scoped))
        assertEquals(2, exactDuplicateReviewCards(scoped).single().items.size)
    }

    @Test fun scopeExcludingOneSideCannotPresentADeletableCopy() {
        val rows = listOf(row("original", "phone", "Camera"), row("copy", "usb", "Downloads"))
        val review = planExactDuplicateReview(rows, includedFolderTargets = setOf("Camera"),
            requestedUris = setOf("copy"))
        assertTrue(exactDuplicateReviewCards(review).isEmpty())
        assertTrue(review.selectedUris.isEmpty())
        assertNull(exactDuplicateTrashPrompt(review))
    }

    @Test fun protectedOriginalRemainsUnselectableAndCopiesRequireManualOptIn() {
        val rows = listOf(row("original", "phone", "Camera"), row("copy", "phone", "Camera"))
        val review = planExactDuplicateReview(rows)
        assertFalse(exactDuplicateReviewCards(review).single().items[0].manuallySelected)
        val attempted = selectExactDuplicateCopy(review, rows, "original", true)
        assertTrue(attempted.selectedUris.isEmpty())
        val chosen = selectExactDuplicateCopy(attempted, rows, "copy", true)
        assertEquals(setOf("copy"), chosen.selectedUris)
        assertTrue(exactDuplicateTrashPrompt(chosen)!!.message.contains("SHA-256"))
    }

    @Test fun disconnectedSafScopeBlocksTrashUntilSourceReturns() {
        val rows = listOf(row("original", "usb", "Camera"), row("copy", "usb", "Camera"))
        val review = planExactDuplicateReview(rows, includedStorageIds = setOf("usb"),
            includedFolderTargets = setOf("Camera"), requestedUris = setOf("copy"))
        assertEquals(1, review.selectedCount)
        assertTrue(duplicateReviewScopeAvailable(review, listOf("usb"), listOf("Camera")))
        assertFalse(duplicateReviewScopeAvailable(review, listOf("phone"), listOf("Camera")))
        assertFalse(duplicateReviewScopeAvailable(review, listOf("usb"), emptyList()))
    }
}

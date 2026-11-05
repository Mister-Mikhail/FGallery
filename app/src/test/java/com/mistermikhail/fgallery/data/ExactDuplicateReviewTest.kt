package com.mistermikhail.fgallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactDuplicateReviewTest {
    private fun item(
        uri: String, full: String = "same", storage: String = "phone",
        folder: String = "DCIM/", modified: Long = 100L,
    ) = CatalogMediaRow(
        uri = uri, mediaId = 1, name = "photo.jpg", mimeType = "image/jpeg",
        kind = "IMAGE", dateTakenMillis = 123, width = 100, height = 100,
        durationMillis = 0, album = "Camera", relativePath = folder,
        sizeBytes = 200, dateModifiedMillis = modified,
        storageId = storage, storageName = storage, folderTarget = folder,
        sourcePath = "/DCIM/photo.jpg", quickHash = "edge", fullHash = full,
        recycled = false,
    )

    @Test fun noImplicitSelectionAndOriginalIsProtected() {
        val a = item("a")
        val b = item("b")
        val review = planExactDuplicateReview(listOf(a, b), requestedUris = setOf("a", "b"))
        assertEquals(setOf("b"), review.selectedUris)
        assertEquals(setOf("a"), review.protectedUris)
        assertTrue(planExactDuplicateReview(listOf(a, b)).selectedUris.isEmpty())
    }

    @Test fun scopeCannotTrashUnselectedStorageOrFolder() {
        val rows = listOf(item("a"), item("b"), item("c", storage = "usb"),
            item("d", folder = "Pictures/"))
        val review = planExactDuplicateReview(rows,
            includedStorageIds = setOf("phone"), includedFolderTargets = setOf("DCIM/"),
            requestedUris = setOf("b", "c", "d"))
        assertEquals(setOf("b"), review.selectedUris)
    }

    @Test fun rawJpegAndEditedVersionsAreNotExactDuplicates() {
        val rows = listOf(item("raw", "raw-bytes"), item("jpeg", "jpeg-bytes"),
            item("edited", "retouched"))
        assertTrue(planExactDuplicateReview(rows, requestedUris = rows.map { it.uri }.toSet())
            .groups.isEmpty())
    }

    @Test fun confirmationFailsClosedOnChangeOrMissingConsent() {
        val rows = listOf(item("a"), item("b"))
        val review = planExactDuplicateReview(rows, requestedUris = setOf("b"))
        assertTrue(confirmedExactDuplicateUris(review, rows, explicitlyConfirmed = false).isEmpty())
        assertEquals(setOf("b"), confirmedExactDuplicateUris(review, rows,
            explicitlyConfirmed = true))
        assertTrue(confirmedExactDuplicateUris(review,
            listOf(item("a"), item("b", modified = 101L)), explicitlyConfirmed = true).isEmpty())
        assertTrue(confirmedExactDuplicateUris(review,
            listOf(item("a")), explicitlyConfirmed = true).isEmpty())
    }
    @Test fun confirmationRejectsMovedFileEvenWithUnchangedHashesAndMtime() {
        val rows = listOf(item("a"), item("b"))
        val review = planExactDuplicateReview(rows, requestedUris = setOf("b"))
        assertTrue(confirmedExactDuplicateUris(review,
            listOf(item("a"), item("b", storage = "usb")),
            explicitlyConfirmed = true).isEmpty())
        assertTrue(confirmedExactDuplicateUris(review,
            listOf(item("a"), item("b", folder = "Pictures/")),
            explicitlyConfirmed = true).isEmpty())
    }

    @Test fun confirmationRequiresTheSameReviewedScope() {
        val rows = listOf(item("a"), item("b"))
        val storageScope = setOf("phone")
        val folderScope = setOf("DCIM/")
        val review = planExactDuplicateReview(rows, storageScope, folderScope,
            requestedUris = setOf("b"))
        assertEquals(setOf("b"), confirmedExactDuplicateUris(review, rows,
            storageScope, folderScope, explicitlyConfirmed = true))
        assertTrue(confirmedExactDuplicateUris(review, rows,
            explicitlyConfirmed = true).isEmpty())
        assertTrue(confirmedExactDuplicateUris(review, rows,
            storageScope, setOf("Pictures/"), explicitlyConfirmed = true).isEmpty())
        assertTrue(confirmedExactDuplicateUris(review, rows,
            setOf("phone", "usb"), folderScope, explicitlyConfirmed = true).isEmpty())
    }

    @Test fun confirmationRejectsChangedOriginalMetadataEvenWhenHashesMatch() {
        val rows = listOf(item("a"), item("b"))
        val review = planExactDuplicateReview(rows, requestedUris = setOf("b"))
        assertEquals(setOf("b"), confirmedExactDuplicateUris(review, rows,
            explicitlyConfirmed = true))
        assertTrue(confirmedExactDuplicateUris(review,
            listOf(rows[0].copy(width = 200), rows[1]),
            explicitlyConfirmed = true).isEmpty())
        assertTrue(confirmedExactDuplicateUris(review,
            listOf(rows[0], rows[1].copy(name = "renamed.jpg")),
            explicitlyConfirmed = true).isEmpty())
        assertTrue(confirmedExactDuplicateUris(review,
            listOf(rows[0].copy(sourcePath = "/Pictures/photo.jpg"), rows[1]),
            explicitlyConfirmed = true).isEmpty())
    }

    @Test fun confirmationRejectsConflictingCatalogueRowsForSameUri() {
        val rows = listOf(item("a"), item("b"))
        val review = planExactDuplicateReview(rows, requestedUris = setOf("b"))
        assertEquals(setOf("b"), confirmedExactDuplicateUris(review, rows,
            explicitlyConfirmed = true))
        // During SAF/MediaStore reconciliation a stale row may coexist with
        // the updated row. Never silently trust distinctBy(uri) here.
        val conflicting = rows + rows[1].copy(dateModifiedMillis = 999L)
        assertTrue(confirmedExactDuplicateUris(review, conflicting,
            explicitlyConfirmed = true).isEmpty())
    }

}

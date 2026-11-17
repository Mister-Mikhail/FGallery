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
        kind = "IMAGE", dateTakenMillis = if (uri == "original") 1 else 123, width = 100, height = 100,
        durationMillis = 0, album = "Camera", relativePath = folder,
        sizeBytes = 200, dateModifiedMillis = modified,
        storageId = storage, storageName = storage, folderTarget = folder,
        sourcePath = "/DCIM/$uri.jpg", quickHash = "edge", fullHash = full,
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

    @Test fun reviewDoesNotOfferActionsForAmbiguousUriGenerations() {
        val rows = listOf(item("a"), item("b"))
        val ambiguous = rows + rows[1].copy(dateModifiedMillis = 999L)
        val review = planExactDuplicateReview(ambiguous, requestedUris = setOf("b"))
        assertTrue(review.groups.isEmpty())
        assertTrue(review.selectedUris.isEmpty())
        assertTrue(review.protectedUris.isEmpty())
        // Once reconciliation completes, the ordinary review is available.
        assertEquals(setOf("b"), planExactDuplicateReview(rows,
            requestedUris = setOf("b")).selectedUris)
    }

    @Test fun confirmationRejectsRecycledOrRehashedCandidates() {
        val rows = listOf(item("a"), item("b"))
        val review = planExactDuplicateReview(rows, requestedUris = setOf("b"))
        assertEquals(setOf("b"), confirmedExactDuplicateUris(review, rows,
            explicitlyConfirmed = true))
        // A file that changed on disk or entered the trash cannot be acted
        // upon using the earlier review, even if the URI is unchanged.
        assertTrue(confirmedExactDuplicateUris(review,
            listOf(rows[0], rows[1].copy(fullHash = "new-content")),
            explicitlyConfirmed = true).isEmpty())
        assertTrue(confirmedExactDuplicateUris(review,
            listOf(rows[0], rows[1].copy(quickHash = "new-edge")),
            explicitlyConfirmed = true).isEmpty())
        assertTrue(confirmedExactDuplicateUris(review,
            listOf(rows[0], rows[1].copy(recycled = true)),
            explicitlyConfirmed = true).isEmpty())
    }

    @Test fun reviewCannotSelectAnUnrelatedFileOrTheProtectedOriginal() {
        val rows = listOf(item("a"), item("b"), item("unrelated", full = "different"))
        val review = planExactDuplicateReview(rows,
            requestedUris = setOf("a", "b", "unrelated", "missing"))
        assertEquals(setOf("b"), review.selectedUris)
        assertEquals(setOf("a"), review.protectedUris)
        assertEquals(setOf("b"), confirmedExactDuplicateUris(review, rows,
            explicitlyConfirmed = true))
    }

    @Test fun confirmationRejectsChangedCatalogueIdentityOutsideHashFields() {
        val rows = listOf(item("a"), item("b"))
        val review = planExactDuplicateReview(rows, requestedUris = setOf("b"))
        assertEquals(setOf("b"), confirmedExactDuplicateUris(review, rows,
            explicitlyConfirmed = true))
        val changes = listOf(
            rows[1].copy(mediaId = 99),
            rows[1].copy(storageName = "renamed volume"),
            rows[1].copy(album = "Other album"),
            rows[1].copy(relativePath = "Pictures/"),
            rows[1].copy(durationMillis = 1000L),
            rows[1].copy(exifCamera = "Canon EOS R5"),
        )
        changes.forEach { changed ->
            assertTrue(confirmedExactDuplicateUris(review, listOf(rows[0], changed),
                explicitlyConfirmed = true).isEmpty())
        }
    }

    @Test fun freshByteGateReadsCurrentCatalogueRowsNotReviewSnapshot() {
        val snapshot = listOf(item("original"), item("copy"))
        val review = planExactDuplicateReview(snapshot, requestedUris = setOf("copy"))
        // Structurally equal rows from a new scan must be used for the final
        // read, rather than the stale object instances held by the UI.
        val rescanned = snapshot.map { it.copy() }
        val visited = mutableListOf<CatalogMediaRow>()
        val authorized = verifiedExactDuplicateUris(review, rescanned,
            explicitlyConfirmed = true) { row ->
            visited += row
            row.fullHash
        }
        assertEquals(setOf("copy"), authorized)
        assertEquals(2, visited.size)
        visited.forEach { row ->
            assertTrue(rescanned.any { it === row })
            assertTrue(snapshot.none { it === row })
        }
    }

    @Test fun freshByteGateChecksOriginalAndSelectedCopies() {
        val rows = listOf(item("original"), item("copy"))
        val review = planExactDuplicateReview(rows, requestedUris = setOf("copy"))
        val checked = mutableListOf<String>()
        val approved = verifiedExactDuplicateUris(review, rows, explicitlyConfirmed = true) {
            checked += it.uri
            it.fullHash
        }
        assertEquals(setOf("copy"), approved)
        assertEquals(setOf("original", "copy"), checked.toSet())
        assertTrue(verifiedExactDuplicateUris(review, rows, explicitlyConfirmed = false) {
            it.fullHash
        }.isEmpty())
    }

    @Test fun freshByteGateFailsClosedWhenOriginalOrCopyChangesOrIsUnreadable() {
        val rows = listOf(item("original"), item("copy"))
        val review = planExactDuplicateReview(rows, requestedUris = setOf("copy"))
        for (badUri in listOf("original", "copy")) {
            assertTrue(verifiedExactDuplicateUris(review, rows, explicitlyConfirmed = true) {
                if (it.uri == badUri) "changed" else it.fullHash
            }.isEmpty())
            assertTrue(verifiedExactDuplicateUris(review, rows, explicitlyConfirmed = true) {
                if (it.uri == badUri) null else it.fullHash
            }.isEmpty())
            assertTrue(verifiedExactDuplicateUris(review, rows, explicitlyConfirmed = true) {
                if (it.uri == badUri) throw java.io.IOException("SAF disconnected")
                it.fullHash
            }.isEmpty())
        }
    }

    @Test fun freshByteGatePropagatesCancellationInsteadOfContinuingVerification() {
        val rows = listOf(item("original"), item("copy"))
        val review = planExactDuplicateReview(rows, requestedUris = setOf("copy"))
        var reads = 0
        try {
            verifiedExactDuplicateUris(review, rows, explicitlyConfirmed = true) {
                reads++
                throw java.util.concurrent.CancellationException("scan superseded")
            }
            org.junit.Assert.fail("Cancellation must propagate to the caller")
        } catch (_: java.util.concurrent.CancellationException) {
            assertEquals(1, reads)
        }
    }

    @Test fun freshByteGateRejectsStaleCatalogueBeforeReadingAnyFile() {
        val rows = listOf(item("original"), item("copy"))
        val review = planExactDuplicateReview(rows, requestedUris = setOf("copy"))
        var reads = 0
        val authorized = verifiedExactDuplicateUris(
            review, listOf(rows[0], rows[1].copy(dateModifiedMillis = 200L)),
            explicitlyConfirmed = true,
        ) {
            reads++
            it.fullHash
        }
        assertTrue(authorized.isEmpty())
        assertEquals(0, reads)
    }

    @Test fun groupSummaryIsReadOnlyAndCountsOnlyManuallySelectedCopies() {
        val rows = listOf(item("original"), item("copy1"), item("copy2"))
        val review = planExactDuplicateReview(rows, requestedUris = setOf("copy2"))
        val summary = exactDuplicateGroupSummaries(review).single()
        assertEquals("original", summary.originalUri)
        assertEquals(listOf("copy1", "copy2"), summary.copyUris)
        assertEquals(200L, summary.sizeBytes)
        assertEquals(200L, summary.reclaimableBytes)
        assertEquals("same", summary.sha256)
        assertEquals(setOf("copy2"), review.selectedUris)
        assertEquals(0L, exactDuplicateGroupSummaries(
            planExactDuplicateReview(rows)
        ).single().reclaimableBytes)
    }

    @Test fun groupSummaryRespectsFolderAndStorageScopeAndRejectsAmbiguity() {
        val rows = listOf(item("original"), item("copy"), item("usb", storage = "usb"))
        val scoped = planExactDuplicateReview(rows,
            includedStorageIds = setOf("phone"), requestedUris = setOf("copy", "usb"))
        assertEquals(listOf("copy"), exactDuplicateGroupSummaries(scoped).single().copyUris)
        assertTrue(exactDuplicateGroupSummaries(
            planExactDuplicateReview(rows + rows[1].copy(dateModifiedMillis = 999L))
        ).isEmpty())
    }


    @Test fun manualSelectionCannotSelectProtectedOriginalOrOutOfScopeFile() {
        val rows = listOf(item("original"), item("copy"), item("usb", storage = "usb"))
        val initial = planExactDuplicateReview(rows, includedStorageIds = setOf("phone"))
        assertTrue(initial.selectedUris.isEmpty())
        val protected = selectExactDuplicateCopy(initial, rows, "original", true)
        assertTrue(protected.selectedUris.isEmpty())
        val outOfScope = selectExactDuplicateCopy(protected, rows, "usb", true)
        assertTrue(outOfScope.selectedUris.isEmpty())
        val chosen = selectExactDuplicateCopy(outOfScope, rows, "copy", true)
        assertEquals(setOf("copy"), chosen.selectedUris)
        assertEquals(setOf("original"), chosen.protectedUris)
        assertTrue(selectExactDuplicateCopy(chosen, rows, "copy", false).selectedUris.isEmpty())
    }

    @Test fun manualSelectionInvalidatesOnStaleCatalogueAndRequiresNewConsent() {
        val rows = listOf(item("original"), item("copy"))
        val selected = selectExactDuplicateCopy(planExactDuplicateReview(rows), rows, "copy", true)
        val changed = listOf(rows[0], rows[1].copy(dateModifiedMillis = 201L))
        val reset = selectExactDuplicateCopy(selected, changed, "copy", true)
        assertTrue(reset.selectedUris.isEmpty())
        assertTrue(confirmedExactDuplicateUris(selected, changed,
            explicitlyConfirmed = true).isEmpty())
        assertTrue(confirmedExactDuplicateUris(reset, changed,
            explicitlyConfirmed = true).isEmpty())
    }

    @Test fun offScopeDuplicateUriInvalidatesScopedReviewAndConfirmation() {
        val rows = listOf(item("original"), item("copy"))
        val scoped = planExactDuplicateReview(rows,
            includedStorageIds = setOf("phone"), requestedUris = setOf("copy"))
        assertEquals(setOf("copy"), scoped.selectedUris)
        // SAF reconciliation can retain a stale generation on a disconnected USB
        // volume. Filtering before URI uniqueness would conceal this collision.
        val conflicting = rows + item("copy", storage = "usb", modified = 999L)
        val blocked = planExactDuplicateReview(conflicting,
            includedStorageIds = setOf("phone"), requestedUris = setOf("copy"))
        assertTrue(blocked.groups.isEmpty())
        assertTrue(blocked.selectedUris.isEmpty())
        assertTrue(confirmedExactDuplicateUris(scoped, conflicting,
            includedStorageIds = setOf("phone"), explicitlyConfirmed = true).isEmpty())
        var reads = 0
        assertTrue(verifiedExactDuplicateUris(scoped, conflicting,
            includedStorageIds = setOf("phone"), explicitlyConfirmed = true) {
            reads++
            it.fullHash
        }.isEmpty())
        assertEquals(0, reads)
    }


    @Test fun allScopeReviewRejectsDisconnectedStorageOrFolder() {
        val rows = listOf(item("a"), item("b", storage = "usb", folder = "USB/Pictures/"))
        val review = planExactDuplicateReview(rows)
        assertTrue(review.groups.isNotEmpty())
        assertEquals(false, duplicateReviewScopeAvailable(
            review, listOf("phone"), listOf("DCIM/", "USB/Pictures/")))
        assertEquals(false, duplicateReviewScopeAvailable(
            review, listOf("phone", "usb"), listOf("DCIM/")))
        assertEquals(true, duplicateReviewScopeAvailable(
            review, listOf("phone", "usb"), listOf("DCIM/", "USB/Pictures/")))
    }
    @Test fun freshByteGateRejectsInterruptRaisedAfterDigestIsReturned() {
        val rows = listOf(item("original"), item("copy"))
        val review = planExactDuplicateReview(rows, requestedUris = setOf("copy"))
        var reads = 0
        try {
            verifiedExactDuplicateUris(review, rows, explicitlyConfirmed = true) { row ->
                reads++
                // A provider may finish a read while the worker has already
                // been interrupted; returning a valid hash cannot grant consent.
                Thread.currentThread().interrupt()
                row.fullHash
            }
            org.junit.Assert.fail("Interrupted digest must not authorize trash")
        } catch (_: InterruptedException) {
            assertEquals(1, reads)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    @Test fun freshByteGatePropagatesWorkerInterruptionAndPreservesInterruptFlag() {
        val rows = listOf(item("original"), item("copy"))
        val review = planExactDuplicateReview(rows, requestedUris = setOf("copy"))
        var reads = 0
        try {
            verifiedExactDuplicateUris(review, rows, explicitlyConfirmed = true) {
                reads++
                throw InterruptedException("SAF worker interrupted")
            }
            org.junit.Assert.fail("Interrupted verification must not authorize trash")
        } catch (_: InterruptedException) {
            assertEquals(1, reads)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            // Do not leak the interrupt flag into the rest of the JVM suite.
            Thread.interrupted()
        }
    }

}

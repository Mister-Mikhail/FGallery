package com.mistermikhail.fgallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactDuplicateReviewPresentationTest {
    @Test fun selectedDuplicateSizeUsesReadableBinaryUnitsWithoutLosingExactBytes() {
        assertEquals("250 байт", formatDuplicateBytes(250))
        assertEquals("1,5 КиБ", formatDuplicateBytes(1536))
        assertEquals("2,0 МиБ", formatDuplicateBytes(2L * 1024 * 1024))
        assertEquals("1,0 ГиБ", formatDuplicateBytes(1024L * 1024 * 1024))
        val review = planExactDuplicateReview(
            listOf(row("original"), row("copy")).map { it.copy(sizeBytes = 1536) },
            requestedUris = setOf("copy"),
        )
        val prompt = exactDuplicateTrashPrompt(review)!!
        assertEquals(1536L, prompt.selectedBytes)
        assertTrue(prompt.message.contains("1,5 КиБ (1536 байт)"))
        val small = planExactDuplicateReview(
            listOf(row("original"), row("copy")),
            requestedUris = setOf("copy"),
        )
        val smallPrompt = exactDuplicateTrashPrompt(small)!!
        assertTrue(smallPrompt.message.contains("250 байт."))
        assertFalse(smallPrompt.message.contains("250 байт (250 байт)"))
    }

    private fun row(uri: String, hash: String = "a".repeat(64)) = CatalogMediaRow(
        uri = uri, mediaId = 1, name = "$uri.jpg", mimeType = "image/jpeg",
        kind = "IMAGE", dateTakenMillis = if (uri == "original") 1 else 123, width = 100, height = 100,
        durationMillis = 0, album = "Camera", relativePath = "DCIM/",
        sizeBytes = 250, dateModifiedMillis = 100,
        storageId = "phone", storageName = "phone", folderTarget = "DCIM/",
        sourcePath = "/DCIM/$uri.jpg", quickHash = "edge", fullHash = hash,
        recycled = false,
    )

    @Test fun nulInCatalogueUriFailsClosedBeforeTrashReview() {
        val rows = listOf(row("original"), row("copy"), row("bad\u0000uri"))
        val review = planExactDuplicateReview(rows, requestedUris = setOf("copy"))
        assertTrue(review.groups.isEmpty())
        assertTrue(review.selectedUris.isEmpty())
        assertNull(exactDuplicateTrashPrompt(review))
    }

    @Test fun cardsExplainProtectedOriginalAndNeverPreselectCopies() {
        val review = planExactDuplicateReview(listOf(row("original"), row("copy")))
        val card = exactDuplicateReviewCards(review).single()
        assertEquals(2, card.items.size)
        assertTrue(card.items.first().protectedOriginal)
        assertFalse(card.items.first().manuallySelected)
        assertTrue(card.items.first().explanation.contains("нельзя"))
        assertFalse(card.items.last().protectedOriginal)
        assertTrue(card.items.last().explanation.contains("SHA-256"))
        assertEquals(0, card.selectedCopyCount)
        assertEquals(0L, card.selectedBytes)
        assertNull(exactDuplicateTrashPrompt(review))
    }

    @Test fun protectedKeeperExplainsEarliestCaptureWithoutClaimingCameraOriginal() {
        val review = planExactDuplicateReview(listOf(row("copy"), row("original")))
        val protected = exactDuplicateReviewCards(review).single().items.first()
        assertEquals("original", protected.uri)
        assertTrue(protected.explanation.contains("самой ранней датой"))
        assertFalse(protected.manuallySelected)
    }

    @Test fun unknownCopyCaptureDateDoesNotProveKeeperIsEarliest() {
        val rows = listOf(
            row("original").copy(dateTakenMillis = 1L),
            row("copy").copy(dateTakenMillis = 0L),
        )
        val review = planExactDuplicateReview(rows)
        val keeper = exactDuplicateReviewCards(review).single().items.first()
        assertEquals("original", keeper.uri)
        assertTrue(keeper.protectedOriginal)
        assertFalse(keeper.explanation.contains("самой ранней датой"))
        assertTrue(keeper.explanation.contains("исторический оригинал"))
    }

    @Test fun allKnownLaterCaptureDatesPermitEarliestDateExplanation() {
        val rows = listOf(
            row("original").copy(dateTakenMillis = 1L),
            row("copy1").copy(dateTakenMillis = 2L),
            row("copy2").copy(dateTakenMillis = 3L),
        )
        val review = planExactDuplicateReview(rows)
        val keeper = exactDuplicateReviewCards(review).single().items.first()
        assertTrue(keeper.explanation.contains("самой ранней датой"))
        assertEquals(0, review.selectedCount)
    }

    @Test fun keeperWithEqualCaptureDatesExplainsDeterministicFallback() {
        val rows = listOf(row("a"), row("b")).map { it.copy(dateTakenMillis = 100L) }
        val review = planExactDuplicateReview(rows)
        val protected = exactDuplicateReviewCards(review).single().items.first()
        assertEquals("a", protected.uri)
        assertTrue(protected.explanation.contains("исторический оригинал"))
        assertFalse(protected.manuallySelected)
    }

    @Test fun confirmationPromptOnlyCountsManuallySelectedCopies() {
        val rows = listOf(row("original"), row("copy1"), row("copy2"))
        val review = planExactDuplicateReview(rows, requestedUris = setOf("copy2"))
        val card = exactDuplicateReviewCards(review).single()
        assertEquals(1, card.selectedCopyCount)
        assertEquals(250L, card.selectedBytes)
        assertFalse(card.items[1].manuallySelected)
        assertTrue(card.items[2].manuallySelected)
        val prompt = exactDuplicateTrashPrompt(review)!!
        assertEquals(1, prompt.selectedCopyCount)
        assertEquals(250L, prompt.selectedBytes)
        assertTrue(prompt.message.contains("проверены повторно"))
        assertTrue(prompt.message.contains("250 байт"))
        assertTrue(prompt.message.contains("не освобождает место"))
        assertTrue(prompt.message.contains("Оригиналы останутся"))
    }

    @Test fun overflowedSelectedBytesCannotAuthorizeTrashPrompt() {
        val rows = listOf(row("original"), row("copy1"), row("copy2"))
            .map { it.copy(sizeBytes = Long.MAX_VALUE) }
        val review = planExactDuplicateReview(rows, requestedUris = setOf("copy1", "copy2"))
        assertEquals(2, review.selectedCount)
        // The card itself must not display a negative/wrapped byte estimate,
        // even before the separate trash confirmation is requested.
        assertTrue(exactDuplicateReviewCards(review).isEmpty())
        assertNull(exactDuplicateTrashPrompt(review))
    }

    @Test fun handcraftedMalformedDigestCannotRenderActionableCardOrPrompt() {
        val rows = listOf(row("original"), row("copy"))
        val safe = planExactDuplicateReview(rows, requestedUris = setOf("copy"))
        assertEquals(1, safe.selectedCount)
        // A UI state restored from an old snapshot must not bypass the
        // planner's strict full-SHA-256 contract.
        val malformed = safe.copy(groups = safe.groups.map { group ->
            group.map { it.copy(fullHash = "a".repeat(63) + "z") }
        })
        assertTrue(exactDuplicateReviewCards(malformed).isEmpty())
        assertNull(exactDuplicateTrashPrompt(malformed))
    }

    @Test fun malformedUnselectedGroupHidesEntireReviewInsteadOfPartialCards() {
        val first = planExactDuplicateReview(
            listOf(row("original"), row("copy")),
            requestedUris = setOf("copy"),
        )
        val second = planExactDuplicateReview(
            listOf(row("other", "b".repeat(64)), row("otherCopy", "b".repeat(64))),
        )
        val corrupt = first.copy(groups = first.groups + second.groups.map { group ->
            group.map { it.copy(fullHash = "not-a-sha256") }
        })
        assertEquals(1, exactDuplicateReviewCards(first).size)
        assertTrue(exactDuplicateReviewCards(corrupt).isEmpty())
        assertNull(exactDuplicateTrashPrompt(corrupt))
    }

    @Test fun crossGroupUriAliasHidesAllReviewCardsBeforeSelection() {
        val first = planExactDuplicateReview(
            listOf(row("original"), row("copy")),
            requestedUris = setOf("copy"),
        )
        val second = planExactDuplicateReview(
            listOf(row("other", "b".repeat(64)), row("copy", "b".repeat(64))),
        )
        val corrupt = first.copy(groups = first.groups + second.groups)
        // Each group is individually well formed; the duplicate URI across
        // groups makes the entire review ambiguous, not merely the trash prompt.
        assertTrue(exactDuplicateReviewCards(corrupt).isEmpty())
        assertNull(exactDuplicateTrashPrompt(corrupt))
        assertEquals(1, exactDuplicateReviewCards(first).size)
    }

    @Test fun repeatedKeeperUriAcrossDifferentHashGroupsBlocksTrashConsent() {
        val first = planExactDuplicateReview(
            listOf(row("original"), row("copy")),
            requestedUris = setOf("copy"),
        )
        val second = planExactDuplicateReview(
            listOf(row("original", "b".repeat(64)), row("other", "b".repeat(64))),
        )
        // A stale catalogue can combine groups that each look internally valid,
        // but point at the same keeper URI. Never offer consent for this state.
        val corrupt = first.copy(groups = first.groups + second.groups)
        assertEquals(1, corrupt.selectedUris.size)
        assertNull(exactDuplicateTrashPrompt(corrupt))
    }

    @Test fun restoredReviewWithRepeatedUriCannotOfferTwoCopiesForTrash() {
        val rows = listOf(row("original"), row("copy"))
        val safe = planExactDuplicateReview(rows, requestedUris = setOf("copy"))
        val repeated = safe.copy(groups = safe.groups.map { group ->
            group + group.last()
        })
        assertTrue(exactDuplicateReviewCards(repeated).isEmpty())
        assertNull(exactDuplicateTrashPrompt(repeated))
    }

    @Test fun restoredReviewWithBlankOrProtectedCopyUriCannotOfferTrash() {
        val rows = listOf(row("original"), row("copy"))
        val safe = planExactDuplicateReview(rows, requestedUris = setOf("copy"))
        val blank = safe.copy(groups = safe.groups.map { group ->
            group.dropLast(1) + group.last().copy(uri = " ")
        })
        assertTrue(exactDuplicateReviewCards(blank).isEmpty())
        assertNull(exactDuplicateTrashPrompt(blank))
        val protectedAlias = safe.copy(groups = safe.groups.map { group ->
            group.dropLast(1) + group.last().copy(uri = "original")
        })
        assertTrue(exactDuplicateReviewCards(protectedAlias).isEmpty())
        assertNull(exactDuplicateTrashPrompt(protectedAlias))
    }

    @Test fun restoredReviewWithNulUriCannotRenderCardsOrOfferTrash() {
        val safe = planExactDuplicateReview(
            listOf(row("original"), row("copy")),
            requestedUris = setOf("copy"),
        )
        // A restored snapshot can bypass the planner's input validation.
        // Never render an actionable card for a malformed provider URI.
        val malformed = safe.copy(groups = safe.groups.map { group ->
            group.dropLast(1) + group.last().copy(uri = "copy\u0000alias")
        })
        assertTrue(exactDuplicateReviewCards(malformed).isEmpty())
        assertNull(exactDuplicateTrashPrompt(malformed))
    }

    @Test fun handcraftedZeroByteGroupCannotOfferTrash() {
        val rows = listOf(row("original"), row("copy"))
        val safe = planExactDuplicateReview(rows, requestedUris = setOf("copy"))
        val zeroBytes = safe.copy(groups = safe.groups.map { group ->
            group.map { it.copy(sizeBytes = 0L) }
        })
        assertTrue(exactDuplicateReviewCards(zeroBytes).isEmpty())
        assertNull(exactDuplicateTrashPrompt(zeroBytes))
    }

    @Test fun restoredReviewCannotShowCopiesOutsideSelectedStorageOrFolder() {
        val rows = listOf(row("original"), row("copy"))
        val safe = planExactDuplicateReview(rows, requestedUris = setOf("copy"))
        assertEquals(1, exactDuplicateReviewCards(safe).size)

        // Restored selection metadata can disagree with the saved group rows.
        // Neither a different volume nor a different folder may be offered.
        val wrongStorage = safe.copy(includedStorageIds = setOf("usb"))
        assertTrue(exactDuplicateReviewCards(wrongStorage).isEmpty())
        assertNull(exactDuplicateTrashPrompt(wrongStorage))

        val wrongFolder = safe.copy(includedFolderTargets = setOf("Pictures/"))
        assertTrue(exactDuplicateReviewCards(wrongFolder).isEmpty())
        assertNull(exactDuplicateTrashPrompt(wrongFolder))

        val correctScope = safe.copy(
            includedStorageIds = setOf("phone"),
            includedFolderTargets = setOf("DCIM/"),
        )
        assertEquals(1, exactDuplicateReviewCards(correctScope).size)
    }

    @Test fun restoredReviewRejectsStaleSelectionAndKeeperMetadata() {
        val safe = planExactDuplicateReview(
            listOf(row("original"), row("copy")),
            requestedUris = setOf("copy"),
        )
        assertEquals(1, exactDuplicateReviewCards(safe).size)

        // A selected URI removed from the saved groups cannot remain visually
        // actionable, even if the separate trash prompt already rejects it.
        val staleSelection = safe.copy(selectedUris = safe.selectedUris + "missing")
        assertTrue(exactDuplicateReviewCards(staleSelection).isEmpty())
        assertNull(exactDuplicateTrashPrompt(staleSelection))

        val staleKeeper = safe.copy(protectedUris = safe.protectedUris + "missing")
        assertTrue(exactDuplicateReviewCards(staleKeeper).isEmpty())
        assertNull(exactDuplicateTrashPrompt(staleKeeper))

        val missingKeeper = safe.copy(protectedUris = emptySet())
        assertTrue(exactDuplicateReviewCards(missingKeeper).isEmpty())
        assertNull(exactDuplicateTrashPrompt(missingKeeper))
    }

    @Test fun scopedAndAmbiguousReviewsCannotProduceTrashPrompt() {
        val rows = listOf(row("original"), row("copy"))
        val ambiguous = planExactDuplicateReview(rows + rows[1].copy(dateModifiedMillis = 200),
            requestedUris = setOf("copy"))
        assertTrue(exactDuplicateReviewCards(ambiguous).isEmpty())
        assertNull(exactDuplicateTrashPrompt(ambiguous))
        val outOfScope = planExactDuplicateReview(rows,
            includedStorageIds = setOf("usb"), requestedUris = setOf("copy"))
        assertNull(exactDuplicateTrashPrompt(outOfScope))
    }
    @Test fun restoredReviewWithPhysicalPathAliasCannotShowTrashSelection() {
        val safe = planExactDuplicateReview(
            listOf(row("original"), row("copy")),
            requestedUris = setOf("copy"),
        )
        assertEquals(1, exactDuplicateReviewCards(safe).size)
        // Different URIs, but both resolve to the keeper's same physical path.
        // A restored UI snapshot must not bypass the planner's alias guard.
        val aliased = safe.copy(groups = safe.groups.map { group ->
            group.dropLast(1) + group.last().copy(sourcePath = "/DCIM/./original.jpg")
        })
        assertTrue(exactDuplicateReviewCards(aliased).isEmpty())
        assertNull(exactDuplicateTrashPrompt(aliased))
    }

}

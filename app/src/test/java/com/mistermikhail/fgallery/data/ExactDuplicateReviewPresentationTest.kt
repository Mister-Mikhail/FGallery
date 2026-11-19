package com.mistermikhail.fgallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactDuplicateReviewPresentationTest {
    private fun row(uri: String, hash: String = "a".repeat(64)) = CatalogMediaRow(
        uri = uri, mediaId = 1, name = "$uri.jpg", mimeType = "image/jpeg",
        kind = "IMAGE", dateTakenMillis = if (uri == "original") 1 else 123, width = 100, height = 100,
        durationMillis = 0, album = "Camera", relativePath = "DCIM/",
        sizeBytes = 250, dateModifiedMillis = 100,
        storageId = "phone", storageName = "phone", folderTarget = "DCIM/",
        sourcePath = "/DCIM/$uri.jpg", quickHash = "edge", fullHash = hash,
        recycled = false,
    )

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
}

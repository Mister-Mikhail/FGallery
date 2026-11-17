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

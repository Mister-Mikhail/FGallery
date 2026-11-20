package com.mistermikhail.fgallery.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicatePendingSelectionTest {
    @Test fun pendingTapSurvivesUnrelatedCatalogueEmission() {
        val pending = mapOf("copy" to true)
        assertEquals(emptySet<String>(), duplicatePendingSelectionKeysToDiscard(
            pending, setOf("original", "copy"), emptySet()))
    }

    @Test fun acknowledgedTapIsCleared() {
        assertEquals(setOf("copy"), duplicatePendingSelectionKeysToDiscard(
            mapOf("copy" to true), setOf("original", "copy"), setOf("copy")))
    }

    @Test fun removedFileCannotRetainPendingSelection() {
        assertEquals(setOf("old"), duplicatePendingSelectionKeysToDiscard(
            mapOf("old" to true), setOf("original", "copy"), emptySet()))
    }

    @Test fun acknowledgedUncheckIsCleared() {
        assertEquals(setOf("copy"), duplicatePendingSelectionKeysToDiscard(
            mapOf("copy" to false), setOf("original", "copy"), emptySet()))
    }
}

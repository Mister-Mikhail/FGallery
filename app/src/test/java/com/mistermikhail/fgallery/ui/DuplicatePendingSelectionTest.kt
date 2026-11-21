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
    @Test fun copyPromotedToProtectedKeeperCannotRetainOptimisticCheck() {
        assertEquals(setOf("copy"), duplicatePendingSelectionKeysToDiscard(
            mapOf("copy" to true),
            setOf("copy", "other-copy"),
            emptySet(),
            setOf("copy"),
        ))
    }

    @Test fun unrelatedPendingCopyRemainsWhileKeeperChanges() {
        assertEquals(setOf("former-copy"), duplicatePendingSelectionKeysToDiscard(
            mapOf("former-copy" to true, "other-copy" to true),
            setOf("former-copy", "other-copy"),
            emptySet(),
            setOf("former-copy"),
        ))
    }
}

package com.mistermikhail.fgallery.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicateScopeTransitionTest {
    @Test fun implicitAllIsRenderedAsChecked() {
        assertEquals(true, isDuplicateScopeChecked(emptySet(), "phone"))
        assertEquals(true, isDuplicateScopeChecked(emptySet(), "usb"))
    }

    @Test fun explicitScopeOnlyChecksItsMembers() {
        assertEquals(true, isDuplicateScopeChecked(setOf("phone"), "phone"))
        assertEquals(false, isDuplicateScopeChecked(setOf("phone"), "usb"))
    }

    @Test fun uncheckingOneFromAllNarrowsScopeInsteadOfWidening() {
        assertEquals(setOf("phone", "usb"),
            toggleDuplicateScope(listOf("phone", "usb", "sd"), emptySet(), "sd", false))
    }

    @Test fun selectingEveryAvailableScopeReturnsAllSentinel() {
        assertEquals(emptySet<String>(),
            toggleDuplicateScope(listOf("phone", "usb"), setOf("phone"), "usb", true))
    }

    @Test fun uncheckingLastExplicitScopeDoesNotSwitchToAll() {
        assertEquals(setOf("phone"),
            toggleDuplicateScope(listOf("phone", "usb"), setOf("phone"), "phone", false))
    }

    @Test fun unknownOptionCannotChangeScope() {
        assertEquals(setOf("phone"),
            toggleDuplicateScope(listOf("phone", "usb"), setOf("phone"), "unknown", false))
    }

    @Test fun emptyAvailabilityIsNotAnImplicitExpansion() {
        assertEquals(emptySet<String>(),
            toggleDuplicateScope(emptyList(), emptySet(), "phone", true))
    }
    @Test fun disconnectedSafVolumeDoesNotWidenScopeWhenAnotherVolumeAppears() {
        assertEquals(setOf("usb-disconnected"),
            toggleDuplicateScope(listOf("phone", "sd"), setOf("usb-disconnected"), "phone", true))
        assertEquals(setOf("usb-disconnected"),
            toggleDuplicateScope(listOf("phone", "sd"), setOf("usb-disconnected"), "sd", false))
    }
    @Test fun partiallyDisconnectedScopeCannotSilentlyDropMissingVolume() {
        val chosen = setOf("phone", "usb-disconnected")
        val available = listOf("phone", "sd")
        assertEquals(chosen, toggleDuplicateScope(available, chosen, "sd", true))
        assertEquals(chosen, toggleDuplicateScope(available, chosen, "phone", false))
    }

    @Test fun partiallyDisconnectedFolderScopeCannotBecomeImplicitAll() {
        val chosen = setOf("DCIM/", "USB/Pictures/")
        assertEquals(chosen, toggleDuplicateScope(listOf("DCIM/", "Downloads/"), chosen, "Downloads/", true))
    }
}

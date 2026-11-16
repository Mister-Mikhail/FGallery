package com.mistermikhail.fgallery.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicateScopeTransitionTest {
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
}

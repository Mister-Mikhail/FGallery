package com.mistermikhail.fgallery.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreDeltaTest {
    @Test fun unchangedGenerationWithNewMediaIdForcesFullVolumeReconciliation() {
        assertTrue(hasNewMediaStoreIds(setOf(11L, 12L, 13L), setOf(11L, 12L)))
    }

    @Test fun unchangedGenerationWithDeletedOrTrashedIdNeedsOnlyCheapFiltering() {
        assertFalse(hasNewMediaStoreIds(setOf(11L), setOf(11L, 12L)))
    }

    @Test fun unchangedGenerationWithIdenticalIdsAvoidsFullMetadataQuery() {
        assertFalse(hasNewMediaStoreIds(setOf(11L, 12L), setOf(11L, 12L)))
        assertFalse(hasNewMediaStoreIds(emptySet(), emptySet()))
    }
}

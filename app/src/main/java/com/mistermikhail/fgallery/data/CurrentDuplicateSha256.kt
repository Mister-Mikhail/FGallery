package com.mistermikhail.fgallery.data

import android.content.Context
import android.net.Uri
import java.io.IOException

/**
 * Reads the CURRENT bytes of a Stage 6 candidate rather than trusting the
 * catalogue's stored digest. Invoke on Dispatchers.IO, never on the UI thread.
 *
 * This is a verification primitive, not authorization to move a file. The
 * caller must also check the current catalogue, explicit user consent and
 * the recycle-bin preflight before mutating any source.
 */
internal fun currentDuplicateSha256(
    context: Context,
    row: CatalogMediaRow,
    checkActive: () -> Unit = {},
): String {
    require(row.sizeBytes > 0L) { "Cannot verify an unknown/empty source length" }
    checkActive()
    val uri = Uri.parse(row.uri)
    val stream = context.contentResolver.openInputStream(uri)
        ?: throw IOException("Cannot open original media for duplicate verification")
    return stream.use {
        DuplicateFingerprints.full(it, checkActive, expectedBytes = row.sizeBytes)
    }
}

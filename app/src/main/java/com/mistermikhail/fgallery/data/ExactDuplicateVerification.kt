package com.mistermikhail.fgallery.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Stage 6 bridge between the read-only review plan and live ContentResolver
 * bytes. This returns only a verified proposal, never a trash operation.
 *
 * Call only after the separate user confirmation. The mutation layer must
 * still re-check current file identity/permissions immediately before moving
 * each file, and invalidate the batch on any catalogue generation change.
 */
internal suspend fun verifyCurrentExactDuplicateSelection(
    context: Context,
    reviewed: ExactDuplicateReview,
    currentRows: List<CatalogMediaRow>,
    explicitlyConfirmed: Boolean,
): Set<String> = withContext(Dispatchers.IO) {
    val jobContext = coroutineContext
    jobContext.ensureActive()
    verifiedExactDuplicateUris(
        reviewed = reviewed,
        currentRows = currentRows,
        explicitlyConfirmed = explicitlyConfirmed,
        readCurrentSha256 = { row ->
            jobContext.ensureActive()
            currentDuplicateSha256(context, row) {
                jobContext.ensureActive()
            }
        },
    ).also { jobContext.ensureActive() }
}

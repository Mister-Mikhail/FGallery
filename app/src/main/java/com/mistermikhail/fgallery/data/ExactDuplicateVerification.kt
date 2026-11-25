package com.mistermikhail.fgallery.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Authorize the whole confirmed batch against a fresh catalogue and live bytes.
 * At each move, the same-size catalogue rows give the current exact group.
 * Rehash the copy and then its keeper under the bin lock immediately before moving.
 */
internal suspend fun DriveRecycleBin.trashExactDuplicateBatch(
    context: Context,
    reviewed: ExactDuplicateReview,
    catalogue: suspend () -> List<CatalogMediaRow>,
    rowsBySize: suspend (Long) -> List<CatalogMediaRow>,
    mediaItem: (CatalogMediaRow) -> MediaItem,
    onMoved: (MediaItem, MediaItem) -> Unit,
) = withContext(Dispatchers.IO) {
    val initial = catalogue()
    val confirmed = verifyCurrentExactDuplicateSelection(context, reviewed, initial, explicitlyConfirmed = true)
    check(confirmed.isNotEmpty() && confirmed == reviewed.selectedUris) {
        "Файл или каталог дубликатов изменился. Обновите список и подтвердите заново."
    }
    val rowsByUri = initial.associateBy { it.uri }
    val keeperByCopy = buildMap {
        for (group in reviewed.groups) {
            val keeper = group.first()
            for (copy in group.drop(1)) if (copy.uri in confirmed) put(copy.uri, keeper)
        }
    }
    val items = confirmed.sorted().map { mediaItem(rowsByUri.getValue(it)) }
    val checkedPaths = (confirmed.map(rowsByUri::getValue) + keeperByCopy.values).distinctBy { it.uri }
        .associate { it.uri to File(it.sourcePath).canonicalPath }
    val jobContext = coroutineContext
    trashWithPreMoveCheck(items, verifyPreflight = { it.uriKey in confirmed }, onMoved = onMoved) { item ->
        val copy = rowsByUri.getValue(item.uriKey)
        val keeper = keeperByCopy.getValue(item.uriKey)
        val latest = rowsBySize(copy.sizeBytes)
        if (!exactDuplicateMovePairStillValid(reviewed, latest, copy.uri)) return@trashWithPreMoveCheck false
        val copyHash = currentDuplicateSha256(context, copy) { jobContext.ensureActive() }
        val keeperHash = currentDuplicateSha256(context, keeper) { jobContext.ensureActive() }
        File(copy.sourcePath).canonicalPath == checkedPaths.getValue(copy.uri) &&
            File(keeper.sourcePath).canonicalPath == checkedPaths.getValue(keeper.uri) &&
            copyHash.equals(copy.fullHash, ignoreCase = true) && keeperHash.equals(keeper.fullHash, ignoreCase = true)
    }
}

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

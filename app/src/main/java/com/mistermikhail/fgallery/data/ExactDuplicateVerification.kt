package com.mistermikhail.fgallery.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Authorize the confirmed batch against the catalogue buckets for its sizes and live bytes.
 * At each move, retrieve only the copy and the protected original by their URI.
 * Rehash the copy and then its keeper under the bin lock immediately before moving.
 */
internal suspend fun DriveRecycleBin.trashExactDuplicateBatch(
    context: Context,
    reviewed: ExactDuplicateReview,
    rowsBySizes: suspend (Set<Long>) -> List<CatalogMediaRow>,
    movePair: suspend (String, String) -> List<CatalogMediaRow>,
    mediaItem: (CatalogMediaRow) -> MediaItem,
    onMoved: (MediaItem, MediaItem) -> Unit,
) = withContext(Dispatchers.IO) {
    val sizes = reviewed.groups.filter { group -> group.any { it.uri in reviewed.selectedUris } }
        .mapTo(hashSetOf()) { it.first().sizeBytes }
    val affectedGroups = reviewed.groups.filter { it.firstOrNull()?.sizeBytes in sizes }
    val scopedReview = reviewed.copy(groups = affectedGroups)
    val initial = rowsBySizes(sizes)
    val confirmed = verifyCurrentExactDuplicateSelection(context, scopedReview, initial, explicitlyConfirmed = true)
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
        val latest = movePair(copy.uri, keeper.uri)
        if (!exactDuplicateMovePairStillValid(scopedReview, latest, copy.uri)) return@trashWithPreMoveCheck false
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

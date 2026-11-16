package com.mistermikhail.fgallery.data

/**
 * Read-only Stage 6 review plan. Only full-SHA-256 exact-byte groups are eligible.
 * A visually similar image, RAW+JPEG pair or edited photograph never enters
 * this plan based on dHash or matching capture metadata.
 *
 * This is NOT a delete command. The caller must show a separate confirmation
 * dialog and revalidate current file generations/permissions before trashing.
 */
internal data class ExactDuplicateReview(
    val groups: List<List<CatalogMediaRow>>,
    val selectedUris: Set<String>,
    val protectedUris: Set<String>,
    val includedStorageIds: Set<String>,
    val includedFolderTargets: Set<String>,
) {
    val selectedCount: Int get() = selectedUris.size
}

internal fun planExactDuplicateReview(
    indexedRows: List<CatalogMediaRow>,
    includedStorageIds: Set<String> = emptySet(),
    includedFolderTargets: Set<String> = emptySet(),
    requestedUris: Set<String> = emptySet(),
): ExactDuplicateReview {
    // Scope both the comparison and the possible action. An empty scope means
    // all storages/folders; never silently include files outside a chosen scope.
    val scoped = indexedRows.filter { row ->
        (includedStorageIds.isEmpty() || row.storageId in includedStorageIds) &&
            (includedFolderTargets.isEmpty() || row.folderTarget in includedFolderTargets)
    }
    // A transient SAF/MediaStore reconciliation may expose multiple generations
    // of the same URI, including one outside the chosen scope. Fail closed globally:
    // a stale off-scope row must not make an in-scope URI actionable.
    // The caller can retry after catalogue reconciliation completes.
    if (indexedRows.map { it.uri }.toSet().size != indexedRows.size) {
        return ExactDuplicateReview(emptyList(), emptySet(), emptySet(),
            includedStorageIds.toSet(), includedFolderTargets.toSet())
    }
    val groups = exactDuplicateGroups(scoped)
    val protected = groups.map { it.first().uri }.toSet()
    val eligible = groups.flatMap { it.drop(1) }.map { it.uri }.toSet()
    // Explicit opt-in only; never preselect anything for the trash.
    return ExactDuplicateReview(groups, requestedUris.intersect(eligible), protected,
        includedStorageIds.toSet(), includedFolderTargets.toSet())
}

/**
 * Confirmation is bound to the same exact group snapshot. Any catalog change
 * between review and confirmation invalidates the proposal (fail closed).
 * This does not replace a final on-disk metadata/hash check before mutation.
 */
internal fun confirmedExactDuplicateUris(
    reviewed: ExactDuplicateReview,
    currentRows: List<CatalogMediaRow>,
    includedStorageIds: Set<String> = emptySet(),
    includedFolderTargets: Set<String> = emptySet(),
    explicitlyConfirmed: Boolean,
): Set<String> {
    if (!explicitlyConfirmed || reviewed.selectedUris.isEmpty()) return emptySet()
    // The confirmation must use exactly the scope shown during review.
    // Otherwise a caller could silently widen or narrow the affected folders.
    if (includedStorageIds != reviewed.includedStorageIds ||
        includedFolderTargets != reviewed.includedFolderTargets) return emptySet()
    // Reconciliation can briefly expose two rows for one URI. distinctBy in
    // exactDuplicateGroups would hide a conflicting second generation; do not
    // authorize trash until the catalogue has converged to one row per URI.
    if (currentRows.map { it.uri }.distinct().size != currentRows.size) return emptySet()
    val current = planExactDuplicateReview(
        currentRows, includedStorageIds, includedFolderTargets, reviewed.selectedUris
    )
    val identity = { groups: List<List<CatalogMediaRow>> ->
        groups.map { group ->
            group.map { row ->
                listOf(row.uri, row.sizeBytes.toString(), row.dateModifiedMillis.toString(),
                    row.quickHash.orEmpty(), row.fullHash.orEmpty(), row.storageId,
                    row.folderTarget, row.recycled.toString(), row.name, row.mimeType,
                    row.kind, row.width.toString(), row.height.toString(),
                    row.dateTakenMillis.toString(), row.sourcePath.orEmpty(),
                    row.mediaId.toString(), row.storageName, row.album,
                    row.relativePath, row.durationMillis.toString(),
                    row.exifCamera.orEmpty())
            }
        }
    }
    if (identity(reviewed.groups) != identity(current.groups)) return emptySet()
    return current.selectedUris.takeIf { it == reviewed.selectedUris } ?: emptySet()
}

/**
 * Final Stage 6 pre-trash gate. The caller MUST obtain fresh SHA-256 digests
 * from the current file bytes, not from Room or a cached fingerprint. Verify
 * the protected original as well as every selected copy; an inaccessible,
 * replaced or changed file invalidates the entire batch.
 *
 * This returns URIs for a *separate* explicit trash operation; it never moves
 * or deletes a file. The mutation layer must still enforce its own permission
 * and file-generation checks immediately before each move.
 */
internal fun verifiedExactDuplicateUris(
    reviewed: ExactDuplicateReview,
    currentRows: List<CatalogMediaRow>,
    includedStorageIds: Set<String> = reviewed.includedStorageIds,
    includedFolderTargets: Set<String> = reviewed.includedFolderTargets,
    explicitlyConfirmed: Boolean,
    readCurrentSha256: (CatalogMediaRow) -> String?,
): Set<String> {
    val authorized = confirmedExactDuplicateUris(
        reviewed, currentRows, includedStorageIds, includedFolderTargets, explicitlyConfirmed
    )
    if (authorized.isEmpty()) return emptySet()
    val relevantGroups = reviewed.groups.filter { group ->
        group.any { it.uri in authorized }
    }
    // Never authorize a batch if the original is not still readable and
    // byte-identical to the copies. A provider error fails the whole batch.
    for (group in relevantGroups) {
        for (row in group) {
            val observed = try {
                readCurrentSha256(row)
            } catch (_: Exception) {
                null
            }
            if (observed == null || !observed.equals(row.fullHash, ignoreCase = true)) {
                return emptySet()
            }
        }
    }
    return authorized
}


/**
 * Read-only facts for a Stage 6 review card. No removal recommendation is
 * implied: the user must select copies manually and confirm separately.
 * Storage and folder scope have already been applied by the review planner.
 */
internal data class ExactDuplicateGroupSummary(
    val originalUri: String,
    val copyUris: List<String>,
    val sizeBytes: Long,
    val reclaimableBytes: Long,
    val sha256: String,
)

internal fun exactDuplicateGroupSummaries(
    review: ExactDuplicateReview,
): List<ExactDuplicateGroupSummary> = review.groups.mapNotNull { group ->
    val original = group.firstOrNull() ?: return@mapNotNull null
    if (group.size < 2 || original.uri !in review.protectedUris) return@mapNotNull null
    val copies = group.drop(1)
    if (copies.any { it.uri in review.protectedUris }) return@mapNotNull null
    val digest = original.fullHash ?: return@mapNotNull null
    if (digest.isBlank() || copies.any { it.fullHash != digest || it.sizeBytes != original.sizeBytes }) {
        return@mapNotNull null
    }
    val selected = copies.filter { it.uri in review.selectedUris }
    ExactDuplicateGroupSummary(
        originalUri = original.uri,
        copyUris = copies.map { it.uri },
        sizeBytes = original.sizeBytes,
        reclaimableBytes = selected.sumOf { it.sizeBytes },
        sha256 = digest,
    )
}

/**
 * Pure selection transition for the Stage 6 review UI. The UI must never
 * mutate selectedUris directly or treat a suggested original as disposable.
 * Rebuild the proposal from the same scoped catalogue snapshot on every tap.
 * A later scan must replace the entire review and clear consent.
 */
internal fun selectExactDuplicateCopy(
    review: ExactDuplicateReview,
    indexedRows: List<CatalogMediaRow>,
    uri: String,
    selected: Boolean,
): ExactDuplicateReview {
    // A stale review is not actionable, even for a harmless-looking tap.
    val fresh = planExactDuplicateReview(
        indexedRows, review.includedStorageIds, review.includedFolderTargets,
        review.selectedUris
    )
    if (fresh.groups != review.groups || fresh.protectedUris != review.protectedUris ||
        fresh.selectedUris != review.selectedUris) {
        return planExactDuplicateReview(
            indexedRows, review.includedStorageIds, review.includedFolderTargets
        )
    }
    val eligible = fresh.groups.flatMap { it.drop(1) }.map { it.uri }.toSet()
    if (uri !in eligible || uri in fresh.protectedUris) return fresh
    val requested = if (selected) fresh.selectedUris + uri else fresh.selectedUris - uri
    return planExactDuplicateReview(
        indexedRows, fresh.includedStorageIds, fresh.includedFolderTargets, requested
    )
}

/**
 * Scope checkbox transition. Empty selection represents ALL, so unchecking
 * one item from ALL must narrow the scope rather than silently retain ALL.
 * Never turn the final unchecked item into an implicit ALL selection.
 */
internal fun toggleDuplicateScope(
    available: List<String>,
    included: Set<String>,
    item: String,
    checked: Boolean,
): Set<String> {
    val options = available.toSet()
    if (item !in options) return included
    val current = if (included.isEmpty()) options else included.intersect(options)
    val next = if (checked) current + item else current - item
    // No empty-scope sentinel for "none": keep the last selected option.
    if (next.isEmpty()) return current
    return if (next == options) emptySet() else next
}

/** An empty stored filter means ALL, so every visible checkbox is checked. */
internal fun isDuplicateScopeChecked(included: Set<String>, item: String): Boolean =
    included.isEmpty() || item in included

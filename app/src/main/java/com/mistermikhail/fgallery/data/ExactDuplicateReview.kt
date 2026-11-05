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
    // of the same URI. Do not present an actionable review from ambiguous rows.
    // The caller can retry after catalogue reconciliation completes.
    if (scoped.map { it.uri }.toSet().size != scoped.size) {
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
    val scopedRows = currentRows.filter { row ->
        (includedStorageIds.isEmpty() || row.storageId in includedStorageIds) &&
            (includedFolderTargets.isEmpty() || row.folderTarget in includedFolderTargets)
    }
    if (scopedRows.map { it.uri }.distinct().size != scopedRows.size) return emptySet()
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
                    row.dateTakenMillis.toString(), row.sourcePath.orEmpty())
            }
        }
    }
    if (identity(reviewed.groups) != identity(current.groups)) return emptySet()
    return current.selectedUris.takeIf { it == reviewed.selectedUris } ?: emptySet()
}

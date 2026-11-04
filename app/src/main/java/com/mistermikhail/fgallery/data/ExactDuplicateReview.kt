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
    val groups = exactDuplicateGroups(scoped)
    val protected = groups.map { it.first().uri }.toSet()
    val eligible = groups.flatMap { it.drop(1) }.map { it.uri }.toSet()
    // Explicit opt-in only; never preselect anything for the trash.
    return ExactDuplicateReview(groups, requestedUris.intersect(eligible), protected)
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
    val current = planExactDuplicateReview(
        currentRows, includedStorageIds, includedFolderTargets, reviewed.selectedUris
    )
    val identity = { groups: List<List<CatalogMediaRow>> ->
        groups.map { group ->
            group.map { row ->
                listOf(row.uri, row.sizeBytes.toString(), row.dateModifiedMillis.toString(),
                    row.quickHash.orEmpty(), row.fullHash.orEmpty(), row.storageId,
                    row.folderTarget, row.recycled.toString())
            }
        }
    }
    if (identity(reviewed.groups) != identity(current.groups)) return emptySet()
    return current.selectedUris.takeIf { it == reviewed.selectedUris } ?: emptySet()
}

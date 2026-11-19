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
    // A blank URI cannot be resolved safely by the mutation layer. Reject the
    // entire review rather than offering an item that preflight must refuse.
    if (indexedRows.any { it.uri.isBlank() } ||
        indexedRows.map { it.uri }.toSet().size != indexedRows.size) {
        return ExactDuplicateReview(emptyList(), emptySet(), emptySet(),
            includedStorageIds.toSet(), includedFolderTargets.toSet())
    }
    // Different provider URIs can refer to the same physical file. Never offer
    // an alias of the protected original as a disposable duplicate. A known
    // non-empty source path must be unique across the entire catalogue.
    // Match the mutation preflight's lexical path identity: distinct provider
    // URIs may spell the same physical file with "." or ".." segments.
    // Never normalize opaque SAF URIs or infer paths when the catalogue has none.
    val rawPaths = indexedRows.mapNotNull { it.sourcePath }
    // NUL cannot identify a filesystem path. Reject before canonicalization so
    // malformed provider metadata never reaches a review action.
    if (rawPaths.any { it.isBlank() || '\u0000' in it }) {
        return ExactDuplicateReview(emptyList(), emptySet(), emptySet(),
            includedStorageIds.toSet(), includedFolderTargets.toSet())
    }
    // Keep review identity consistent with the mutation preflight: symlinked
    // local paths must not present the protected original as a second copy.
    // If a provider path cannot be resolved, fail closed before any selection.
    val knownPaths = try {
        rawPaths.map { java.io.File(it).canonicalPath }
    } catch (_: java.io.IOException) {
        return ExactDuplicateReview(emptyList(), emptySet(), emptySet(),
            includedStorageIds.toSet(), includedFolderTargets.toSet())
    }
    if (knownPaths.toSet().size != knownPaths.size) {
        return ExactDuplicateReview(emptyList(), emptySet(), emptySet(),
            includedStorageIds.toSet(), includedFolderTargets.toSet())
    }
    // Reject malformed or legacy fingerprints before presenting actionable copies.
    // A full SHA-256 is exactly 64 hexadecimal characters; a short label must
    // never be treated as evidence of byte identity in the review UI.
    val groups = exactDuplicateGroups(scoped).filter { group ->
        group.all { row ->
            val hash = row.fullHash
            hash != null && hash.length == 64 &&
                hash.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        }
    }
    val protected = groups.map { it.first().uri }.toSet()
    val eligible = groups.flatMap { it.drop(1) }.map { it.uri }.toSet()
    // Explicit opt-in only; never preselect anything for the trash.
    return ExactDuplicateReview(groups, requestedUris.intersect(eligible), protected,
        includedStorageIds.toSet(), includedFolderTargets.toSet())
}

/**
 * Mutation-time snapshot gate for one selected copy and its protected keeper.
 * Earlier successful moves may shrink a group, so compare the two relevant
 * rows rather than the entire original review after every move.
 */
internal fun exactDuplicateMovePairStillValid(
    reviewed: ExactDuplicateReview,
    currentRows: List<CatalogMediaRow>,
    selectedUri: String,
): Boolean {
    if (selectedUri !in reviewed.selectedUris || selectedUri in reviewed.protectedUris) return false
    val originalGroup = reviewed.groups.singleOrNull { group ->
        group.drop(1).any { it.uri == selectedUri }
    } ?: return false
    val keeper = originalGroup.first()
    val copy = originalGroup.single { it.uri == selectedUri }
    if (currentRows.count { it.uri == keeper.uri } != 1 ||
        currentRows.count { it.uri == copy.uri } != 1) return false
    if (currentRows.first { it.uri == keeper.uri } != keeper ||
        currentRows.first { it.uri == copy.uri } != copy) return false
    val fresh = planExactDuplicateReview(
        currentRows, reviewed.includedStorageIds,
        reviewed.includedFolderTargets, setOf(selectedUri),
    )
    return selectedUri in fresh.selectedUris &&
        fresh.groups.any { group ->
            group.first().uri == keeper.uri && group.any { it.uri == selectedUri }
        }
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
    // Read through the current catalogue, not through rows retained by the
    // earlier UI snapshot. Identity was checked above, but providers may need
    // the current row instance to resolve the file at verification time.
    val currentByUri = currentRows.associateBy { it.uri }
    // Never authorize a batch if the original is not still readable and
    // byte-identical to the copies. A provider error fails the whole batch.
    for (group in relevantGroups) {
        // Only the protected original and explicitly selected copies require
        // fresh byte reads. An unselected, disconnected SAF copy must not
        // prevent the user from moving a different verified copy.
        for (reviewedRow in group.filter { it.uri == group.first().uri || it.uri in authorized }) {
            // An interrupted verifier must not begin another SAF read.
            // Checking only after a provider returns can block on a disconnected
            // source despite the caller already having cancelled the batch.
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedException("Duplicate verification interrupted before read")
            }
            val row = currentByUri[reviewedRow.uri] ?: return emptySet()
            val observed = try {
                readCurrentSha256(row)
            } catch (cancelled: java.util.concurrent.CancellationException) {
                // Cancellation must stop the operation, not be mistaken for an
                // unreadable file while a superseded scan keeps running.
                throw cancelled
            } catch (interrupted: InterruptedException) {
                // A worker interrupted during SAF I/O must not continue toward
                // a trash operation as though the source were merely unreadable.
                Thread.currentThread().interrupt()
                throw interrupted
            } catch (_: Exception) {
                null
            }
            // An interrupt arriving after the provider returned its digest must
            // still revoke the entire batch before any trash intent escapes.
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Duplicate verification interrupted")
            // Never accept a malformed catalogue fingerprint as proof of byte identity.
            // A SHA-256 digest is exactly 64 hexadecimal characters; a provider
            // returning a cached label or truncated digest must fail closed.
            val expected = row.fullHash
            if (expected == null || expected.length != 64 ||
                !expected.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } ||
                observed == null || observed.length != 64 ||
                !observed.equals(expected, ignoreCase = true)) {
                return emptySet()
            }
        }
    }
    // A provider may replace the protected original while selected copies
    // are being read. Re-read it after the copies, so a changed keeper cannot
    // authorize a stale batch. This is still only a proposal: the mutation
    // layer must perform its own immediate per-file preflight.
    for (group in relevantGroups) {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Duplicate verification interrupted")
        val keeper = currentByUri[group.first().uri] ?: return emptySet()
        val observed = try {
            readCurrentSha256(keeper)
        } catch (cancelled: java.util.concurrent.CancellationException) {
            throw cancelled
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw interrupted
        } catch (_: Exception) {
            null
        }
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Duplicate verification interrupted")
        if (observed == null || observed.length != 64 ||
            !observed.equals(keeper.fullHash, ignoreCase = true)) return emptySet()
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
    // A restored or hand-built UI snapshot must not show two aliases of one
    // URI as independent disposable copies. Every group must have exactly one
    // protected keeper, and a selected URI must never be the keeper elsewhere.
    if (group.any { it.uri.isBlank() } ||
        group.map { it.uri }.toSet().size != group.size ||
        copies.any { it.uri in review.protectedUris } ||
        group.count { it.uri in review.protectedUris } != 1) return@mapNotNull null
    val digest = original.fullHash ?: return@mapNotNull null
    // Even a malformed hand-built/stale review must not render a plausible
    // SHA-256 card or offer an action. Reject zero-length files and invalid
    // digests rather than trusting the planner was always the caller.
    if (original.sizeBytes <= 0L || digest.length != 64 ||
        digest.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' } ||
        copies.any { !it.fullHash.equals(digest, ignoreCase = true) ||
            it.sizeBytes != original.sizeBytes }) {
        return@mapNotNull null
    }
    val selected = copies.filter { it.uri in review.selectedUris }
    // Never show a wrapped reclaimable-size estimate in a review card. An
    // invalid catalogue size or overflow makes this group non-actionable.
    val reclaimableBytes = try {
        selected.fold(0L) { total, row ->
            if (row.sizeBytes <= 0L) return@mapNotNull null
            Math.addExact(total, row.sizeBytes)
        }
    } catch (_: ArithmeticException) {
        return@mapNotNull null
    }
    ExactDuplicateGroupSummary(
        originalUri = original.uri,
        copyUris = copies.map { it.uri },
        sizeBytes = original.sizeBytes,
        reclaimableBytes = reclaimableBytes,
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
    // If a mounted SAF/USB volume vanished, preserve the explicit scope.
    // Treating a now-unknown scope as ALL would unexpectedly expose other media.
    // A partially disconnected scope is unsafe too: changing another checkbox
    // must not silently discard missing SAF/USB targets and widen the search.
    // Require a refreshed catalogue and an explicit new scope instead.
    if (included.isNotEmpty() && !options.containsAll(included)) return included
    val current = if (included.isEmpty()) options else included.intersect(options)
    val next = if (checked) current + item else current - item
    // No empty-scope sentinel for "none": keep the last selected option.
    if (next.isEmpty()) return current
    return if (next == options) emptySet() else next
}

/** An empty stored filter means ALL, so every visible checkbox is checked. */
internal fun isDuplicateScopeChecked(included: Set<String>, item: String): Boolean =
    included.isEmpty() || item in included

/** A disconnected SAF volume or removed folder invalidates pending consent. */
internal fun duplicateReviewScopeAvailable(
    review: ExactDuplicateReview,
    availableStorages: List<String>,
    availableFolders: List<String>,
): Boolean =
    review.includedStorageIds.all { it in availableStorages } &&
        review.includedFolderTargets.all { it in availableFolders } &&
        review.groups.flatten().all { it.storageId in availableStorages && it.folderTarget in availableFolders }

/** A missing source or invalidated selection revokes an open trash dialog. */
internal fun duplicateConfirmationInvalidated(
    review: ExactDuplicateReview,
    availableStorages: List<String>,
    availableFolders: List<String>,
): Boolean = !duplicateReviewScopeAvailable(review, availableStorages, availableFolders) ||
    exactDuplicateTrashPrompt(review) == null

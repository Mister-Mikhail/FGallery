package com.mistermikhail.fgallery.data

/**
 * Immutable Stage 6 review content. This layer does not grant trash permission.
 * Only byte-identical SHA-256 groups from ExactDuplicateReview are shown.
 */
internal data class ExactDuplicateReviewItem(
    val uri: String,
    val displayName: String,
    val storageId: String,
    val folderTarget: String,
    val protectedOriginal: Boolean,
    val manuallySelected: Boolean,
    val explanation: String,
)

internal data class ExactDuplicateReviewCard(
    val sha256: String,
    val sizeBytes: Long,
    val items: List<ExactDuplicateReviewItem>,
    val selectedCopyCount: Int,
    val selectedBytes: Long,
)

internal fun exactDuplicateReviewCards(
    review: ExactDuplicateReview,
): List<ExactDuplicateReviewCard> {
    val summaries = exactDuplicateGroupSummaries(review)
    return review.groups.mapNotNull { group ->
        val summary = summaries.firstOrNull { it.originalUri == group.firstOrNull()?.uri }
            ?: return@mapNotNull null
        ExactDuplicateReviewCard(
            sha256 = summary.sha256,
            sizeBytes = summary.sizeBytes,
            items = group.map { row ->
                val original = row.uri == summary.originalUri
                ExactDuplicateReviewItem(
                    uri = row.uri,
                    displayName = row.name,
                    storageId = row.storageId,
                    folderTarget = row.folderTarget,
                    protectedOriginal = original,
                    manuallySelected = !original && row.uri in review.selectedUris,
                    explanation = if (original) {
                        "Защищённый оригинал — нельзя выбрать для корзины"
                    } else {
                        "Точная копия: совпадают размер и полный SHA-256; выбор только вручную"
                    },
                )
            },
            selectedCopyCount = group.drop(1).count { it.uri in review.selectedUris },
            selectedBytes = summary.reclaimableBytes,
        )
    }
}

/**
 * Information for the separate, explicit trash confirmation. Never authorizes
 * a move by itself; the caller must run verifiedExactDuplicateUris using fresh
 * file bytes after the user confirms.
 */
internal data class ExactDuplicateTrashPrompt(
    val selectedCopyCount: Int,
    val selectedBytes: Long,
    val message: String,
)

internal fun exactDuplicateTrashPrompt(
    review: ExactDuplicateReview,
): ExactDuplicateTrashPrompt? {
    if (review.selectedUris.isEmpty() || review.selectedUris.any { it in review.protectedUris }) return null
    val cards = exactDuplicateReviewCards(review)
    val selected = cards.sumOf { it.selectedCopyCount }
    if (selected == 0 || selected != review.selectedUris.size) return null
    // A corrupted or extreme catalogue size must never wrap the confirmation
    // estimate into a negative/small value. Refuse consent rather than display
    // misleading reclaimable bytes for a destructive operation.
    val selectedSizes = review.groups.flatten()
        .filter { it.uri in review.selectedUris }
        .map { it.sizeBytes }
    if (selectedSizes.size != selected || selectedSizes.any { it <= 0L }) return null
    val selectedBytes = try {
        selectedSizes.fold(0L) { total, size -> Math.addExact(total, size) }
    } catch (_: ArithmeticException) {
        return null
    }
    return ExactDuplicateTrashPrompt(
        selectedCopyCount = selected,
        selectedBytes = selectedBytes,
        message = "Переместить $selected выбранных точных копий в корзину? " +
            "Оригиналы останутся на месте. Перед операцией файлы и SHA-256 " +
            "будут проверены повторно. Отмена не изменяет файлы.",
    )
}

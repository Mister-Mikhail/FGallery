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
    return review.groups.zip(summaries).map { (group, summary) ->
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
            selectedCopyCount = group.count { it.uri in review.selectedUris },
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
    if (review.selectedUris.isEmpty()) return null
    val cards = exactDuplicateReviewCards(review)
    val selected = cards.sumOf { it.selectedCopyCount }
    if (selected == 0 || selected != review.selectedUris.size) return null
    return ExactDuplicateTrashPrompt(
        selectedCopyCount = selected,
        selectedBytes = cards.sumOf { it.selectedBytes },
        message = "Переместить $selected выбранных точных копий в корзину? " +
            "Оригиналы останутся на месте. Перед операцией файлы и SHA-256 " +
            "будут проверены повторно. Отмена не изменяет файлы.",
    )
}

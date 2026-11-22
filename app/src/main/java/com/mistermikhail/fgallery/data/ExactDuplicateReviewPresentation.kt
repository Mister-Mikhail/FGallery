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
    // A stale review can contain the same provider URI in different SHA groups.
    // Hide all cards until the catalogue is reconciled: a visually selectable
    // alias must not appear even when the separate trash prompt rejects it.
    val allUris = review.groups.flatten().map { it.uri }
    if (allUris.size != allUris.toSet().size) return emptyList()
    // Index once: repeated linear searches become quadratic on large duplicate libraries.
    val summaries = exactDuplicateGroupSummaries(review)
    // Never display a partial catalogue when one group is malformed or stale.
    // The user must not mistake a filtered subset for the complete scan.
    if (summaries.size != review.groups.size) return emptyList()
    val summariesByOriginal = summaries.associateBy { it.originalUri }
    if (summariesByOriginal.size != summaries.size) return emptyList()
    return review.groups.mapNotNull { group ->
        val summary = summariesByOriginal[group.firstOrNull()?.uri]
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
                        "Защищённый оригинал — нельзя выбрать для корзины. " +
                            exactDuplicateKeeperReason(group)
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
    // A stale/corrupt catalogue may repeat the same URI across SHA groups.
    // Never present consent for an ambiguous selection, even if the displayed
    // copy count happens to match the number of selected URI strings.
    val allUris = review.groups.flatten().map { it.uri }
    if (allUris.size != allUris.toSet().size) return null
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

/**
 * Explain the deterministic keeper choice without implying that a matching
 * SHA-256 identifies the historical camera original. All group members are
 * byte-identical; the app protects one copy and never selects any for trash.
 */
internal fun exactDuplicateKeeperReason(group: List<CatalogMediaRow>): String {
    val keeper = group.firstOrNull() ?: return "Копия сохранена по правилу защиты."
    if (group.size < 2) return "Копия сохранена по правилу защиты."
    val others = group.drop(1)
    val keeperDate = keeper.dateTakenMillis.takeIf { it > 0L }
    // Missing dates are unknown, not evidence that the keeper is the
    // earliest capture. Never present an unverified chronological claim.
    if (keeperDate != null && others.all {
            it.dateTakenMillis > 0L && keeperDate < it.dateTakenMillis
        }) {
        return "Сохранена копия с самой ранней датой съёмки."
    }
    if (!keeper.exifCamera.isNullOrBlank() &&
        others.all { it.exifCamera.isNullOrBlank() }) {
        return "Сохранена копия с метаданными камеры."
    }
    return "Сохранена одна копия по стабильному порядку каталога; " +
        "исторический оригинал по SHA-256 определить нельзя."
}

package com.mistermikhail.fgallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contract for the Stage 6 Compose review: scope changes must discard consent. */
class ExactDuplicateReviewScreenContractTest {
    @Test fun rejectedRestoredCatalogueNeverMasqueradesAsEmptyScan() {
        val message: (Int, Int) -> String = { groups, cards ->
            com.mistermikhail.fgallery.ui.duplicateExactResultsMessage(groups, cards)
        }
        assertTrue(message(1, 0).contains("заблокирован"))
        assertTrue(message(2, 0).contains("Обновите поиск"))
        assertFalse(message(1, 0).contains("не найдено"))
        assertTrue(message(0, 0).contains("не найдено"))
        assertTrue(message(1, 1).contains("не найдено"))
    }

    @Test fun trashDialogDoesNotRepeatExactByteCount() {
        val review = planExactDuplicateReview(
            listOf(row("original", "phone", "DCIM"), row("copy", "phone", "DCIM")),
            requestedUris = setOf("copy"),
        )
        val prompt = exactDuplicateTrashPrompt(review)!!
        val dialog = com.mistermikhail.fgallery.ui.duplicateTrashDialogMessage(prompt)
        assertEquals(prompt.message, dialog)
        assertEquals(1, Regex("Общий размер выбранных файлов:").findAll(dialog).count())
        assertTrue(dialog.contains("SHA-256"))
        assertEquals("", com.mistermikhail.fgallery.ui.duplicateTrashDialogMessage(null))
    }

    @Test fun visualCandidateLabelsDoNotMistakeOpaqueContentIdsForFilenames() {
        val label: (String) -> String = {
            com.mistermikhail.fgallery.ui.duplicateVisualSourceLabel(it)
        }
        assertEquals("black.jpg", label("content://media/external/images/black.jpg"))
        assertTrue(label("content://media/external/images/media/1234").contains("content://media"))
        assertFalse(label("content://media/external/images/media/1234") == "1234")
        assertTrue(label("content://provider/document/42").contains("document/42"))
    }

    @Test fun expandedPreviewShowsVerifiedIdentityAndNeverMistakesOpaqueUriForFilename() {
        val original = row("original", "phone", "DCIM")
        val copy = row("copy", "usb", "Pictures")
        val review = planExactDuplicateReview(listOf(original, copy))
        val cards = exactDuplicateReviewCards(review)
        val originalDetails = com.mistermikhail.fgallery.ui.duplicatePreviewDetails("original", cards)
        assertTrue(originalDetails.contains("Защищённая копия"))
        assertTrue(originalDetails.contains("original.jpg"))
        assertTrue(originalDetails.contains("DCIM"))
        val copyDetails = com.mistermikhail.fgallery.ui.duplicatePreviewDetails("copy", cards)
        assertTrue(copyDetails.contains("Точная копия"))
        assertTrue(copyDetails.contains("copy.jpg"))
        assertTrue(copyDetails.contains("Pictures"))
        val opaque = "content://media/external/images/media/1234"
        assertTrue(com.mistermikhail.fgallery.ui.duplicatePreviewDetails(opaque, cards).contains("content://media"))
        assertTrue(review.selectedUris.isEmpty())
        assertNull(exactDuplicateTrashPrompt(review))
    }

    @Test fun duplicateFileIdentityAlwaysShowsNameAndLocationEvenForBlackThumbnails() {
        val identity: (String, String, String) -> String = { name, storage, folder ->
            com.mistermikhail.fgallery.ui.duplicateReviewFileIdentity(name, storage, folder)
        }
        assertEquals("black.jpg · phone / DCIM", identity("black.jpg", "phone", "DCIM"))
        assertEquals("Без имени · Неизвестный носитель / Папка неизвестна", identity("", "", ""))
        assertTrue(identity("copy.jpg", "usb", "Pictures").contains("copy.jpg"))
        assertTrue(identity("copy.jpg", "usb", "Pictures").contains("Pictures"))
    }

    @Test fun compactLocationLabelNeverRepeatsFilenameAndHandlesMissingMetadata() {
        val location: (String, String) -> String = { storage, folder ->
            com.mistermikhail.fgallery.ui.duplicateReviewFileLocation(storage, folder)
        }
        assertEquals("phone / DCIM", location("phone", "DCIM"))
        assertFalse(location("phone", "DCIM").contains("black.jpg"))
        assertEquals("Неизвестный носитель / Папка неизвестна", location("", ""))
        assertEquals("usb / Pictures", location("usb", "Pictures"))
    }

    @Test fun allStorageDefaultClearsOnlyLegacyScopeAndNeverRequiresSelection() {
        val reset: (Set<String>, Set<String>) -> Boolean = { storages, folders ->
            com.mistermikhail.fgallery.ui.duplicateLegacyScopeNeedsReset(storages, folders)
        }
        assertFalse(reset(emptySet(), emptySet()))
        assertTrue(reset(setOf("external_primary"), emptySet()))
        assertTrue(reset(emptySet(), setOf("/storage/emulated/0/DCIM")))
        assertTrue(reset(setOf("usb"), setOf("DCIM")))
    }

    @Test fun collapsedFolderFilterSummaryNeverTreatsExplicitScopeAsAll() {
        val summary: (Int, Set<String>) -> String = { count, selected -> com.mistermikhail.fgallery.ui.duplicateFolderScopeSummary(count, selected) }
        assertEquals("Все папки (12)", summary(12, emptySet()))
        assertEquals("Выбрано папок: 2 из 12", summary(12, setOf("WhatsApp Images", "WhatsApp Video")))
        assertEquals("Выбрано папок: 1 из 0", summary(0, setOf("disconnected-usb")))
        // Missing folders stay explicitly scoped; a collapsed UI must not broaden to all.
        val requested = setOf("disconnected-usb")
        assertEquals(requested, requested.toSet())
    }

    @Test fun visualCandidateStatusNeverSuggestsDeletionAndDistinguishesVerification() {
        val label: (String?) -> String = { com.mistermikhail.fgallery.ui.visualCandidateReviewLabel(it) }
        assertTrue(label(null).contains("Ожидает проверки"))
        assertTrue(label("").contains("Ожидает проверки"))
        val verified = label("Пиксели совпадают после масштабирования")
        assertTrue(verified.contains("Результат проверки"))
        assertTrue(verified.contains("Пиксели совпадают"))
        assertTrue(verified.contains("удаление недоступно"))
        assertFalse(verified.contains("Ожидает проверки"))
    }

    @Test fun fileSizePresentationDoesNotRoundOrOverflowSafetyInputs() {
        val size: (Long) -> String = { com.mistermikhail.fgallery.ui.duplicateReviewFileSize(it) }
        assertEquals("0 Б", size(0))
        assertEquals("1023 Б", size(1023))
        assertEquals("1.0 КБ", size(1024))
        assertEquals("1.0 МБ", size(1048576))
        assertEquals("1.00 ГБ", size(1073741824))
        assertEquals("Размер неизвестен", size(-1))
        assertTrue(size(Long.MAX_VALUE).contains("ГБ"))
    }

    @Test fun lazyDuplicateRowKeysRemainStableAcrossCheckboxToggles() {
        val key: (String, Int, Int) -> String = { hash, group, row ->
            com.mistermikhail.fgallery.ui.duplicateCopyRowKey(hash, group, row)
        }
        assertEquals("copies-abc-0-0", key("abc", 0, 0))
        assertEquals(key("abc", 0, 1), key("abc", 0, 1))
        assertFalse(key("abc", 0, 0) == key("abc", 0, 1))
        assertFalse(key("abc", 0, 0) == key("def", 0, 0))
        assertFalse(key("abc", 0, 0) == key("abc", 1, 0))
    }

    @Test fun twoColumnReviewKeepsOddFinalRowAndNeverAddsThirdColumn() {
        val rows: (Int) -> Int = { com.mistermikhail.fgallery.ui.duplicateReviewRows(it) }
        assertEquals(0, rows(0))
        assertEquals(1, rows(1))
        assertEquals(1, rows(2))
        assertEquals(2, rows(3))
        assertEquals(3, rows(5))
        assertEquals(4, rows(8))
    }

    @Test fun verifiedTrashFeedbackNeverReportsZeroMovedAsSuccess() {
        assertEquals("В корзину перемещено: 2",
            com.mistermikhail.fgallery.ui.exactDuplicateTrashFeedback(2))
        val refused = com.mistermikhail.fgallery.ui.exactDuplicateTrashFeedback(0)
        assertFalse(refused.contains("перемещено"))
        assertTrue(refused.contains("SHA-256"))
    }

    @Test fun folderAndStorageScopeControlsStayLockedDuringVerifiedTrash() {
        assertTrue(com.mistermikhail.fgallery.ui.duplicateScopeEditingEnabled(true, false))
        assertFalse(com.mistermikhail.fgallery.ui.duplicateScopeEditingEnabled(true, true))
        assertFalse(com.mistermikhail.fgallery.ui.duplicateScopeEditingEnabled(false, false))
        assertFalse(com.mistermikhail.fgallery.ui.duplicateScopeEditingEnabled(false, true))
    }

    @Test fun visualVerificationCannotOverlapTrashConfirmation() {
        val canConfirm: (Boolean, Boolean, Boolean, Boolean) -> Boolean =
            { available, trash, visual, prompt ->
                com.mistermikhail.fgallery.ui.duplicateTrashConfirmationEnabled(available, trash, visual, prompt)
            }
        assertTrue(canConfirm(true, false, false, true))
        assertFalse(canConfirm(true, false, true, true))
        assertFalse(canConfirm(true, true, false, true))
        assertFalse(canConfirm(false, false, false, true))
        assertFalse(canConfirm(true, false, false, false))
    }

    @Test fun previewIdentityDoesNotAlterManualSelectionOrProtectedOriginal() {
        val rows = listOf(row("original", "phone", "Camera"), row("copy", "phone", "Camera"))
        val review = planExactDuplicateReview(rows, requestedUris = setOf("copy"))
        val cardsBefore = exactDuplicateReviewCards(review)
        // Preview uses a URI, not a selection or trash callback.
        val previewUri = cardsBefore.single().items.last().uri
        assertEquals("copy", previewUri)
        val cardsAfter = exactDuplicateReviewCards(review)
        assertEquals(cardsBefore, cardsAfter)
        assertEquals(setOf("copy"), review.selectedUris)
        assertTrue(cardsAfter.single().items.first().protectedOriginal)
    }

    @Test fun protectedOriginalNeverDisplaysAsCheckedDuringPendingSelection() {
        val checked: (Boolean, Boolean, Boolean?) -> Boolean = { protected, selected, pending ->
            com.mistermikhail.fgallery.ui.duplicateDisplayedCopyChecked(protected, selected, pending)
        }
        assertFalse(checked(true, false, true))
        assertFalse(checked(true, true, true))
        assertFalse(checked(true, true, null))
        assertFalse(checked(true, false, false))
        assertTrue(checked(false, false, true))
        assertTrue(checked(false, true, null))
        assertFalse(checked(false, true, false))
    }

    @Test fun displayedSelectionCountUsesOnlyAuthoritativeSelectionAndPendingOverrides() {
        val count: (Set<String>, Map<String, Boolean>) -> Int = { selected, pending ->
            com.mistermikhail.fgallery.ui.duplicateDisplayedSelectionCount(selected, pending)
        }
        assertEquals(0, count(emptySet(), emptyMap()))
        assertEquals(2, count(setOf("a", "b"), emptyMap()))
        assertEquals(3, count(setOf("a", "b"), mapOf("c" to true)))
        assertEquals(1, count(setOf("a", "b"), mapOf("a" to false)))
        assertEquals(2, count(setOf("a", "b"), mapOf("a" to false, "c" to true)))
        assertEquals(2, count(setOf("a", "b"), mapOf("a" to true, "c" to false)))
        assertEquals(0, count(setOf("a"), mapOf("a" to false)))
        // Repeated tapping never double-counts an already authoritative selection.
        assertEquals(1, count(setOf("a"), mapOf("a" to true)))
    }

    @Test fun confirmationConsentIsBoundToExactCatalogueSnapshot() {
        val initialRows = listOf(row("original", "phone", "Camera"), row("copy", "phone", "Camera"))
        val snapshot = planExactDuplicateReview(initialRows, requestedUris = setOf("copy"))
        val current: (ExactDuplicateReview?, ExactDuplicateReview) -> Boolean = { old, now ->
            com.mistermikhail.fgallery.ui.duplicateConfirmationSnapshotCurrent(old, now)
        }
        assertFalse(current(null, snapshot))
        assertTrue(current(snapshot, snapshot))
        assertFalse(current(snapshot, snapshot.copy(selectedUris = emptySet())))
        assertFalse(current(snapshot, snapshot.copy(includedFolderTargets = setOf("Camera"))))
        val modifiedRows = initialRows.map {
            if (it.uri == "copy") it.copy(dateModifiedMillis = 9) else it
        }
        val rebuilt = planExactDuplicateReview(modifiedRows, requestedUris = setOf("copy"))
        assertFalse(current(snapshot, rebuilt))
    }

    @Test fun footerAndPrimaryTrashConsentMustUseTheSameCurrentReviewSnapshot() {
        val rows = listOf(row("original", "phone", "Camera"), row("copy", "phone", "Camera"))
        val review = planExactDuplicateReview(rows, requestedUris = setOf("copy"))
        val snapshot = review // Both entry points must capture this when opening consent.
        assertTrue(com.mistermikhail.fgallery.ui.duplicateConfirmationSnapshotCurrent(snapshot, review))
        assertTrue(exactDuplicateTrashPrompt(review) != null)

        // A footer action that only sets confirmOpen without capturing a snapshot
        // would fail this contract and leave the user with an inert button.
        assertFalse(com.mistermikhail.fgallery.ui.duplicateConfirmationSnapshotCurrent(null, review))
        val changed = review.copy(selectedUris = emptySet())
        assertFalse(com.mistermikhail.fgallery.ui.duplicateConfirmationSnapshotCurrent(snapshot, changed))
        assertNull(exactDuplicateTrashPrompt(changed))
    }

    @Test fun alreadyOpenTrashDialogIsInvalidatedWhenScopeResetBegins() {
        val canShowOrSubmit: (Boolean, Boolean, Boolean, Boolean, Boolean, Int, Boolean) -> Boolean =
            { verified, scope, trash, visual, prompt, pending, resetting ->
                com.mistermikhail.fgallery.ui.duplicateTrashActionEnabled(
                    verified, scope, trash, visual, prompt, pending, resetting)
            }
        // Confirmation may have opened for the old restricted catalogue.
        assertTrue(canShowOrSubmit(true, true, false, false, true, 0, false))
        // Before the new all-storage catalogue arrives, neither the dialog nor
        // its confirm callback may submit the stale review.
        assertFalse(canShowOrSubmit(true, true, false, false, true, 0, true))
        assertFalse(canShowOrSubmit(true, true, false, false, true, 1, true))
        assertFalse(canShowOrSubmit(true, true, true, false, true, 0, false))
        assertFalse(canShowOrSubmit(true, true, false, true, true, 0, false))
        assertTrue(canShowOrSubmit(true, true, false, false, true, 0, false))
    }

    @Test fun legacyScopeResetCannotAuthorizeTrashBeforeAllStorageReviewIsRebuilt() {
        val action: (Boolean, Boolean, Boolean, Boolean, Boolean, Int, Boolean) -> Boolean =
            { verified, scope, trash, visual, prompt, pending, reset ->
                com.mistermikhail.fgallery.ui.duplicateTrashActionEnabled(
                    verified, scope, trash, visual, prompt, pending, reset)
            }
        // A valid old-scope prompt is not consent for a widened all-storage review.
        assertFalse(action(true, true, false, false, true, 0, true))
        assertFalse(action(true, true, false, false, true, 1, true))
        assertTrue(action(true, true, false, false, true, 0, false))
        assertTrue(com.mistermikhail.fgallery.ui.duplicateLegacyScopeNeedsReset(
            setOf("usb"), emptySet()))
    }

    @Test fun pendingSelectionBlocksBothTrashButtonsUntilHostAcknowledges() {
        val enabled: (Boolean, Boolean, Boolean, Boolean, Boolean, Int) -> Boolean = { verified, scope, trash, visual, prompt, pending ->
            com.mistermikhail.fgallery.ui.duplicateTrashActionEnabled(verified, scope, trash, visual, prompt, pending)
        }
        assertTrue(enabled(true, true, false, false, true, 0))
        assertFalse(enabled(true, true, false, false, true, 1))
        assertFalse(enabled(true, true, false, false, true, 3))
        assertFalse(enabled(false, true, false, false, true, 0))
        assertFalse(enabled(true, false, false, false, true, 0))
        assertFalse(enabled(true, true, true, false, true, 0))
        assertFalse(enabled(true, true, false, true, true, 0))
        assertFalse(enabled(true, true, false, false, false, 0))
    }

    @Test fun pendingSelectionMustNotAuthorizeTrashBeforeHostAcknowledgement() {
        val rows = listOf(row("original", "phone", "Camera"), row("copy", "phone", "Camera"))
        val review = planExactDuplicateReview(rows)
        assertNull(exactDuplicateTrashPrompt(review))
        // Optimistic checkbox rendering is UI-only; the verified host review remains unchanged.
        val pending = mapOf("copy" to true)
        assertTrue(pending["copy"] == true)
        assertTrue(review.selectedUris.isEmpty())
        assertNull(exactDuplicateTrashPrompt(review))
    }

    @Test fun manyIndependentGroupsRetainCorrectSummaryAndProtectedKeeper() {
        val rows = (0 until 60).flatMap { group ->
            val hash = group.toString(16).padStart(64, '0')
            listOf(
                row("original-$group", "phone", "Camera").copy(fullHash = hash),
                row("copy-$group", "phone", "Camera").copy(fullHash = hash),
            )
        }
        val review = planExactDuplicateReview(rows)
        val cards = exactDuplicateReviewCards(review)
        assertEquals(60, cards.size)
        assertEquals(120, cards.sumOf { it.items.size })
        assertTrue(cards.all { it.items.count { item -> item.protectedOriginal } == 1 })
        assertTrue(cards.all { it.items.none { item -> item.manuallySelected } })
        assertNull(exactDuplicateTrashPrompt(review))
    }

    private fun row(uri: String, storage: String, folder: String) = CatalogMediaRow(
        uri = uri, mediaId = 1, name = "$uri.jpg", mimeType = "image/jpeg",
        kind = "IMAGE", dateTakenMillis = if (uri == "original") 1 else 2, width = 200, height = 200,
        durationMillis = 0, album = folder, relativePath = folder,
        sizeBytes = 100, dateModifiedMillis = 1, storageId = storage,
        storageName = storage, folderTarget = folder, sourcePath = "/test/$uri.jpg",
        quickHash = "edge", fullHash = "a".repeat(64), recycled = false,
    )

    @Test fun changingStorageScopeCannotCarrySelectionOrConfirmation() {
        val rows = listOf(row("original", "phone", "Camera"), row("copy", "phone", "Camera"),
            row("usb", "usb", "Camera"))
        val initial = planExactDuplicateReview(rows, requestedUris = setOf("copy"))
        assertEquals(1, initial.selectedCount)
        val scoped = planExactDuplicateReview(rows, includedStorageIds = setOf("phone"))
        assertTrue(scoped.selectedUris.isEmpty())
        assertNull(exactDuplicateTrashPrompt(scoped))
        assertEquals(2, exactDuplicateReviewCards(scoped).single().items.size)
    }

    @Test fun scopeExcludingOneSideCannotPresentADeletableCopy() {
        val rows = listOf(row("original", "phone", "Camera"), row("copy", "usb", "Downloads"))
        val review = planExactDuplicateReview(rows, includedFolderTargets = setOf("Camera"),
            requestedUris = setOf("copy"))
        assertTrue(exactDuplicateReviewCards(review).isEmpty())
        assertTrue(review.selectedUris.isEmpty())
        assertNull(exactDuplicateTrashPrompt(review))
    }

    @Test fun protectedOriginalRemainsUnselectableAndCopiesRequireManualOptIn() {
        val rows = listOf(row("original", "phone", "Camera"), row("copy", "phone", "Camera"))
        val review = planExactDuplicateReview(rows)
        assertFalse(exactDuplicateReviewCards(review).single().items[0].manuallySelected)
        val attempted = selectExactDuplicateCopy(review, rows, "original", true)
        assertTrue(attempted.selectedUris.isEmpty())
        val chosen = selectExactDuplicateCopy(attempted, rows, "copy", true)
        assertEquals(setOf("copy"), chosen.selectedUris)
        assertTrue(exactDuplicateTrashPrompt(chosen)!!.message.contains("SHA-256"))
    }

    @Test fun disconnectedSafScopeBlocksTrashUntilSourceReturns() {
        val rows = listOf(row("original", "usb", "Camera"), row("copy", "usb", "Camera"))
        val review = planExactDuplicateReview(rows, includedStorageIds = setOf("usb"),
            includedFolderTargets = setOf("Camera"), requestedUris = setOf("copy"))
        assertEquals(1, review.selectedCount)
        assertTrue(duplicateReviewScopeAvailable(review, listOf("usb"), listOf("Camera")))
        assertFalse(duplicateReviewScopeAvailable(review, listOf("phone"), listOf("Camera")))
        assertFalse(duplicateReviewScopeAvailable(review, listOf("usb"), emptyList()))
    }
    @Test fun distinctUrisAliasingOnePhysicalFileCannotBeTrashed() {
        val original = row("original", "phone", "Camera")
        val alias = row("copy", "phone", "Camera").copy(sourcePath = original.sourcePath)
        val review = planExactDuplicateReview(listOf(original, alias),
            requestedUris = setOf("copy"))
        assertTrue(review.groups.isEmpty())
        assertTrue(review.selectedUris.isEmpty())
        assertNull(exactDuplicateTrashPrompt(review))
        assertTrue(verifiedExactDuplicateUris(review, listOf(original, alias),
            explicitlyConfirmed = true, readCurrentSha256 = { "a".repeat(64) }).isEmpty())
    }

    @Test fun offScopePhysicalAliasAlsoBlocksTrash() {
        val original = row("original", "phone", "Camera")
        val copy = row("copy", "phone", "Camera")
        val alias = row("alias", "usb", "Other").copy(sourcePath = original.sourcePath)
        val review = planExactDuplicateReview(listOf(original, copy, alias),
            includedStorageIds = setOf("phone"), requestedUris = setOf("copy"))
        assertTrue(review.groups.isEmpty())
        assertTrue(review.selectedUris.isEmpty())
    }

    @Test fun partiallyDisconnectedScopeCannotBeEditedIntoAnImplicitAllScope() {
        val rows = listOf(row("original", "usb", "Camera"), row("copy", "usb", "Camera"))
        val review = planExactDuplicateReview(rows, includedStorageIds = setOf("usb"),
            includedFolderTargets = setOf("Camera"), requestedUris = setOf("copy"))
        assertFalse(duplicateReviewScopeAvailable(review, listOf("phone"), listOf("Camera")))
        // The remaining phone checkbox must not erase the missing USB scope.
        val unchanged = toggleDuplicateScope(listOf("phone"), review.includedStorageIds,
            "phone", checked = true)
        assertEquals(setOf("usb"), unchanged)
        assertFalse(duplicateReviewScopeAvailable(review, listOf("phone"), listOf("Camera")))
        assertTrue(duplicateReviewScopeAvailable(review,
            listOf("phone", "usb"), listOf("Camera")))
    }

    @Test fun disconnectedSourceRevokesConfirmationAndSelectionResetRequiresNewConsent() {
        val rows = listOf(row("original", "usb", "Camera"), row("copy", "usb", "Camera"))
        val review = planExactDuplicateReview(rows, includedStorageIds = setOf("usb"),
            requestedUris = setOf("copy"))
        assertFalse(duplicateConfirmationInvalidated(review, listOf("usb"), listOf("Camera")))
        assertTrue(duplicateConfirmationInvalidated(review, listOf("phone"), listOf("Camera")))
        val cleared = planExactDuplicateReview(rows, includedStorageIds = setOf("usb"))
        assertTrue(duplicateConfirmationInvalidated(cleared, listOf("usb"), listOf("Camera")))
    }

    @Test fun editingScopeOrCopySelectionRequiresFreshTrashConsent() {
        val rows = listOf(row("original", "phone", "Camera"), row("copy", "phone", "Camera"))
        val reviewed = planExactDuplicateReview(rows, requestedUris = setOf("copy"))
        assertEquals(setOf("copy"), confirmedExactDuplicateUris(reviewed, rows,
            explicitlyConfirmed = true))
        // A scope edit cannot reuse the previous dialog's approval.
        assertTrue(confirmedExactDuplicateUris(reviewed, rows,
            includedStorageIds = setOf("phone"), explicitlyConfirmed = true).isEmpty())
        // Unchecking a copy clears the proposal, even if a dialog was open.
        val deselected = selectExactDuplicateCopy(reviewed, rows, "copy", false)
        assertTrue(deselected.selectedUris.isEmpty())
        assertNull(exactDuplicateTrashPrompt(deselected))
        assertTrue(confirmedExactDuplicateUris(deselected, rows,
            explicitlyConfirmed = true).isEmpty())
    }

    @Test fun malformedSelectionIncludingProtectedOriginalCannotOpenTrashPrompt() {
        val rows = listOf(row("original", "phone", "Camera"), row("copy", "phone", "Camera"))
        val safe = planExactDuplicateReview(rows, requestedUris = setOf("copy"))
        assertEquals(1, exactDuplicateTrashPrompt(safe)!!.selectedCopyCount)
        val malformed = safe.copy(selectedUris = setOf("original", "copy"))
        assertNull(exactDuplicateTrashPrompt(malformed))
        // A malformed selection invalidates the whole review presentation.
        // No card or actionable selection may survive a protected-original violation.
        assertTrue(exactDuplicateReviewCards(malformed).isEmpty())
    }

    @Test fun confirmationSnapshotCannotAuthorizeChangedCatalogueGeneration() {
        val rows = listOf(row("original", "phone", "Camera"), row("copy", "phone", "Camera"))
        val dialogSnapshot = planExactDuplicateReview(rows, requestedUris = setOf("copy"))
        assertEquals(setOf("copy"), confirmedExactDuplicateUris(dialogSnapshot, rows,
            explicitlyConfirmed = true))
        // The UI must hand this immutable snapshot to verification. A later
        // catalogue generation cannot inherit the earlier dialog's consent.
        val replaced = rows.map { if (it.uri == "copy") it.copy(dateModifiedMillis = 2) else it }
        assertTrue(confirmedExactDuplicateUris(dialogSnapshot, replaced,
            explicitlyConfirmed = true).isEmpty())
        val rescanned = planExactDuplicateReview(replaced, requestedUris = setOf("copy"))
        assertFalse(dialogSnapshot.groups == rescanned.groups)
    }

    @Test fun explicitScopeResetAfterSafDisconnectDoesNotInheritTrashConsent() {
        val rows = listOf(row("original", "usb", "Camera"), row("copy", "usb", "Camera"))
        val stale = planExactDuplicateReview(rows, includedStorageIds = setOf("usb"),
            includedFolderTargets = setOf("Camera"), requestedUris = setOf("copy"))
        assertFalse(duplicateReviewScopeAvailable(stale, listOf("phone"), listOf("Camera")))
        // UI reset explicitly passes empty scopes. Host must rebuild without
        // requestedUris, so even a reconnected source cannot inherit selection.
        val reset = planExactDuplicateReview(rows,
            includedStorageIds = emptySet(), includedFolderTargets = emptySet())
        assertTrue(reset.selectedUris.isEmpty())
        assertNull(exactDuplicateTrashPrompt(reset))
        assertTrue(confirmedExactDuplicateUris(stale, rows,
            includedStorageIds = reset.includedStorageIds,
            includedFolderTargets = reset.includedFolderTargets,
            explicitlyConfirmed = true).isEmpty())
    }


    @Test fun explicitCatalogueRefreshMustRevokeOldConsentWhenFileGenerationChanges() {
        val original = row("original", "phone", "Camera")
        val copy = row("copy", "phone", "Camera")
        val oldRows = listOf(original, copy)
        val confirmed = planExactDuplicateReview(oldRows, requestedUris = setOf("copy"))
        assertEquals(setOf("copy"), confirmedExactDuplicateUris(
            confirmed, oldRows, explicitlyConfirmed = true))
        val refreshed = listOf(original, copy.copy(dateModifiedMillis = 2))
        // A refresh is a new catalogue generation. The old dialog cannot authorize it.
        assertTrue(confirmedExactDuplicateUris(
            confirmed, refreshed, explicitlyConfirmed = true).isEmpty())
        val freshReview = planExactDuplicateReview(refreshed)
        assertTrue(freshReview.selectedUris.isEmpty())
        assertNull(exactDuplicateTrashPrompt(freshReview))
    }
}

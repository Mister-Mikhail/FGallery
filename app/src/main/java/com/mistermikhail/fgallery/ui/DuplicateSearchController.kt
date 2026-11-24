package com.mistermikhail.fgallery.ui

import android.net.Uri
import com.mistermikhail.fgallery.data.CatalogMediaRow
import com.mistermikhail.fgallery.data.DuplicateSearchInput
import com.mistermikhail.fgallery.data.ExactDuplicateReview
import com.mistermikhail.fgallery.data.ExactDuplicateReviewCard
import com.mistermikhail.fgallery.data.ExactDuplicateTrashPrompt
import com.mistermikhail.fgallery.data.ExternalMediaKind
import com.mistermikhail.fgallery.data.MediaItem
import com.mistermikhail.fgallery.data.MediaKind
import com.mistermikhail.fgallery.data.VisualDuplicatePixelVerification
import com.mistermikhail.fgallery.data.exactDuplicateReviewCards
import com.mistermikhail.fgallery.data.exactDuplicateTrashPrompt
import com.mistermikhail.fgallery.data.planExactDuplicateReview
import kotlinx.coroutines.ensureActive
import com.mistermikhail.fgallery.data.visualReviewGroups
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class DuplicateTab { EXACT, VISUAL }

internal class DuplicateCatalogue(input: DuplicateSearchInput) {
    val review = planExactDuplicateReview(input.rows)
    val cards = exactDuplicateReviewCards(review)
    val cardsByHash = cards.associateBy { it.sha256 }
    val valid = review.catalogueValid && cards.size == review.groups.size
    val rows = input.rows.associateBy { it.uri }
    val selectableUris = if (valid) cards.flatMap { card ->
        card.items.filterNot { it.protectedOriginal }.map { it.uri }
    }.toHashSet() else emptySet()
    val visualRows = input.rows.filter { it.kind == MediaKind.IMAGE.name && !it.recycled }
    val duplicateFileCount = cards.sumOf { it.items.size }
    val reclaimableBytes = try {
        cards.fold(0L) { total, card ->
            Math.addExact(total, Math.multiplyExact(card.sizeBytes, card.items.lastIndex.toLong()))
        }
    } catch (_: ArithmeticException) {
        null
    }
    val disconnectedStorages = input.disconnectedStorages
    val pendingFingerprints = input.pendingFingerprints
    val requiresAccess = input.requiresAccess
}

internal data class DuplicateConfirmation(
    val catalogue: DuplicateCatalogue,
    val review: ExactDuplicateReview,
    val prompt: ExactDuplicateTrashPrompt,
)

internal data class ComparisonTrashConfirmation(val row: CatalogMediaRow, val sha256: String)

internal data class DuplicateSearchUiState(
    val catalogue: DuplicateCatalogue? = null,
    val tab: DuplicateTab = DuplicateTab.EXACT,
    val group: ExactDuplicateReviewCard? = null,
    val selected: Set<String> = emptySet(),
    val scanProgress: String? = "Загрузка каталога…",
    val error: String? = null,
    val notice: String? = null,
    val preview: MediaItem? = null,
    val visualGroups: List<List<String>>? = null,
    val visualError: String? = null,
    val visualComparison: Pair<String, String>? = null,
    val visualStatuses: Map<Pair<String, String>, String> = emptyMap(),
    val visualChecking: Pair<String, String>? = null,
    val comparisonCleanup: Boolean = false,
    val comparisonError: String? = null,
    val comparisonTrashConfirmation: ComparisonTrashConfirmation? = null,
    val preparingConfirmation: Boolean = false,
    val confirmation: DuplicateConfirmation? = null,
    val trashInProgress: Boolean = false,
)

internal class DuplicateTrashInterrupted(val completed: Int, cause: Exception) : Exception(cause.message, cause)

/** One catalogue snapshot per scan. UI events address indexed rows and do not touch files. */
internal class DuplicateSearchController(
    private val scope: CoroutineScope,
    private val load: suspend (Boolean, (String) -> Unit) -> DuplicateSearchInput,
    private val verifyVisual: suspend (CatalogMediaRow, CatalogMediaRow) -> VisualDuplicatePixelVerification.Result,
    private val trash: suspend (ExactDuplicateReview) -> Int,
    private val prepareComparisonTrash: suspend (CatalogMediaRow) -> String,
    private val trashComparedImage: suspend (ComparisonTrashConfirmation) -> Unit,
    private val prepareVisual: suspend (List<CatalogMediaRow>) -> List<CatalogMediaRow> = { it },
) {
    private val _state = MutableStateFlow(DuplicateSearchUiState())
    val state = _state.asStateFlow()
    private var scan: Job? = null
    private var visualJob: Job? = null
    private var verificationJob: Job? = null
    private var confirmationJob: Job? = null

    fun close() {
        if (_state.value.trashInProgress) return
        scan?.cancel()
        visualJob?.cancel()
        verificationJob?.cancel()
        confirmationJob?.cancel()
        _state.value = DuplicateSearchUiState()
    }

    fun refresh(force: Boolean = true, notice: String? = null) {
        val current = _state.value
        if (current.trashInProgress) return
        close()
        _state.value = DuplicateSearchUiState(tab = current.tab, notice = notice)
        scan = scope.launch {
            try {
                val input = load(force) { progress -> _state.value = _state.value.copy(scanProgress = progress) }
                val catalogue = withContext(Dispatchers.IO) { DuplicateCatalogue(input) }
                _state.value = _state.value.copy(catalogue = catalogue, scanProgress = null)
                if (_state.value.tab == DuplicateTab.VISUAL) loadVisualGroups()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.value = _state.value.copy(
                    scanProgress = null,
                    error = failure.localizedMessage ?: "Не удалось прочитать каталог.",
                )
            }
        }
    }

    fun showTab(tab: DuplicateTab) {
        val current = _state.value
        if (current.trashInProgress || current.preparingConfirmation || current.tab == tab) return
        _state.value = current.copy(tab = tab, group = null, confirmation = null, visualComparison = null,
            preview = null, comparisonCleanup = false, comparisonError = null, comparisonTrashConfirmation = null)
        if (tab == DuplicateTab.VISUAL && current.visualGroups == null) loadVisualGroups()
    }

    fun showGroup(card: ExactDuplicateReviewCard?) {
        val current = _state.value
        if (current.trashInProgress || current.preparingConfirmation ||
            (card != null && current.catalogue?.cardsByHash?.get(card.sha256) !== card)
        ) return
        _state.value = current.copy(group = card, confirmation = null)
    }

    fun setSelected(uri: String, checked: Boolean) {
        val current = _state.value
        if (current.trashInProgress || current.preparingConfirmation || current.preview != null ||
            current.tab != DuplicateTab.EXACT || current.catalogue?.selectableUris?.contains(uri) != true
        ) return
        val selected = if (checked) current.selected + uri else current.selected - uri
        _state.value = current.copy(selected = selected, confirmation = null)
    }

    fun selectCopies(cards: List<ExactDuplicateReviewCard>) {
        val current = _state.value
        val catalogue = current.catalogue ?: return
        if (!catalogue.valid || current.tab != DuplicateTab.EXACT || current.trashInProgress ||
            current.preparingConfirmation || current.confirmation != null || current.preview != null) return
        val selected = cards.asSequence().filter { catalogue.cardsByHash[it.sha256] === it }
            .flatMap { it.items.asSequence() }.map { it.uri }.filter { it in catalogue.selectableUris }.toSet()
        _state.value = current.copy(selected = selected)
    }

    fun clearSelection() {
        val current = _state.value
        if (!current.trashInProgress && !current.preparingConfirmation) {
            _state.value = current.copy(selected = emptySet(), confirmation = null)
        }
    }

    fun openPreview(uri: String) {
        val current = _state.value
        if (current.trashInProgress || current.preparingConfirmation || current.confirmation != null) return
        val media = current.catalogue?.rows?.get(uri)?.toDuplicateMediaItem()
        _state.value = current.copy(
            preview = media,
            comparisonCleanup = false,
            comparisonError = null,
            comparisonTrashConfirmation = null,
            notice = if (media == null) "Этот тип файла нельзя открыть в просмотрщике." else current.notice,
        )
    }

    fun closePreview() {
        val current = _state.value
        if (current.trashInProgress || current.preparingConfirmation) return
        _state.value = current.copy(preview = null, comparisonCleanup = false, comparisonError = null,
            comparisonTrashConfirmation = null)
    }

    fun setComparisonCleanup(enabled: Boolean) {
        val current = _state.value
        if (current.preview == null || current.visualComparison == null || current.trashInProgress ||
            current.preparingConfirmation || current.comparisonTrashConfirmation != null) return
        _state.value = current.copy(comparisonCleanup = enabled, comparisonError = null)
    }

    fun requestComparisonTrash(uri: String) {
        val current = _state.value
        val pair = current.visualComparison ?: return
        val catalogue = current.catalogue ?: return
        val row = catalogue.rows[uri] ?: return
        if (!current.comparisonCleanup || current.preview == null || current.tab != DuplicateTab.VISUAL ||
            current.trashInProgress || current.preparingConfirmation || current.comparisonTrashConfirmation != null ||
            (uri != pair.first && uri != pair.second) || row.kind != MediaKind.IMAGE.name) return
        if (uri in catalogue.review.protectedUris) {
            _state.value = current.copy(comparisonError = "Этот снимок помечен «Сохранить» в точных копиях.")
            return
        }

        _state.value = current.copy(preparingConfirmation = true, comparisonError = null)
        confirmationJob = scope.launch {
            try {
                val sha256 = withContext(Dispatchers.IO) { prepareComparisonTrash(row) }
                _state.value = _state.value.copy(
                    preparingConfirmation = false,
                    comparisonTrashConfirmation = ComparisonTrashConfirmation(row, sha256),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.value = _state.value.copy(preparingConfirmation = false,
                    comparisonError = failure.localizedMessage ?: "Не удалось проверить исходный файл.")
            }
        }
    }

    fun dismissComparisonTrash() {
        _state.value = _state.value.copy(comparisonTrashConfirmation = null)
    }

    fun confirmComparisonTrash(consent: ComparisonTrashConfirmation) {
        val current = _state.value
        if (current.comparisonTrashConfirmation !== consent || !current.comparisonCleanup ||
            current.preview == null || current.trashInProgress) return

        _state.value = current.copy(comparisonTrashConfirmation = null, trashInProgress = true)
        scope.launch {
            val notice = try {
                trashComparedImage(consent)
                "${consent.row.name} перемещён в корзину."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                "Перенос в корзину не подтверждён. ${failure.localizedMessage.orEmpty()}"
            } finally {
                _state.value = _state.value.copy(trashInProgress = false)
            }
            refresh(force = false, notice = notice)
        }
    }

    fun loadVisualGroups() {
        val current = _state.value
        val catalogue = current.catalogue ?: return
        if (current.tab != DuplicateTab.VISUAL || visualJob?.isActive == true) return
        _state.value = current.copy(visualError = null)
        visualJob = scope.launch {
            try {
                val rows = prepareVisual(catalogue.visualRows)
                val groups = withContext(Dispatchers.Default) { visualReviewGroups(rows) { ensureActive() } }
                _state.value = _state.value.copy(visualGroups = groups)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(visualError = "Не удалось сравнить изображения.")
            }
        }
    }

    fun openVisualComparison(pair: Pair<String, String>) {
        val current = _state.value
        if (current.tab != DuplicateTab.VISUAL || current.preview != null || pair.first == pair.second ||
            current.visualGroups?.any { pair.first in it && pair.second in it } != true
        ) return
        _state.value = current.copy(visualComparison = pair)
    }

    fun closeVisualComparison() {
        _state.value = _state.value.copy(visualComparison = null)
    }

    fun checkVisual() {
        val current = _state.value
        val pair = current.visualComparison ?: return
        if (current.visualChecking != null || current.preview != null) return
        val rows = current.catalogue?.rows ?: return
        val first = rows[pair.first] ?: return
        val second = rows[pair.second] ?: return
        _state.value = current.copy(visualChecking = pair)
        verificationJob = scope.launch {
            val label = try {
                when (withContext(Dispatchers.IO) { verifyVisual(first, second) }) {
                    VisualDuplicatePixelVerification.Result.EXACT_PIXELS -> "Подтверждено · пиксели совпадают"
                    VisualDuplicatePixelVerification.Result.EXACT_CROP -> "Подтверждено · точное кадрирование"
                    VisualDuplicatePixelVerification.Result.EXACT_INTEGER_RESIZE -> "Подтверждено · точное масштабирование"
                    else -> "Не подтверждено"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                "Не подтверждено · исходный файл недоступен"
            }
            val latest = _state.value
            _state.value = latest.copy(visualChecking = null, visualStatuses = latest.visualStatuses + (pair to label))
        }
    }

    fun requestConfirmation() {
        val current = _state.value
        val catalogue = current.catalogue ?: return
        if (current.tab != DuplicateTab.EXACT || current.preview != null || current.selected.isEmpty() ||
            current.preparingConfirmation || current.trashInProgress || !catalogue.valid
        ) return
        val review = catalogue.review.copy(selectedUris = current.selected)
        _state.value = current.copy(preparingConfirmation = true)
        confirmationJob = scope.launch {
            val prompt = withContext(Dispatchers.IO) { exactDuplicateTrashPrompt(review) }
            val latest = _state.value
            if (latest.catalogue !== catalogue || latest.selected != review.selectedUris) return@launch
            _state.value = latest.copy(
                preparingConfirmation = false,
                confirmation = prompt?.let { DuplicateConfirmation(catalogue, review, it) },
                notice = if (prompt == null) "Список изменился. Обновите поиск и отметьте копии заново." else latest.notice,
            )
        }
    }

    fun dismissConfirmation() {
        _state.value = _state.value.copy(confirmation = null)
    }

    fun confirmTrash(consent: DuplicateConfirmation) {
        val current = _state.value
        if (current.confirmation !== consent || current.catalogue !== consent.catalogue ||
            current.selected != consent.review.selectedUris || current.trashInProgress ||
            current.preview != null || current.tab != DuplicateTab.EXACT
        ) return
        _state.value = current.copy(confirmation = null, trashInProgress = true)
        scope.launch {
            val notice = try {
                "В корзину перемещено: ${trash(consent.review)} из ${consent.prompt.selectedCopyCount}."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: DuplicateTrashInterrupted) {
                "Подтверждено переносов: ${failure.completed} из ${consent.prompt.selectedCopyCount}. " +
                    "Операция остановлена. Проверьте корзину. ${failure.localizedMessage.orEmpty()}"
            } catch (failure: Exception) {
                "Файлы не перемещены. ${failure.localizedMessage ?: "Проверка не пройдена."}"
            } finally {
                _state.value = _state.value.copy(trashInProgress = false)
            }
            refresh(force = false, notice = notice)
        }
    }
}

internal fun CatalogMediaRow.toDuplicateMediaItem(): MediaItem? {
    val mediaKind = ExternalMediaKind.classify(name, mimeType)
        ?: MediaKind.entries.firstOrNull { it.name == kind }
        ?: return null
    return MediaItem(
        mediaId, Uri.parse(uri), name, mimeType, mediaKind, dateTakenMillis, width, height,
        durationMillis, album, relativePath, sizeBytes, dateModifiedMillis, storageId,
        storageName, folderTarget, sourcePath,
    )
}

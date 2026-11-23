package com.mistermikhail.fgallery.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mistermikhail.fgallery.data.ExactDuplicateReview
import com.mistermikhail.fgallery.data.ExactDuplicateReviewCard
import com.mistermikhail.fgallery.data.exactDuplicateReviewCards
import com.mistermikhail.fgallery.data.exactDuplicateTrashPrompt
import com.mistermikhail.fgallery.data.duplicateReviewScopeAvailable
import com.mistermikhail.fgallery.data.duplicateConfirmationInvalidated

/**
 * Stage 6 review surface. The host owns the verified trash transaction;
 * controls are locked while it is running to prevent overlapping requests.
 * onRequestTrashVerification is an INTENT, not permission to move files.
 */
internal fun duplicateScopeEditingEnabled(scopeAvailable: Boolean, trashInProgress: Boolean): Boolean =
    scopeAvailable && !trashInProgress

internal fun duplicateTrashConfirmationEnabled(scopeAvailable: Boolean, trashInProgress: Boolean, visualInProgress: Boolean, hasPrompt: Boolean): Boolean =
    scopeAvailable && !trashInProgress && !visualInProgress && hasPrompt

/** Stable lazy-list identity: never key a row by its current selection state. */
internal fun duplicateCopyRowKey(sha256: String, groupIndex: Int, rowIndex: Int): String =
    "copies-$sha256-$groupIndex-$rowIndex"

/** Fixed two-column portrait review: one item in a final row retains half-width. */
internal fun duplicateReviewRows(itemCount: Int): Int =
    if (itemCount <= 0) 0 else (itemCount / 2) + (itemCount % 2)

/** Presentation only: byte counts never determine deletion safety. */
internal fun duplicateReviewFileIdentity(name: String, storage: String, folder: String): String =
    "${name.ifBlank { "Без имени" }} · ${storage.ifBlank { "Неизвестный носитель" }} / ${folder.ifBlank { "Папка неизвестна" }}"

/** Short location line displayed below the file name, without duplicating it. */
internal fun duplicateReviewFileLocation(storage: String, folder: String): String =
    "${storage.ifBlank { "Неизвестный носитель" }} / ${folder.ifBlank { "Папка неизвестна" }}"

internal fun duplicateReviewFileSize(bytes: Long): String {
    if (bytes < 0L) return "Размер неизвестен"
    if (bytes < 1024L) return "$bytes Б"
    val kib = bytes.toDouble() / 1024.0
    if (kib < 1024.0) return String.format(java.util.Locale.ROOT, "%.1f КБ", kib)
    val mib = kib / 1024.0
    if (mib < 1024.0) return String.format(java.util.Locale.ROOT, "%.1f МБ", mib)
    return String.format(java.util.Locale.ROOT, "%.2f ГБ", mib / 1024.0)
}

/** A content URI's final segment can be an opaque ID, not a filename. */
internal fun duplicateVisualSourceLabel(uri: String): String {
    val segment = uri.substringBefore('?').substringAfterLast('/').trim()
    val decoded = segment
    return if (decoded.isNotBlank() && decoded.contains('.') &&
        decoded.none { it == '/' || it == '\u0000' }) decoded
    else "Источник: " + uri.substringBefore('?').take(90)
}

/** Expanded preview uses verified catalogue metadata, never opaque URI segments. */
internal fun duplicatePreviewDetails(uri: String, cards: List<ExactDuplicateReviewCard>): String {
    val item = cards.asSequence().flatMap { it.items.asSequence() }
        .firstOrNull { it.uri == uri } ?: return duplicateVisualSourceLabel(uri)
    val role = if (item.protectedOriginal) "Защищённая копия" else "Точная копия"
    return "$role · " + duplicateReviewFileIdentity(item.displayName, item.storageId, item.folderTarget)
}

/** A rejected restored catalogue is not evidence that the scan found zero copies. */
internal fun duplicateExactResultsMessage(groupCount: Int, visibleCardCount: Int): String =
    if (groupCount > 0 && visibleCardCount == 0) {
        "Результаты устарели или повреждены. Выбор копий заблокирован. Обновите поиск перед продолжением."
    } else {
        "Точных совпадений в доступной памяти не найдено"
    }

/** Candidate status is informative only; never authorizes trash. */
internal fun visualCandidateReviewLabel(status: String?): String =
    if (status.isNullOrBlank()) {
        "Ожидает проверки исходных пикселей · удаление недоступно"
    } else {
        "Результат проверки: $status · удаление недоступно"
    }

/** A collapsed filter must never silently change the explicit search scope. */
internal fun duplicateFolderScopeSummary(availableCount: Int, selected: Set<String>): String =
    if (selected.isEmpty()) "Все папки ($availableCount)"
    else "Выбрано папок: ${selected.size} из $availableCount"

/** Legacy filters must not survive the user's all-storage search decision. */
internal fun duplicateLegacyScopeNeedsReset(storages: Set<String>, folders: Set<String>): Boolean =
    storages.isNotEmpty() || folders.isNotEmpty()

/** A newly protected original must never appear checked, even for one frame of pending UI state. */
internal fun duplicateDisplayedCopyChecked(
    protectedOriginal: Boolean,
    authoritativeSelected: Boolean,
    pendingSelected: Boolean?,
): Boolean = !protectedOriginal && (pendingSelected ?: authoritativeSelected)

/** Pending checkbox changes are presentation-only and must block both trash entry points. */
internal fun duplicateTrashActionEnabled(
    verifiedAvailable: Boolean,
    scopeAvailable: Boolean,
    trashInProgress: Boolean,
    visualInProgress: Boolean,
    hasPrompt: Boolean,
    pendingSelectionCount: Int,
    scopeResetPending: Boolean = false,
): Boolean = verifiedAvailable && !scopeResetPending && pendingSelectionCount == 0 &&
    duplicateTrashConfirmationEnabled(scopeAvailable, trashInProgress, visualInProgress, hasPrompt)

/** Only validated cards may contribute to visible selection or optimistic taps. */
internal fun duplicateVisibleReviewUris(cards: List<ExactDuplicateReviewCard>): Set<String> =
    cards.asSequence().flatMap { it.items.asSequence() }.map { it.uri }.toSet()

/** Pending taps remain visible until acknowledged, but cannot outlive their files. */
internal fun duplicatePendingSelectionKeysToDiscard(
    pending: Map<String, Boolean>,
    currentUris: Set<String>,
    selectedUris: Set<String>,
    protectedUris: Set<String> = emptySet(),
): Set<String> = pending.filter { (uri, checked) ->
    uri !in currentUris || uri in protectedUris || checked == (uri in selectedUris)
}.keys

/** O(pending taps), not O(all rendered files), on each checkbox recomposition.
 * This is display-only; verified trash still uses review.selectedUris.
 */
internal fun duplicateDisplayedSelectionCount(
    authoritativeSelectedUris: Set<String>,
    pending: Map<String, Boolean>,
    currentUris: Set<String> = authoritativeSelectedUris + pending.keys,
    protectedUris: Set<String> = emptySet(),
): Int {
    // Compose effects clear stale optimistic taps asynchronously. Never count
    // an inaccessible row or newly protected keeper while that effect runs.
    val eligible = currentUris - protectedUris
    var count = authoritativeSelectedUris.count { it in eligible }
    pending.forEach { (uri, checked) ->
        if (uri !in eligible) return@forEach
        val wasChecked = uri in authoritativeSelectedUris
        if (checked && !wasChecked) count++
        if (!checked && wasChecked) count--
    }
    return count
}

/** The prompt already includes the exact byte count; do not append it twice. */
internal fun duplicateTrashDialogMessage(prompt: com.mistermikhail.fgallery.data.ExactDuplicateTrashPrompt?): String =
    prompt?.message.orEmpty()

/** Consent belongs to the exact catalogue and selection shown when the dialog opened. */
internal fun duplicateConfirmationSnapshotCurrent(
    snapshot: ExactDuplicateReview?,
    current: ExactDuplicateReview,
): Boolean = snapshot != null && snapshot == current

internal fun exactDuplicateTrashFeedback(moved: Int): String =
    if (moved > 0) "В корзину перемещено: $moved"
    else "Файлы изменились или проверка SHA-256 не прошла. Обновите список."

@Composable
internal fun ExactDuplicateReviewScreen(
    review: ExactDuplicateReview,
    availableStorages: List<String>,
    availableFolders: List<String>,
    onScopeChanged: (Set<String>, Set<String>) -> Unit,
    onCopySelectionChanged: (String, Boolean) -> Unit,
    onRequestTrashVerification: (ExactDuplicateReview) -> Unit,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    trashVerificationAvailable: Boolean = false,
    trashInProgress: Boolean = false,
    visualCandidates: List<Pair<String, String>> = emptyList(),
    visualPage: Int = 0,
    visualPageCount: Int = 1,
    onVisualPageChanged: (Int) -> Unit = {},
    visualStatus: Map<String, String> = emptyMap(),
    onVerifyVisual: (String, String) -> Unit = { _, _ -> },
    visualInProgress: Boolean = false,
) {
    var confirmOpen by remember { mutableStateOf(false) }
    var confirmationSnapshot by remember { mutableStateOf<ExactDuplicateReview?>(null) }
    fun openConfirmation() {
        confirmationSnapshot = review
        confirmOpen = true
    }
    var previewUri by remember { mutableStateOf<String?>(null) }
    // Selection feedback is immediate; the host still owns authoritative verification.
    val pendingSelections = remember { mutableStateMapOf<String, Boolean>() }
    val cards = remember(review) { exactDuplicateReviewCards(review) }
    val visibleUris = remember(cards) { duplicateVisibleReviewUris(cards) }
    LaunchedEffect(visibleUris, review.selectedUris, review.protectedUris) {
        // Preserve in-flight taps across catalogue emissions, but never carry
        // selections over to files no longer present in the current review.
        duplicatePendingSelectionKeysToDiscard(pendingSelections, visibleUris, review.selectedUris, review.protectedUris)
            .forEach { pendingSelections.remove(it) }
    }
    // User explicitly selected all accessible storage as the default search scope.
    // Reset legacy filters once; never widen scope while a trash transaction is active.
    LaunchedEffect(review.includedStorageIds, review.includedFolderTargets, trashInProgress, visualInProgress) {
        if (!trashInProgress && !visualInProgress &&
            duplicateLegacyScopeNeedsReset(review.includedStorageIds, review.includedFolderTargets)) {
            confirmOpen = false
            onScopeChanged(emptySet(), emptySet())
        }
    }
    val scopeResetPending = duplicateLegacyScopeNeedsReset(review.includedStorageIds, review.includedFolderTargets)
    val scopeAvailable = duplicateReviewScopeAvailable(review, availableStorages, availableFolders)
    val prompt = if (scopeAvailable && !scopeResetPending && !trashInProgress && !visualInProgress) exactDuplicateTrashPrompt(review) else null
    // A disconnected SAF source invalidates an already-open confirmation.
    // Reconnection must require a fresh tap, not resurrect an old dialog.
    LaunchedEffect(scopeAvailable, prompt, pendingSelections.size) {
        if (pendingSelections.isNotEmpty() || scopeResetPending) confirmOpen = false
        if (duplicateConfirmationInvalidated(review, availableStorages, availableFolders)) confirmOpen = false
    }
    MaterialTheme(colorScheme = darkColorScheme(background = Color.Black, surface = Color(0xFF161616), onBackground = Color(0xFFE0E0E0), onSurface = Color(0xFFE0E0E0))) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "heading") {
            Text("Точные дубликаты", style = MaterialTheme.typography.titleLarge)
        }
        item(key = "description") {
        Text("Сравниваются полные SHA-256. Похожие фотографии и RAW+JPEG не считаются точными копиями.")
        Text("Поиск по всей доступной памяти устройства. Выбор папок временно отключён.", style = MaterialTheme.typography.bodySmall)
        }
        if (!scopeAvailable) {
            item(key = "scope-warning") {
            Text("Выбранный носитель или папка недоступны. Подключите источник и обновите каталог либо явно сбросьте область поиска.")
            OutlinedButton(onClick = {
                // A disconnected explicit scope must never be silently widened.
                // Only an explicit reset clears both filters and pending consent.
                confirmOpen = false
                if (!trashInProgress && !visualInProgress) onScopeChanged(emptySet(), emptySet())
            }, enabled = !trashInProgress && !visualInProgress) { Text("Сбросить область поиска") }
            }
        }
        val selectedNow = duplicateDisplayedSelectionCount(
            review.selectedUris, pendingSelections,
            visibleUris,
            review.protectedUris,
        )
        item(key = "selection-summary") { Text("Выбрано вручную: $selectedNow. Ничего не удаляется автоматически.") }
        if (selectedNow > 0) {
            item(key = "selection-actions") {
            Surface(color = Color(0xFF242424)) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("Выбрано копий: $selectedNow")
                    OutlinedButton(
                        onClick = { openConfirmation() },
                        enabled = duplicateTrashActionEnabled(trashVerificationAvailable, scopeAvailable, trashInProgress,
                            visualInProgress, prompt != null, pendingSelections.size, scopeResetPending),
                    ) { Text("Проверить и переместить в корзину…") }
                    if (pendingSelections.isNotEmpty()) Text("Синхронизация выделения…", style = MaterialTheme.typography.bodySmall)
                }
            }
            }
        }
        item(key = "result-count") { Text("Найдено: ${cards.size} групп · ${cards.sumOf { it.items.size }} файлов", style = MaterialTheme.typography.titleMedium) }
        if (cards.isEmpty()) item(key = "no-exact") { Text(duplicateExactResultsMessage(review.groups.size, cards.size)) }
        cards.forEachIndexed { index, card ->
            item(key = "group-${card.sha256}-$index") {
            HorizontalDivider()
            Text("Группа ${index + 1} · ${card.items.size} файлов · ${duplicateReviewFileSize(card.sizeBytes)} каждый",
                style = MaterialTheme.typography.titleMedium)
            Text("Слева и справа — отдельные файлы. Нажмите на фото для просмотра; выбор не удаляет файл.", style = MaterialTheme.typography.bodySmall)
            Text("SHA-256: ${card.sha256.take(16)}…", style = MaterialTheme.typography.bodySmall)
            }
            card.items.chunked(2).forEachIndexed { rowIndex, pair ->
                item(key = duplicateCopyRowKey(card.sha256, index, rowIndex)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pair.forEach { item ->
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            DuplicatePreview(item.uri, item.displayName) { previewUri = item.uri }
                            Row {
                                Checkbox(
                                    checked = duplicateDisplayedCopyChecked(item.protectedOriginal, item.manuallySelected, pendingSelections[item.uri]),
                                    enabled = !scopeResetPending && duplicateScopeEditingEnabled(scopeAvailable, trashInProgress || visualInProgress) && !item.protectedOriginal,
                                    onCheckedChange = {
                                        confirmOpen = false
                                        pendingSelections[item.uri] = it
                                        onCopySelectionChanged(item.uri, it)
                                    },
                                )
                                Text(
                                    if (item.protectedOriginal) "Сохранить" else "Копия",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            }
                            Text(item.displayName.ifBlank { "Без имени" }, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(duplicateReviewFileLocation(item.storageId, item.folderTarget), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(duplicateReviewFileSize(card.sizeBytes), style = MaterialTheme.typography.bodySmall)
                            Text(item.explanation, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
                }
            }
        }
        item(key = "visual-heading") {
        HorizontalDivider()
        Text("Визуальные кандидаты (только просмотр) · ${visualCandidates.size} пар", style = MaterialTheme.typography.titleMedium)
        Text("dHash лишь предлагает пары. Проверка читает исходные пиксели; результат не разрешает перемещение в корзину. Кандидаты ищутся также между страницами каталога. Каждая пара показывается на странице её первого изображения; не более 10 пар на страницу.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onVisualPageChanged(visualPage - 1) }, enabled = visualPage > 0 && !visualInProgress && !trashInProgress) { Text("Назад") }
            Text("Страница ${visualPage + 1} из $visualPageCount", modifier = Modifier.padding(top = 12.dp))
            OutlinedButton(onClick = { onVisualPageChanged(visualPage + 1) }, enabled = visualPage + 1 < visualPageCount && !visualInProgress && !trashInProgress) { Text("Далее") }
        }
        }
        if (visualCandidates.isEmpty()) item(key = "no-visual") { Text("На этой странице кандидатов нет.") }
        visualCandidates.forEachIndexed { candidateIndex, (first, second) ->
            item(key = "visual-$visualPage-$candidateIndex") {
            val key = "$first|$second"
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    DuplicatePreview(first, duplicateVisualSourceLabel(first)) { previewUri = first }
                    Text(duplicateVisualSourceLabel(first), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Column(modifier = Modifier.weight(1f)) {
                    DuplicatePreview(second, duplicateVisualSourceLabel(second)) { previewUri = second }
                    Text(duplicateVisualSourceLabel(second), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(visualCandidateReviewLabel(visualStatus[key]), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                enabled = !visualInProgress && !trashInProgress,
                onClick = { onVerifyVisual(first, second) },
            ) { Text("Проверить исходные пиксели") }
            }
        }
        item(key = "footer") {
        if (trashInProgress) Text("Проверяем исходные файлы и SHA-256, затем перемещаем выбранные копии. Не закрывайте экран до завершения.")
        if (!trashVerificationAvailable) Text("Режим просмотра: перемещение будет доступно после подключения повторной проверки файлов и защищённой корзины.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onClose, enabled = !trashInProgress && !visualInProgress) { Text("Закрыть") }
            OutlinedButton(onClick = { confirmOpen = false; onRefresh() }, enabled = !trashInProgress && !visualInProgress) { Text("Обновить") }
            OutlinedButton(onClick = { openConfirmation() }, enabled = duplicateTrashActionEnabled(trashVerificationAvailable, scopeAvailable,
                    trashInProgress, visualInProgress, prompt != null, pendingSelections.size, scopeResetPending)) {
                Text("В корзину…")
            }
        }
        }
    }
    }
    MaterialTheme(colorScheme = darkColorScheme(background = Color.Black, surface = Color(0xFF161616), onBackground = Color(0xFFE0E0E0), onSurface = Color(0xFFE0E0E0))) {
    if (previewUri != null) {
        val openedUri = previewUri.orEmpty()
        var fullPreviewFailed by remember(openedUri) { mutableStateOf(false) }
        Dialog(onDismissRequest = { previewUri = null }) {
            Surface(color = Color(0xFF161616)) { Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = Uri.parse(openedUri),
                        contentDescription = "Просмотр файла",
                        contentScale = ContentScale.Fit,
                        onError = { fullPreviewFailed = true },
                        onSuccess = { fullPreviewFailed = false },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (fullPreviewFailed) Text("Файл не удалось открыть для просмотра", color = Color(0xFFE0E0E0))
                }
                Text(duplicatePreviewDetails(openedUri, cards), style = MaterialTheme.typography.bodyMedium)
                Text(previewUri.orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                OutlinedButton(onClick = { previewUri = null }) { Text("Вернуться к совпадениям") }
            } }
        }
    }
    if (confirmOpen && duplicateConfirmationSnapshotCurrent(confirmationSnapshot, review) && duplicateTrashActionEnabled(trashVerificationAvailable, scopeAvailable, trashInProgress, visualInProgress, prompt != null, pendingSelections.size, scopeResetPending)) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            title = { Text("Подтверждение корзины") },
            text = { Text(duplicateTrashDialogMessage(prompt)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmOpen = false
                    // Recheck the current UI preconditions at click time; the host
                    // independently re-reads file bytes and SHA-256 before trash.
                    if (duplicateConfirmationSnapshotCurrent(confirmationSnapshot, review) &&
                            duplicateTrashActionEnabled(trashVerificationAvailable, scopeAvailable,
                            trashInProgress, visualInProgress, prompt != null,
                            pendingSelections.size, scopeResetPending)) {
                        onRequestTrashVerification(confirmationSnapshot!!)
                    }
                }) { Text("Проверить и переместить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmOpen = false }) { Text("Отмена") }
            },
        )
    }
    }
}

@Composable
private fun ScopeCheckbox(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        Text(label, modifier = Modifier.padding(top = 12.dp))
    }
}

/** Opening a preview never changes the duplicate selection or authorizes trash. */
@Composable
private fun DuplicatePreview(uri: String, name: String, onOpen: () -> Unit) {
    var previewFailed by remember(uri) { mutableStateOf(false) }
    Box(
        modifier = Modifier.fillMaxWidth().height(132.dp)
            .border(1.dp, Color(0xFF666666))
            .background(Color(0xFF202020))
            .clickable(onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = Uri.parse(uri),
            contentDescription = "Открыть $name",
            contentScale = ContentScale.Fit,
            onError = { previewFailed = true },
            onSuccess = { previewFailed = false },
            modifier = Modifier.fillMaxSize(),
        )
        if (previewFailed) {
            Text(
                "Нет превью — откройте файл",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFE0E0E0),
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

package com.mistermikhail.fgallery.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mistermikhail.fgallery.data.ExactDuplicateReview
import com.mistermikhail.fgallery.data.exactDuplicateReviewCards
import com.mistermikhail.fgallery.data.exactDuplicateTrashPrompt
import com.mistermikhail.fgallery.data.toggleDuplicateScope
import com.mistermikhail.fgallery.data.isDuplicateScopeChecked
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

/** Fixed two-column portrait review: one item in a final row retains half-width. */
internal fun duplicateReviewRows(itemCount: Int): Int =
    if (itemCount <= 0) 0 else (itemCount / 2) + (itemCount % 2)

/** Presentation only: byte counts never determine deletion safety. */
internal fun duplicateReviewFileSize(bytes: Long): String {
    if (bytes < 0L) return "Размер неизвестен"
    if (bytes < 1024L) return "$bytes Б"
    val kib = bytes.toDouble() / 1024.0
    if (kib < 1024.0) return String.format(java.util.Locale.ROOT, "%.1f КБ", kib)
    val mib = kib / 1024.0
    if (mib < 1024.0) return String.format(java.util.Locale.ROOT, "%.1f МБ", mib)
    return String.format(java.util.Locale.ROOT, "%.2f ГБ", mib / 1024.0)
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
    var confirmOpen by remember(review) { mutableStateOf(false) }
    var previewUri by remember { mutableStateOf<String?>(null) }
    var foldersExpanded by remember { mutableStateOf(false) }
    val cards = exactDuplicateReviewCards(review)
    val scopeAvailable = duplicateReviewScopeAvailable(review, availableStorages, availableFolders)
    val prompt = if (scopeAvailable && !trashInProgress && !visualInProgress) exactDuplicateTrashPrompt(review) else null
    // A disconnected SAF source invalidates an already-open confirmation.
    // Reconnection must require a fresh tap, not resurrect an old dialog.
    LaunchedEffect(scopeAvailable, prompt) {
        if (duplicateConfirmationInvalidated(review, availableStorages, availableFolders)) confirmOpen = false
    }
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Точные дубликаты", style = MaterialTheme.typography.titleLarge)
        Text("Сравниваются полные SHA-256. Похожие фотографии и RAW+JPEG не считаются точными копиями.")
        Text("Носители (все отмечены — поиск по всем)", style = MaterialTheme.typography.titleSmall)
        availableStorages.distinct().sorted().forEach { storage ->
            ScopeCheckbox(storage, isDuplicateScopeChecked(review.includedStorageIds, storage), enabled = duplicateScopeEditingEnabled(scopeAvailable, trashInProgress || visualInProgress)) { checked ->
                confirmOpen = false
                onScopeChanged(
                    toggleDuplicateScope(availableStorages, review.includedStorageIds, storage, checked),
                    review.includedFolderTargets,
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Папки: ${duplicateFolderScopeSummary(availableFolders.distinct().size, review.includedFolderTargets)}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
            OutlinedButton(onClick = { foldersExpanded = !foldersExpanded }) {
                Text(if (foldersExpanded) "Скрыть" else "Выбрать")
            }
        }
        if (foldersExpanded) {
            Text("Без выбранных ограничений поиск выполняется по всем папкам.", style = MaterialTheme.typography.bodySmall)
            availableFolders.distinct().sorted().forEach { folder ->
                ScopeCheckbox(folder, isDuplicateScopeChecked(review.includedFolderTargets, folder), enabled = duplicateScopeEditingEnabled(scopeAvailable, trashInProgress || visualInProgress)) { checked ->
                    confirmOpen = false
                    onScopeChanged(
                        review.includedStorageIds,
                        toggleDuplicateScope(availableFolders, review.includedFolderTargets, folder, checked),
                    )
                }
            }
        }
        if (!scopeAvailable) {
            Text("Выбранный носитель или папка недоступны. Подключите источник и обновите каталог либо явно сбросьте область поиска.")
            OutlinedButton(onClick = {
                // A disconnected explicit scope must never be silently widened.
                // Only an explicit reset clears both filters and pending consent.
                confirmOpen = false
                if (!trashInProgress) onScopeChanged(emptySet(), emptySet())
            }, enabled = !trashInProgress && !visualInProgress) { Text("Сбросить область поиска") }
        }
        Text("Найдено: ${cards.size} групп · ${cards.sumOf { it.items.size }} файлов", style = MaterialTheme.typography.titleMedium)
        if (cards.isEmpty()) Text("Точных совпадений в выбранной области не найдено")
        cards.forEachIndexed { index, card ->
            HorizontalDivider()
            Text("Группа ${index + 1} · ${card.items.size} файлов · ${duplicateReviewFileSize(card.sizeBytes)} каждый",
                style = MaterialTheme.typography.titleMedium)
            Text("Точное совпадение: размер и полный SHA-256 идентичны", style = MaterialTheme.typography.bodySmall)
            Text("SHA-256: ${card.sha256.take(16)}…", style = MaterialTheme.typography.bodySmall)
            card.items.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    pair.forEach { item ->
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            DuplicatePreview(item.uri, item.displayName) { previewUri = item.uri }
                            Row {
                                Checkbox(
                                    checked = item.manuallySelected,
                                    enabled = duplicateScopeEditingEnabled(scopeAvailable, trashInProgress || visualInProgress) && !item.protectedOriginal,
                                    onCheckedChange = {
                                        confirmOpen = false
                                        onCopySelectionChanged(item.uri, it)
                                    },
                                )
                                Text(
                                    if (item.protectedOriginal) "Сохранить" else "Копия",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(top = 14.dp),
                                )
                            }
                            Text(item.displayName, style = MaterialTheme.typography.bodyMedium)
                            Text("${item.storageId} · ${item.folderTarget}", style = MaterialTheme.typography.bodySmall)
                            Text(item.explanation, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        HorizontalDivider()
        Text("Визуальные кандидаты (только просмотр)", style = MaterialTheme.typography.titleMedium)
        Text("dHash лишь предлагает пары. Проверка читает исходные пиксели; результат не разрешает перемещение в корзину. Кандидаты ищутся также между страницами каталога. Каждая пара показывается на странице её первого изображения; не более 10 пар на страницу.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onVisualPageChanged(visualPage - 1) }, enabled = visualPage > 0 && !visualInProgress && !trashInProgress) { Text("Назад") }
            Text("Страница ${visualPage + 1} из $visualPageCount", modifier = Modifier.padding(top = 12.dp))
            OutlinedButton(onClick = { onVisualPageChanged(visualPage + 1) }, enabled = visualPage + 1 < visualPageCount && !visualInProgress && !trashInProgress) { Text("Далее") }
        }
        if (visualCandidates.isEmpty()) Text("На этой странице кандидатов нет.")
        visualCandidates.forEach { (first, second) ->
            val key = "$first|$second"
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    DuplicatePreview(first, first.substringAfterLast('/')) { previewUri = first }
                    Text(first.substringAfterLast('/'), style = MaterialTheme.typography.bodySmall)
                }
                Column(modifier = Modifier.weight(1f)) {
                    DuplicatePreview(second, second.substringAfterLast('/')) { previewUri = second }
                    Text(second.substringAfterLast('/'), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(visualCandidateReviewLabel(visualStatus[key]), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                enabled = !visualInProgress && !trashInProgress,
                onClick = { onVerifyVisual(first, second) },
            ) { Text("Проверить исходные пиксели") }
        }
        Text("Выбрано вручную: ${review.selectedCount}. Ничего не удаляется автоматически.")
        if (trashInProgress) Text("Проверяем исходные файлы и SHA-256, затем перемещаем выбранные копии. Не закрывайте экран до завершения.")
        if (!trashVerificationAvailable) Text("Режим просмотра: перемещение будет доступно после подключения повторной проверки файлов и защищённой корзины.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onClose, enabled = !trashInProgress && !visualInProgress) { Text("Закрыть") }
            OutlinedButton(onClick = { confirmOpen = false; onRefresh() }, enabled = !trashInProgress && !visualInProgress) { Text("Обновить") }
            OutlinedButton(onClick = { confirmOpen = true }, enabled = trashVerificationAvailable && duplicateTrashConfirmationEnabled(scopeAvailable, trashInProgress, visualInProgress, prompt != null)) {
                Text("В корзину…")
            }
        }
    }
    if (previewUri != null) {
        Dialog(onDismissRequest = { previewUri = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(
                    model = Uri.parse(previewUri),
                    contentDescription = "Просмотр файла",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(440.dp),
                )
                Text(previewUri.orEmpty().substringAfterLast('/'), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { previewUri = null }) { Text("Вернуться к совпадениям") }
            }
        }
    }
    if (confirmOpen && trashVerificationAvailable && duplicateTrashConfirmationEnabled(scopeAvailable, trashInProgress, visualInProgress, prompt != null)) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            title = { Text("Подтверждение корзины") },
            text = { Text(prompt?.message.orEmpty() + " Объём: ${prompt?.selectedBytes} байт.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmOpen = false
                    // Host MUST re-read current catalogue and bytes via verifiedExactDuplicateUris.
                    onRequestTrashVerification(review)
                }) { Text("Проверить и переместить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmOpen = false }) { Text("Отмена") }
            },
        )
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
    AsyncImage(
        model = Uri.parse(uri),
        contentDescription = "Открыть $name",
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable(onClick = onOpen),
    )
}

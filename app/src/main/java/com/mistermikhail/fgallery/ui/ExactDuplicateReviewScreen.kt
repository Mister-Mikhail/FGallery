package com.mistermikhail.fgallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
        Text("Папки (все отмечены — поиск по всем)", style = MaterialTheme.typography.titleSmall)
        availableFolders.distinct().sorted().forEach { folder ->
            ScopeCheckbox(folder, isDuplicateScopeChecked(review.includedFolderTargets, folder), enabled = duplicateScopeEditingEnabled(scopeAvailable, trashInProgress || visualInProgress)) { checked ->
                confirmOpen = false
                onScopeChanged(
                    review.includedStorageIds,
                    toggleDuplicateScope(availableFolders, review.includedFolderTargets, folder, checked),
                )
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
        if (cards.isEmpty()) Text("Точных совпадений в выбранной области не найдено")
        cards.forEachIndexed { index, card ->
            HorizontalDivider()
            Text("Группа ${index + 1} · ${card.items.size} файла · ${card.sizeBytes} байт",
                style = MaterialTheme.typography.titleMedium)
            Text("SHA-256: ${card.sha256}", style = MaterialTheme.typography.bodySmall)
            card.items.forEach { item ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(
                        checked = item.manuallySelected,
                        enabled = duplicateScopeEditingEnabled(scopeAvailable, trashInProgress || visualInProgress) && !item.protectedOriginal,
                        onCheckedChange = {
                            confirmOpen = false
                            onCopySelectionChanged(item.uri, it)
                        },
                    )
                    Column {
                        Text(item.displayName)
                        Text("${item.storageId} · ${item.folderTarget}",
                            style = MaterialTheme.typography.bodySmall)
                        Text(item.explanation, style = MaterialTheme.typography.bodySmall)
                    }
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
            Text(first.substringAfterLast('/') + " / " + second.substringAfterLast('/'))
            Text(visualStatus[key] ?: "Не проверено", style = MaterialTheme.typography.bodySmall)
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

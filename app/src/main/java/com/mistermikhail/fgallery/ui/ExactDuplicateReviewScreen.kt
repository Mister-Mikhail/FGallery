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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mistermikhail.fgallery.data.ExactDuplicateReview
import com.mistermikhail.fgallery.data.exactDuplicateReviewCards
import com.mistermikhail.fgallery.data.exactDuplicateTrashPrompt

/**
 * Stage 6 review surface. Intentionally not connected to the gallery menu or
 * trash mutation until the host supplies a fresh-byte verification pipeline.
 * onRequestTrashVerification is an INTENT, not permission to move files.
 */
@Composable
internal fun ExactDuplicateReviewScreen(
    review: ExactDuplicateReview,
    availableStorages: List<String>,
    availableFolders: List<String>,
    onScopeChanged: (Set<String>, Set<String>) -> Unit,
    onCopySelectionChanged: (String, Boolean) -> Unit,
    onRequestTrashVerification: () -> Unit,
    onClose: () -> Unit,
) {
    var confirmOpen by remember(review) { mutableStateOf(false) }
    val cards = exactDuplicateReviewCards(review)
    val prompt = exactDuplicateTrashPrompt(review)
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Точные дубликаты", style = MaterialTheme.typography.titleLarge)
        Text("Сравниваются полные SHA-256. Похожие фотографии и RAW+JPEG не считаются точными копиями.")
        Text("Носители (ничего не выбрано — все)", style = MaterialTheme.typography.titleSmall)
        availableStorages.distinct().sorted().forEach { storage ->
            ScopeCheckbox(storage, storage in review.includedStorageIds) { checked ->
                onScopeChanged(
                    if (checked) review.includedStorageIds + storage else review.includedStorageIds - storage,
                    review.includedFolderTargets,
                )
            }
        }
        Text("Папки (ничего не выбрано — все)", style = MaterialTheme.typography.titleSmall)
        availableFolders.distinct().sorted().forEach { folder ->
            ScopeCheckbox(folder, folder in review.includedFolderTargets) { checked ->
                onScopeChanged(
                    review.includedStorageIds,
                    if (checked) review.includedFolderTargets + folder else review.includedFolderTargets - folder,
                )
            }
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
                        enabled = !item.protectedOriginal,
                        onCheckedChange = { onCopySelectionChanged(item.uri, it) },
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
        Text("Выбрано вручную: ${review.selectedCount}. Ничего не удаляется автоматически.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onClose) { Text("Закрыть") }
            OutlinedButton(onClick = { confirmOpen = true }, enabled = prompt != null) {
                Text("В корзину…")
            }
        }
    }
    if (confirmOpen && prompt != null) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            title = { Text("Подтверждение корзины") },
            text = { Text(prompt.message + " Объём: ${prompt.selectedBytes} байт.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmOpen = false
                    // Host MUST re-read current catalogue and bytes via verifiedExactDuplicateUris.
                    onRequestTrashVerification()
                }) { Text("Проверить и переместить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmOpen = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun ScopeCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, modifier = Modifier.padding(top = 12.dp))
    }
}

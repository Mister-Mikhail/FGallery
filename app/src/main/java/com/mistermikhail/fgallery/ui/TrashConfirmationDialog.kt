package com.mistermikhail.fgallery.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun TrashConfirmationDialog(names: List<String>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (names.size == 1) "Удалить файл?" else "Удалить ${names.size} файлов?") },
        text = { Text(if (names.size == 1) names.first() else "Файлы будут перемещены в корзину.") },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm-trash")) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

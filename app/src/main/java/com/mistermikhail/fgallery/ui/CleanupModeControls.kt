package com.mistermikhail.fgallery.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

private val CleanupOrange = Color(0xFFFF5A36)

/** Only shown while Cleanup is enabled; disable must never ask for confirmation. */
@Composable
internal fun CleanupExitButton(onDisable: () -> Unit) {
    IconButton(onClick = onDisable, modifier = Modifier.size(40.dp)) {
        Icon(
            imageVector = Icons.Outlined.CleaningServices,
            contentDescription = "Выключить режим уборки",
            tint = CleanupOrange,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Explicit opt-in required before a double-tap can send files into FGallery's recycle bin. */
@Composable
internal fun CleanupEnableDialog(onCancel: () -> Unit, onEnable: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Включить режим уборки?") },
        text = { Text("Двойной тап будет сразу отправлять файл в корзину FGallery. Включить?") },
        dismissButton = { TextButton(onClick = onCancel) { Text("Отмена") } },
        confirmButton = { TextButton(onClick = onEnable) { Text("Включить") } },
    )
}

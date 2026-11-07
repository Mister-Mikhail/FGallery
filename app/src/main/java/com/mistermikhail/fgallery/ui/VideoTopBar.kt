package com.mistermikhail.fgallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Actions are provided by the owning ViewerScreen; no separate animation/timer. */
@Composable
internal fun VideoTopBar(
    expanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onCrop: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDetails: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    recycleBinMode: Boolean,
) {
    Box(Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 4.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = "Назад", tint = Color.White)
        }
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            IconButton(onClick = { onMenuExpandedChange(true) }) {
                Icon(Icons.Outlined.Menu, contentDescription = "Действия с файлом", tint = Color.White)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { onMenuExpandedChange(false) }) {
                fun action(callback: () -> Unit) {
                    onMenuExpandedChange(false)
                    callback()
                }
                DropdownMenuItem(text = { Text("Отправить") }, onClick = { action(onShare) })
                if (!recycleBinMode) DropdownMenuItem(text = { Text("Кадрировать видео") }, onClick = { action(onCrop) })
                if (!recycleBinMode) DropdownMenuItem(text = { Text("Переименовать") }, onClick = { action(onRename) })
                if (!recycleBinMode) DropdownMenuItem(text = { Text("Переместить") }, onClick = { action(onMove) })
                DropdownMenuItem(text = { Text("Сведения") }, onClick = { action(onDetails) })
                if (recycleBinMode) DropdownMenuItem(text = { Text("Восстановить") }, onClick = { action(onRestore) })
                else DropdownMenuItem(text = { Text("В корзину") }, onClick = { action(onTrash) })
            }
        }
    }
}

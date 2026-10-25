package com.mistermikhail.fgallery.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.mistermikhail.fgallery.data.MediaItem

/** One rename form for the full viewer and the selected-items toolbar. */
@Composable
internal fun RenameDialog(
    items: List<MediaItem>,
    onDismiss: () -> Unit,
    onConfirm: (List<Pair<MediaItem, String>>) -> Unit,
) {
    if (items.isEmpty()) return
    val batch = items.size > 1
    val initial = if (batch) "" else items.first().name
    val cursor = if (batch) 0 else initial.lastIndexOf('.').takeIf { it > 0 } ?: initial.length
    var field by remember(items.map { it.uriKey }) {
        mutableStateOf(TextFieldValue(initial, selection = TextRange(cursor)))
    }
    var confirmExtension by remember { mutableStateOf<List<Pair<MediaItem, String>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun plan(): List<Pair<MediaItem, String>> {
        val entered = field.text.trim()
        require(entered.isNotEmpty() && entered !in listOf(".", "..") && '/' !in entered && '\\' !in entered && '\u0000' !in entered) {
            "Введите допустимое имя файла"
        }
        if (!batch) {
            val original = items.first()
            val ext = original.name.substringAfterLast('.', "").takeIf { original.name.lastIndexOf('.') > 0 }.orEmpty()
            val name = if ('.' !in entered && ext.isNotEmpty()) "$entered.$ext" else entered
            return listOf(original to name)
        }
        return items.mapIndexed { index, item ->
            val ext = item.name.substringAfterLast('.', "").takeIf { item.name.lastIndexOf('.') > 0 }.orEmpty()
            val stem = entered.substringBeforeLast('.', entered)
            item to ("${stem}_${index + 1}" + if (ext.isNotEmpty()) ".$ext" else "")
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (batch) "Переименовать ${items.size} файлов" else "Переименовать файл") },
        text = {
            Column {
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it; error = null },
                    singleLine = true,
                    label = { Text(if (batch) "Общее имя" else "Новое имя") },
                    modifier = Modifier.focusRequester(focus),
                )
                if (batch) Text("Файлы получат суффиксы _1, _2, _3…; расширения останутся прежними.",
                    modifier = Modifier.padding(top = 8.dp))
                error?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching { plan() }.onSuccess { proposed ->
                    val extChanged = proposed.any { (source, newName) ->
                        source.name.substringAfterLast('.', "").lowercase() !=
                            newName.substringAfterLast('.', "").lowercase()
                    }
                    if (extChanged) confirmExtension = proposed else onConfirm(proposed)
                }.onFailure { error = it.localizedMessage }
            }) { Text("Переименовать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
    confirmExtension?.let { proposed ->
        AlertDialog(
            onDismissRequest = { confirmExtension = null },
            title = { Text("Изменить расширение файла?") },
            text = { Text("После изменения расширения файл может некорректно отображаться или открываться. Продолжить?") },
            confirmButton = {
                TextButton(onClick = { confirmExtension = null; onConfirm(proposed) }) { Text("Изменить") }
            },
            dismissButton = { TextButton(onClick = { confirmExtension = null }) { Text("Отмена") } },
        )
    }
}

package com.mistermikhail.fgallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** One permission per screen, and no system prompt behind another prompt. */
internal enum class AccessWizardStep { MEDIA, ALL_FILES }

@Composable
internal fun AccessOnboarding(
    step: AccessWizardStep,
    onGrant: () -> Unit,
    onLater: () -> Unit,
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 4.dp) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = if (step == AccessWizardStep.MEDIA) Icons.Outlined.PhotoLibrary
                        else Icons.Outlined.FolderOpen,
                    contentDescription = null,
                )
                Text(
                    if (step == AccessWizardStep.MEDIA) "Шаг 1 из 2 · Фото и видео"
                    else "Шаг 2 из 2 · Доступ ко всем файлам",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    if (step == AccessWizardStep.MEDIA)
                        "Разреши доступ ко всем фото и видео, чтобы галерея видела всю медиатеку. " +
                            "При выборе «Ограниченный доступ» видны только отмеченные файлы."
                    else
                        "Полный доступ нужен для RAW, TIFF, PDF, SVG, SD/USB, создания папок, " +
                            "корзины на накопителях и перемещения без повторных окон Android. " +
                            "На следующем экране включи переключатель «Разрешить доступ ко всем файлам» " +
                            "для FGallery и вернись назад.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onLater) { Text("Позже") }
                    Button(onClick = onGrant) { Text("Разрешить") }
                }
            }
        }
    }
}

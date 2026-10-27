package com.mistermikhail.fgallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    quickExifEnabled: Boolean,
    onQuickExifChanged: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    zoomSecondPower: Float,
    onZoomSecondPowerChanged: (Float) -> Unit,
    confirmMove: Boolean,
    confirmRename: Boolean,
    onConfirmMoveChanged: (Boolean) -> Unit,
    onConfirmRenameChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        ) {
            Text(
                text = "Настройки",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "EXIF при одиночном касании",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Показывать вместе с панелью просмотра.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Switch(
                    checked = quickExifEnabled,
                    onCheckedChange = onQuickExifChanged,
                )
            }

            SettingSwitch("Подтверждать перемещение", "Выключено по умолчанию", confirmMove, onConfirmMoveChanged)
            SettingSwitch("Подтверждать переименование", "", confirmRename, onConfirmRenameChanged)
            Text(
                text = "Тест · масштаб PDF/SVG",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = "Для больших фотографий и RAW: 50% → 160% по индикатору, независимо от формата. Параметр p ниже относится только к PDF/SVG.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (percentage in listOf(80, 85, 90)) {
                    FilterChip(
                        selected = kotlin.math.abs(zoomSecondPower - percentage / 100f) < .005f,
                        onClick = { onZoomSecondPowerChanged(percentage / 100f) },
                        label = { Text("$percentage%") },
                    )
                }
            }
            TextButton(onClick = onCheckForUpdates) { Text("Проверить обновления") }

        }
    }
}


@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description.isNotBlank()) Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

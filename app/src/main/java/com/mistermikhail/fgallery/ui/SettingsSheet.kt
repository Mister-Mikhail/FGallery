package com.mistermikhail.fgallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.mistermikhail.fgallery.data.ThumbnailCache
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
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
    mediaAccessStatus: String,
    allFilesGranted: Boolean,
    onMediaAccess: () -> Unit,
    onFilesAccess: () -> Unit,
    confirmRename: Boolean,
    onConfirmMoveChanged: (Boolean) -> Unit,
    onConfirmRenameChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden },
    )
    val context = LocalContext.current
    val installedVersion = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "Версия ${info.versionName} · сборка ${info.longVersionCode}"
    }.getOrDefault("Версия не определена")

    BackHandler(onBack = onDismiss)

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
                modifier = Modifier.padding(bottom = 2.dp),
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

            var accessExpanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth().clickable { accessExpanded = !accessExpanded }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Доступы", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    Text(
                        "Фото и видео: $mediaAccessStatus · Все файлы: " +
                            if (allFilesGranted) "выдан" else "не выдан",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(if (accessExpanded) "Свернуть" else "Открыть",
                    style = MaterialTheme.typography.labelMedium)
            }
            if (accessExpanded) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Фото и видео")
                        Text("Статус: $mediaAccessStatus", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onMediaAccess) { Text("Настроить") }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Доступ ко всем файлам")
                        Text(if (allFilesGranted) "Статус: выдан" else "Статус: не выдан",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onFilesAccess) { Text("Настроить") }
                }
            }
            SettingSwitch("Подтверждать перемещение", "Выключено по умолчанию", confirmMove, onConfirmMoveChanged)
            SettingSwitch("Подтверждать переименование", "", confirmRename, onConfirmRenameChanged)
            // Keep everyday settings visible on a phone without hiding advanced controls.
            var advancedExpanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth().clickable { advancedExpanded = !advancedExpanded }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Кэш и масштаб", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(if (advancedExpanded) "Свернуть" else "Открыть",
                    style = MaterialTheme.typography.labelMedium)
            }
            if (advancedExpanded) {
                Text(
                    text = "Кэш превью",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = "Лимиты дискового кэша. Уменьшение освобождает старые миниатюры, оригиналы не затрагиваются.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f),
                )
                val cacheScope = rememberCoroutineScope()
                var microMb by remember { mutableIntStateOf(ThumbnailCache.limitMb(context, false)) }
                var highMb by remember { mutableIntStateOf(ThumbnailCache.limitMb(context, true)) }
                Text("Маленькие превью · ${microMb} МБ", style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (mb in listOf(250, 500, 1000)) {
                        FilterChip(
                            selected = microMb == mb,
                            onClick = {
                                microMb = mb
                                cacheScope.launch { ThumbnailCache.setLimitMb(context, false, mb) }
                            },
                            label = { Text("${mb} МБ") },
                        )
                    }
                }
                Text("Крупные превью · ${highMb} МБ", style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (mb in listOf(512, 1024, 2048)) {
                        FilterChip(
                            selected = highMb == mb,
                            onClick = {
                                highMb = mb
                                cacheScope.launch { ThumbnailCache.setLimitMb(context, true, mb) }
                            },
                            label = { Text("${mb} МБ") },
                        )
                    }
                }
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
            }
            TextButton(onClick = onCheckForUpdates) { Text("Проверить обновления") }
            Text(
                text = installedVersion,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp, bottom = 4.dp),
            )

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

package com.mistermikhail.fgallery.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.mistermikhail.fgallery.data.CaptureLocation
import com.mistermikhail.fgallery.data.ExifAddressResolver
import com.mistermikhail.fgallery.data.canReadOriginalLocation
import com.mistermikhail.fgallery.data.MediaDetails
import com.mistermikhail.fgallery.data.MediaDetailsRepository
import com.mistermikhail.fgallery.data.MediaItem
import com.mistermikhail.fgallery.data.MediaKind
import com.mistermikhail.fgallery.data.FullResolutionFiles
import com.mistermikhail.fgallery.data.RawEmbeddedJpeg
import android.graphics.BitmapFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailsSheet(
    item: MediaItem,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        MediaDetailsRepository(context.applicationContext)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var hasLocationPermission by remember { mutableStateOf(context.canReadOriginalLocation()) }
    var permissionRequest by remember { mutableIntStateOf(0) }
    val requestLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasLocationPermission = it
        permissionRequest++
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasLocationPermission = context.canReadOriginalLocation()
    }

    val details = key(item, hasLocationPermission, permissionRequest) {
        produceState<MediaDetails?>(initialValue = null) { value = repository.load(item) }.value
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 14.dp),
            )
            if (item.kind == MediaKind.RAW) {
                val displayed = remember(item.uri, item.dateModifiedMillis, item.sizeBytes) {
                    val full = FullResolutionFiles.cached(context, item)
                    val camera = RawEmbeddedJpeg.cached(context, item)
                    val source = full ?: camera
                    val dimensions = source?.let { file ->
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.path, opts)
                        if (opts.outWidth > 0 && opts.outHeight > 0)
                            "${opts.outWidth}×${opts.outHeight}" else null
                    }
                    when {
                        full != null -> "Показано: проявка RAW (LibRaw)"
                        camera != null -> "Показано: JPEG камеры"
                        else -> "Показано: превью RAW"
                    } + (dimensions?.let { ", $it" } ?: "")
                }
                Text(
                    text = displayed,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }


            val loaded = details
            if (loaded == null) {
                Text(
                    text = "Чтение данных…",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                loaded.sections.forEachIndexed { sectionIndex, section ->
                    if (sectionIndex > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        )
                    }

                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    section.fields.forEach { field ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                text = field.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                                modifier = Modifier.weight(0.42f),
                            )
                            Text(
                                text = field.value,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(0.58f),
                            )
                        }
                    }
                }
                loaded.captureLocation?.let {
                    CaptureLocationSection(it) {
                        requestLocation.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
                    }
                }
            }
        }
    }
}

@Composable
internal fun CaptureLocationSection(location: CaptureLocation, requestPermission: () -> Unit) {
    val context = LocalContext.current
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 16.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    )
    Text("Место съёмки", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    when (location) {
        CaptureLocation.Missing -> Text("В файле нет координат")
        CaptureLocation.Unavailable -> Text("Не удалось прочитать координаты")
        CaptureLocation.PermissionRequired -> {
            Text("Для чтения координат оригинала нужен доступ")
            TextButton(onClick = requestPermission) { Text("Предоставить доступ") }
        }
        is CaptureLocation.Available -> {
            val coordinates = location.coordinates
            val locale = LocalConfiguration.current.locales[0]
            val address = key(coordinates, locale) {
                produceState<AddressState>(AddressState.Loading) {
                    val resolved = ExifAddressResolver.resolve(context.applicationContext, coordinates, locale)
                    value = if (resolved == null) AddressState.Unavailable else AddressState.Available(resolved)
                }.value
            }
            when (address) {
                AddressState.Loading -> Text("Определяется адрес…")
                AddressState.Unavailable -> Text("Адрес недоступен")
                is AddressState.Available -> Text(address.text)
            }
            Text(coordinates.display(), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                val point = "${coordinates.latitude},${coordinates.longitude}"
                val label = (address as? AddressState.Available)?.text
                val query = if (label == null) point else "$point ($label)"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$point?q=${Uri.encode(query)}"))
                try {
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, "Приложение карт недоступно", Toast.LENGTH_SHORT).show()
                } catch (_: SecurityException) {
                    Toast.makeText(context, "Не удалось открыть карту", Toast.LENGTH_SHORT).show()
                }
            }) { Text("Открыть на карте") }
        }
    }
}

private sealed interface AddressState {
    data object Loading : AddressState
    data object Unavailable : AddressState
    data class Available(val text: String) : AddressState
}

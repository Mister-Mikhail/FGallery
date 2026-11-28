package com.mistermikhail.fgallery.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.mistermikhail.fgallery.data.CaptureLocation
import com.mistermikhail.fgallery.data.CaptureMapPreview
import com.mistermikhail.fgallery.data.ExifGpsCoordinates
import com.mistermikhail.fgallery.data.ExifAddressResolver
import com.mistermikhail.fgallery.data.canReadOriginalLocation
import com.mistermikhail.fgallery.data.MediaDetails
import com.mistermikhail.fgallery.data.MediaDetailsRepository
import com.mistermikhail.fgallery.data.MediaItem
import com.mistermikhail.fgallery.data.MediaKind
import com.mistermikhail.fgallery.data.FullResolutionFiles
import com.mistermikhail.fgallery.data.RawEmbeddedJpeg
import android.graphics.BitmapFactory
import coil3.compose.AsyncImage
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailsSheet(
    item: MediaItem,
    onDismiss: () -> Unit,
    onNavigate: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        MediaDetailsRepository(context.applicationContext)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val navigate by rememberUpdatedState(onNavigate)

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
                .pointerInput(Unit) {
                    var distance = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { distance = 0f },
                        onDragEnd = { if (abs(distance) >= size.width * .15f) navigate(if (distance < 0) 1 else -1) },
                        onHorizontalDrag = { _, amount -> distance += amount },
                    )
                }
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
                loaded.captureLocation?.let {
                    CaptureLocationSection(it) {
                        requestLocation.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
                    }
                }
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
            }
        }
    }
}

@Composable
internal fun CaptureLocationSection(
    location: CaptureLocation,
    loadMap: suspend (Context, ExifGpsCoordinates) -> Bitmap? = CaptureMapPreview::load,
    openMap: ((Uri) -> Unit)? = null,
    requestPermission: () -> Unit,
) {
    if (location == CaptureLocation.Missing) return
    val context = LocalContext.current
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 16.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    )
    Text("Место съёмки", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    when (location) {
        CaptureLocation.Missing -> Unit
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
            val map = key(coordinates) {
                produceState<MapState>(MapState.Loading) {
                    value = loadMap(context.applicationContext, coordinates)?.let(MapState::Ready) ?: MapState.Unavailable
                }.value
            }
            val openCapturePoint = {
                val point = "${coordinates.latitude},${coordinates.longitude}"
                val label = (address as? AddressState.Available)?.text
                val query = if (label == null) point else "$point ($label)"
                val uri = Uri.parse("geo:$point?q=${Uri.encode(query)}")
                if (openMap != null) openMap(uri) else openLocationLink(context, uri)
            }
            Box(
                Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    .aspectRatio(CaptureMapPreview.WIDTH.toFloat() / CaptureMapPreview.HEIGHT)
                    .clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant)
                    .testTag("capture-location-map").clickable(onClickLabel = "Открыть на карте", onClick = openCapturePoint),
            ) {
                if (map is MapState.Ready) {
                    AsyncImage(map.image, null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
                    Text(
                        "© OpenStreetMap contributors",
                        modifier = Modifier.align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.surface)
                            .clickable { openLocationLink(context, Uri.parse("https://www.openstreetmap.org/copyright")) }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Icon(Icons.Outlined.LocationOn, "Метка места съёмки",
                        Modifier.align(Alignment.Center).offset(y = (-13).dp).size(32.dp),
                        tint = androidx.compose.ui.graphics.Color(0xFFB3261E))
                } else {
                    Text(if (map == MapState.Loading) "Загружается карта…" else "Карта недоступна · Открыть на карте",
                        modifier = Modifier.align(Alignment.Center).padding(10.dp),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            when (address) {
                AddressState.Loading -> Text("Определяется адрес…")
                AddressState.Unavailable -> Text("Адрес недоступен")
                is AddressState.Available -> Text(address.text)
            }
            Text(coordinates.display(), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun openLocationLink(context: Context, uri: Uri) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Приложение карт недоступно", Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, "Не удалось открыть карту", Toast.LENGTH_SHORT).show()
    }
}

private sealed interface MapState {
    data object Loading : MapState
    data object Unavailable : MapState
    data class Ready(val image: Bitmap) : MapState
}

private sealed interface AddressState {
    data object Loading : AddressState
    data object Unavailable : AddressState
    data class Available(val text: String) : AddressState
}

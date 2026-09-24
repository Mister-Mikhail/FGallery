package com.mistermikhail.fgallery

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.mistermikhail.fgallery.data.MediaItem
import com.mistermikhail.fgallery.ui.GalleryScreen
import com.mistermikhail.fgallery.ui.GalleryViewModel
import com.mistermikhail.fgallery.ui.RecycleBinScreen
import com.mistermikhail.fgallery.ui.ViewerScreen
import com.mistermikhail.fgallery.ui.theme.FGalleryTheme
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface PendingWriteOperation {
    data class Rename(
        val item: MediaItem,
        val newName: String,
    ) : PendingWriteOperation

    data class Move(
        val items: List<MediaItem>,
        val relativePath: String,
    ) : PendingWriteOperation
}

class MainActivity : ComponentActivity() {
    private val viewModel: GalleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FGalleryTheme { FGalleryRoot(viewModel) }
        }
    }

    @Composable
    private fun FGalleryRoot(viewModel: GalleryViewModel) {
        val state by viewModel.uiState.collectAsState()
        var selectedItem by remember { mutableStateOf<MediaItem?>(null) }
        var cleanupMode by remember { mutableStateOf(false) }
        var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
        var pendingWrite by remember { mutableStateOf<PendingWriteOperation?>(null) }
        var pendingPermanentDeleteUris by remember { mutableStateOf<Set<String>>(emptySet()) }
        var pendingCropItem by remember { mutableStateOf<MediaItem?>(null) }
        var pendingCropSave by remember { mutableStateOf<Pair<MediaItem, Uri>?>(null) }
        val uiScope = rememberCoroutineScope()

        fun shareMedia(item: MediaItem) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType ?: if (item.kind == com.mistermikhail.fgallery.data.MediaKind.VIDEO) "video/*" else "image/*"
                putExtra(Intent.EXTRA_STREAM, item.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Отправить"))
        }

        suspend fun saveCroppedCopy(
            source: MediaItem,
            croppedUri: Uri,
        ): Boolean = withContext(Dispatchers.IO) {
            val baseName = source.name.substringBeforeLast('.', source.name)
            val outputName = "${baseName}_crop_${System.currentTimeMillis()}.jpg"
            val relativePath = source.relativePath.ifBlank { "Pictures/FGallery/" }

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, outputName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val outputUri = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values,
            ) ?: return@withContext false

            val copied = runCatching {
                contentResolver.openInputStream(croppedUri)?.use { input ->
                    contentResolver.openOutputStream(outputUri, "w")?.use { output ->
                        input.copyTo(output)
                    } ?: error("Cannot open crop destination")
                } ?: error("Cannot open crop result")
                true
            }.getOrDefault(false)

            if (!copied) {
                runCatching { contentResolver.delete(outputUri, null, null) }
                return@withContext false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val ready = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                contentResolver.update(outputUri, ready, null, null)
            }

            true
        }

        val cropLauncher = rememberLauncherForActivityResult(
            CropImageContract(),
        ) { result ->
            val source = pendingCropItem
            pendingCropItem = null

            if (result.isSuccessful && source != null && result.uriContent != null) {
                pendingCropSave = source to result.uriContent!!
            } else if (result.error != null) {
                Toast.makeText(
                    this@MainActivity,
                    "Не удалось кадрировать изображение",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }

        fun cropMedia(item: MediaItem) {
            pendingCropItem = item
            cropLauncher.launch(
                CropImageContractOptions(
                    uri = item.uri,
                    cropImageOptions = CropImageOptions(
                        guidelines = CropImageView.Guidelines.ON,
                        outputCompressFormat = Bitmap.CompressFormat.JPEG,
                        outputCompressQuality = 96,
                    ),
                )
            )
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            viewModel.onPermissionChanged(hasMediaPermission())
        }

        fun executeWrite(operation: PendingWriteOperation): Boolean {
            return when (operation) {
                is PendingWriteOperation.Rename -> {
                    val requested = operation.newName.trim()
                    if (requested.isBlank()) return false

                    val oldExtension = operation.item.name
                        .substringAfterLast('.', "")
                        .takeIf { it.isNotBlank() }

                    val finalName = if (
                        oldExtension != null &&
                        !requested.substringAfterLast('/', requested).contains('.')
                    ) {
                        "$requested.$oldExtension"
                    } else {
                        requested
                    }

                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
                    }

                    contentResolver.update(
                        operation.item.uri,
                        values,
                        null,
                        null,
                    ) > 0
                }

                is PendingWriteOperation.Move -> {
                    val normalized = operation.relativePath
                        .trim()
                        .replace('\\', '/')
                        .trim('/')
                        .takeIf { it.isNotBlank() }
                        ?.plus("/")
                        ?: return false

                    operation.items
                        .map { item ->
                            val values = ContentValues().apply {
                                put(MediaStore.MediaColumns.RELATIVE_PATH, normalized)
                            }
                            contentResolver.update(item.uri, values, null, null) > 0
                        }
                        .all { it }
                }
            }
        }

        val writeLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            val operation = pendingWrite
            pendingWrite = null

            if (result.resultCode == Activity.RESULT_OK && operation != null) {
                val success = runCatching { executeWrite(operation) }.getOrDefault(false)
                if (success) {
                    selectedIds = emptySet()
                    viewModel.refresh()
                }
            }
        }

        fun requestWrite(operation: PendingWriteOperation) {
            val uris = when (operation) {
                is PendingWriteOperation.Rename -> listOf(operation.item.uri)
                is PendingWriteOperation.Move -> operation.items.map { it.uri }
            }

            if (uris.isEmpty()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching {
                    pendingWrite = operation
                    val pendingIntent = MediaStore.createWriteRequest(
                        contentResolver,
                        uris,
                    )
                    writeLauncher.launch(
                        IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    )
                }.onFailure {
                    pendingWrite = null
                }
                return
            }

            try {
                if (executeWrite(operation)) {
                    selectedIds = emptySet()
                    viewModel.refresh()
                }
            } catch (securityException: SecurityException) {
                val recoverable = securityException as? RecoverableSecurityException
                val sender: IntentSender? =
                    recoverable?.userAction?.actionIntent?.intentSender

                if (sender != null) {
                    pendingWrite = operation
                    writeLauncher.launch(IntentSenderRequest.Builder(sender).build())
                }
            }
        }

        val deleteForeverLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            val deletedUris = pendingPermanentDeleteUris
            pendingPermanentDeleteUris = emptySet()

            if (result.resultCode == Activity.RESULT_OK) {
                selectedIds = emptySet()
                viewModel.onPermanentlyDeleted(deletedUris)
            }
        }

        fun requestTrash(
            items: List<MediaItem>,
            keepViewerOpen: Boolean = false,
        ) {
            if (items.isEmpty()) return

            val nextViewerItem = if (
                keepViewerOpen &&
                items.size == 1
            ) {
                val currentItems = state.visibleItems
                val currentIndex = currentItems.indexOfFirst { it.id == items.first().id }

                when {
                    currentIndex < 0 -> null
                    currentIndex < currentItems.lastIndex -> currentItems[currentIndex + 1]
                    currentIndex > 0 -> currentItems[currentIndex - 1]
                    else -> null
                }
            } else {
                null
            }

            viewModel.moveToRecycleBin(items)
            selectedItem = if (keepViewerOpen) nextViewerItem else null
            selectedIds = emptySet()
        }

        fun restoreFromRecycleBin(items: List<MediaItem>) {
            if (items.isEmpty()) return

            viewModel.restoreFromRecycleBin(items)
            selectedIds = emptySet()
        }

        fun deleteForever(items: List<MediaItem>) {
            if (items.isEmpty()) return

            val uriStrings = items.mapTo(mutableSetOf()) { it.uri.toString() }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching {
                    pendingPermanentDeleteUris = uriStrings
                    val pendingIntent = MediaStore.createDeleteRequest(
                        contentResolver,
                        items.map { it.uri },
                    )
                    deleteForeverLauncher.launch(
                        IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    )
                }.onFailure {
                    pendingPermanentDeleteUris = emptySet()
                }
                return
            }

            var deletedAny = false
            items.forEach { item ->
                runCatching {
                    deletedAny =
                        contentResolver.delete(item.uri, null, null) > 0 || deletedAny
                }
            }

            if (deletedAny) {
                selectedIds = emptySet()
                viewModel.onPermanentlyDeleted(uriStrings)
            }
        }

        LaunchedEffect(Unit) {
            viewModel.onPermissionChanged(hasMediaPermission())
        }

        val current = selectedItem

        BackHandler(enabled = current != null) {
            selectedItem = null
        }

        BackHandler(enabled = current == null && selectedIds.isNotEmpty()) {
            selectedIds = emptySet()
        }

        BackHandler(
            enabled = current == null &&
                selectedIds.isEmpty() &&
                state.recycleBinVisible,
        ) {
            viewModel.closeRecycleBin()
        }

        BackHandler(
            enabled = current == null &&
                selectedIds.isEmpty() &&
                !state.recycleBinVisible &&
                state.selectedAlbum != null,
        ) {
            viewModel.closeAlbum()
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (state.recycleBinVisible) {
                RecycleBinScreen(
                    items = state.recycleBinItems,
                    selectedIds = selectedIds,
                    onBack = {
                        selectedIds = emptySet()
                        viewModel.closeRecycleBin()
                    },
                    onToggleSelection = { item ->
                        selectedIds =
                            if (item.id in selectedIds) selectedIds - item.id
                            else selectedIds + item.id
                    },
                    onSelectAll = {
                        selectedIds = state.recycleBinItems.mapTo(mutableSetOf()) { it.id }
                    },
                    onClearSelection = {
                        selectedIds = emptySet()
                    },
                    onRestore = ::restoreFromRecycleBin,
                    onDeleteForever = ::deleteForever,
                )
            } else {
                GalleryScreen(
                    state = state,
                    onRequestPermission = {
                        permissionLauncher.launch(requiredPermissions())
                    },
                    onRefresh = viewModel::refresh,
                    onOpenAlbum = { album ->
                        selectedIds = emptySet()
                        viewModel.openAlbum(album)
                    },
                    onBackToAlbums = {
                        selectedIds = emptySet()
                        viewModel.closeAlbum()
                    },
                    onOpenMedia = {
                        selectedIds = emptySet()
                        selectedItem = it
                    },
                    onToggleGridMode = viewModel::toggleGridMode,
                    onToggleSearch = viewModel::toggleSearch,
                    onQueryChanged = viewModel::setQuery,
                    onFilterChanged = { filter ->
                        selectedIds = emptySet()
                        viewModel.setFilter(filter)
                    },
                    onSortChanged = { sort ->
                        selectedIds = emptySet()
                        viewModel.setSortMode(sort)
                    },
                    onShowSettings = viewModel::showSettings,
                    onHideSettings = viewModel::hideSettings,
                    onQuickExifChanged = viewModel::setQuickExifEnabled,
                    livePreviewEnabled = current == null,
                    onOpenRecycleBin = {
                        selectedIds = emptySet()
                        selectedItem = null
                        viewModel.openRecycleBin()
                    },
                    cleanupMode = cleanupMode,
                    onCleanupModeChanged = { cleanupMode = it },
                    selectedIds = selectedIds,
                    onToggleSelection = { item ->
                        selectedIds =
                            if (item.id in selectedIds) selectedIds - item.id
                            else selectedIds + item.id
                    },
                    onClearSelection = {
                        selectedIds = emptySet()
                    },
                    onTrashSelected = { items -> requestTrash(items, keepViewerOpen = false) },
                    onRenameSelected = { item, name ->
                        requestWrite(
                            PendingWriteOperation.Rename(
                                item = item,
                                newName = name,
                            )
                        )
                    },
                    onMoveSelected = { items, path ->
                        requestWrite(
                            PendingWriteOperation.Move(
                                items = items,
                                relativePath = path,
                            )
                        )
                    },
                )
            }

            AnimatedContent(
                targetState = if (state.recycleBinVisible) null else current,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    if (targetState != null) {
                        (
                            fadeIn(animationSpec = tween(150)) +
                                scaleIn(
                                    animationSpec = tween(220),
                                    initialScale = 0.96f,
                                )
                            ) togetherWith fadeOut(animationSpec = tween(90))
                    } else {
                        fadeIn(animationSpec = tween(90)) togetherWith
                            (
                                fadeOut(animationSpec = tween(180)) +
                                    scaleOut(
                                        animationSpec = tween(210),
                                        targetScale = 0.96f,
                                    )
                                )
                    }
                },
                contentKey = { item -> if (item == null) "gallery" else "viewer" },
                label = "media-viewer-transition",
            ) { animatedItem ->
                if (animatedItem != null) {
                    ViewerScreen(
                        items = state.visibleItems,
                        initialItem = animatedItem,
                        onBack = { selectedItem = null },
                        onTrash = { requestTrash(listOf(it), keepViewerOpen = true) },
                        quickExifEnabled = state.quickExifEnabled,
                        cleanupMode = cleanupMode,
                        onShare = ::shareMedia,
                        onCrop = ::cropMedia,
                        onRename = { item, name ->
                            requestWrite(PendingWriteOperation.Rename(item, name))
                        },
                        onMove = { item, path ->
                            requestWrite(PendingWriteOperation.Move(listOf(item), path))
                        },
                    )
                }
            }
        }
        pendingCropSave?.let { (source, croppedUri) ->
            AlertDialog(
                onDismissRequest = { pendingCropSave = null },
                title = { Text("Сохранить кадрированную копию?") },
                text = {
                    Text(
                        "Оригинал \"${source.name}\" останется без изменений. " +
                            "Кадрированный вариант будет сохранён рядом как новый JPEG."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingCropSave = null
                            uiScope.launch {
                                val saved = saveCroppedCopy(source, croppedUri)
                                if (saved) {
                                    viewModel.refresh()
                                } else {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Не удалось сохранить кадрированную копию",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        },
                    ) {
                        Text("Сохранить")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingCropSave = null }) {
                        Text("Отмена")
                    }
                },
            )
        }
    }

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    private fun hasMediaPermission(): Boolean =
        requiredPermissions().all {
            ContextCompat.checkSelfPermission(
                this,
                it,
            ) == PackageManager.PERMISSION_GRANTED
        }
}

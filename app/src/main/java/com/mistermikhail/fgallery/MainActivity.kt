package com.mistermikhail.fgallery

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.mistermikhail.fgallery.data.MediaItem
import com.mistermikhail.fgallery.ui.GalleryScreen
import com.mistermikhail.fgallery.ui.GalleryViewModel
import com.mistermikhail.fgallery.ui.RecycleBinScreen
import com.mistermikhail.fgallery.ui.ViewerScreen
import com.mistermikhail.fgallery.ui.theme.FGalleryTheme

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

        val trashLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                selectedItem = null
                selectedIds = emptySet()
                viewModel.refresh()
                if (state.recycleBinVisible) viewModel.refreshRecycleBin()
            }
        }

        val restoreLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                selectedIds = emptySet()
                viewModel.refresh()
                viewModel.refreshRecycleBin()
            }
        }

        val deleteForeverLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                selectedIds = emptySet()
                viewModel.refreshRecycleBin()
            }
        }

        fun requestTrash(items: List<MediaItem>) {
            if (items.isEmpty()) return

            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val pendingIntent = MediaStore.createTrashRequest(
                        contentResolver,
                        items.map { it.uri },
                        true,
                    )
                    trashLauncher.launch(
                        IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    )
                    return@runCatching
                }

                var deletedAny = false
                items.forEach { item ->
                    try {
                        deletedAny =
                            contentResolver.delete(item.uri, null, null) > 0 || deletedAny
                    } catch (securityException: SecurityException) {
                        val recoverable = securityException as? RecoverableSecurityException
                        val sender: IntentSender? =
                            recoverable?.userAction?.actionIntent?.intentSender

                        if (sender != null) {
                            trashLauncher.launch(
                                IntentSenderRequest.Builder(sender).build()
                            )
                            return@forEach
                        }
                    }
                }

                if (deletedAny) {
                    selectedItem = null
                    selectedIds = emptySet()
                    viewModel.refresh()
                }
            }
        }

        fun restoreFromRecycleBin(items: List<MediaItem>) {
            if (items.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
            runCatching {
                val pendingIntent = MediaStore.createTrashRequest(
                    contentResolver,
                    items.map { it.uri },
                    false,
                )
                restoreLauncher.launch(
                    IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                )
            }
        }

        fun deleteForever(items: List<MediaItem>) {
            if (items.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
            runCatching {
                val pendingIntent = MediaStore.createDeleteRequest(
                    contentResolver,
                    items.map { it.uri },
                )
                deleteForeverLauncher.launch(
                    IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                )
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
                    loading = state.recycleBinLoading,
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
                    onTrashSelected = ::requestTrash,
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

            if (current != null && !state.recycleBinVisible) {
                ViewerScreen(
                    items = state.visibleItems,
                    initialItem = current,
                    onBack = { selectedItem = null },
                    onTrash = { requestTrash(listOf(it)) },
                    quickExifEnabled = state.quickExifEnabled,
                    cleanupMode = cleanupMode,
                )
            }
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

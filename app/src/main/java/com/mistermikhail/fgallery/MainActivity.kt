package com.mistermikhail.fgallery

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
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
import android.provider.DocumentsContract
import android.provider.Settings
import android.os.Environment
import com.mistermikhail.fgallery.data.StorageFolders
import androidx.compose.runtime.produceState
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.mistermikhail.fgallery.ui.AlbumSummary
import com.mistermikhail.fgallery.ui.GalleryScreen
import com.mistermikhail.fgallery.ui.MoveDestinationScreen
import com.mistermikhail.fgallery.ui.GalleryViewModel
import com.mistermikhail.fgallery.ui.RecycleBinScreen
import com.mistermikhail.fgallery.ui.ViewerScreen
import com.mistermikhail.fgallery.ui.theme.FGalleryTheme
import com.mistermikhail.fgallery.data.MediaKind
import com.mistermikhail.fgallery.data.EditedMediaStore
import com.mistermikhail.fgallery.data.DriveLibrary
import com.mistermikhail.fgallery.data.StorageAccess
import com.mistermikhail.fgallery.data.StorageFolder
import com.mistermikhail.fgallery.data.DocumentLibrary
import com.mistermikhail.fgallery.ui.VideoCropDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PendingTreeMove(
    val sourceItems: List<MediaItem>,
    val copiedUris: List<Uri>,
)

private class PartialMoveException(val remaining: List<MediaItem>, cause: Exception) : Exception(cause.localizedMessage, cause)

private sealed interface PendingWriteOperation {
    data class Rename(
        val item: MediaItem,
        val newName: String,
    ) : PendingWriteOperation

    data class Replace(val item: MediaItem, val editedUri: Uri, val mime: String, val extension: String) : PendingWriteOperation

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
        var pendingWriteConfirmation by remember { mutableStateOf<PendingWriteOperation?>(null) }
        var pendingPermanentDeleteUris by remember { mutableStateOf<Set<String>>(emptySet()) }
        var pendingMoveItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
        var moveQuery by remember { mutableStateOf("") }
        var moveDestinationAlbum by remember { mutableStateOf<AlbumSummary?>(null) }
        var pendingTreeMove by remember { mutableStateOf<PendingTreeMove?>(null) }
        var pendingCropItem by remember { mutableStateOf<MediaItem?>(null) }
        var pendingCropSave by remember { mutableStateOf<Pair<MediaItem, Uri>?>(null) }
        var firstLaunchAccess by remember { mutableStateOf(false) }
        var pendingAccessWrite by remember { mutableStateOf<PendingWriteOperation?>(null) }
        var waitingAccessWrite by remember { mutableStateOf<PendingWriteOperation?>(null) }
        var createFolderVisible by remember { mutableStateOf(false) }
        var createFolderPath by remember { mutableStateOf("Новая папка") }
        var folderRevision by remember { mutableStateOf(0) }
        var directDeleteItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
        var operationBusy by remember { mutableStateOf(false) }
        var operationMessage by remember { mutableStateOf("Операция с файлами") }
        var videoCropItem by remember { mutableStateOf<MediaItem?>(null) }
        val uiScope = rememberCoroutineScope()
        val extraFolders by produceState<List<StorageFolder>>(emptyList(), pendingMoveItems.isNotEmpty(), folderRevision) {
            if (pendingMoveItems.isNotEmpty()) value = withContext(Dispatchers.IO) { DriveLibrary.folders(this@MainActivity) }
        }
        fun emptyAlbum(folder: StorageFolder): AlbumSummary = AlbumSummary(
            name = folder.name,
            cover = MediaItem(StorageAccess.stableId(folder.target), Uri.EMPTY, "", null, MediaKind.IMAGE, 0L, 0, 0, 0L, "", "", 0L,
                storageId = folder.storageId, folderTarget = folder.target),
            count = 0, newestDateMillis = 0L, totalSizeBytes = 0L,
        )

        fun notify(message: String) { Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show() }
        fun finishOperation() {
            selectedIds = emptySet()
            pendingMoveItems = emptyList()
            moveQuery = ""
            moveDestinationAlbum = null
            viewModel.refresh()
        }
        val documentsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            val granted = uris.filter { uri ->
                runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); true }.getOrDefault(false)
            }
            DocumentLibrary(this@MainActivity).add(granted)
            viewModel.refresh()
            if (granted.size < uris.size) notify("Для некоторых документов не удалось сохранить доступ. Выберите их ещё раз.")
        }

        fun shareMedia(item: MediaItem) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType ?: if (item.kind == com.mistermikhail.fgallery.data.MediaKind.VIDEO) "video/*" else "image/*"
                val sharedUri = if (item.uri.scheme == "file") androidx.core.content.FileProvider.getUriForFile(this@MainActivity, "$packageName.files", java.io.File(item.uri.path!!)) else item.uri
                putExtra(Intent.EXTRA_STREAM, sharedUri)
                clipData = android.content.ClipData.newRawUri(item.name, sharedUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Отправить"))
        }

        val cropLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val source = pendingCropItem
            pendingCropItem = null
            val edited = result.data?.data
            if (result.resultCode == Activity.RESULT_OK && source != null && edited != null) {
                pendingCropSave = source to edited
            }
        }

        fun cropMedia(item: MediaItem) {
            if (operationBusy) return
            if (item.kind == MediaKind.VIDEO) { videoCropItem = item; return }
            if (item.kind == MediaKind.PDF || item.kind == MediaKind.SVG) {
                notify("Кадрирование доступно для растровых изображений и видео")
                return
            }
            pendingCropItem = item
            uiScope.launch {
            val inputUri = if (item.kind == MediaKind.RAW || com.mistermikhail.fgallery.data.TiffImages.isTiff(item.name, item.mimeType)) {
                try {
                    val source = com.mistermikhail.fgallery.data.FullResolutionFiles.prepare(this@MainActivity, item)
                    androidx.core.content.FileProvider.getUriForFile(this@MainActivity, "$packageName.files", source)
                } catch (e: Exception) {
                    pendingCropItem = null
                    notify("Не удалось подготовить исходник: ${e.localizedMessage}")
                    return@launch
                }
            } else item.uri
            runCatching {
                cropLauncher.launch(Intent(this@MainActivity, ImageCropActivity::class.java).apply {
                    data = inputUri
                    putExtra("png", item.mimeType == "image/png")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            }.onFailure { pendingCropItem = null; notify("Не удалось открыть кадрирование: ${it.localizedMessage}") }
            }
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            viewModel.onPermissionChanged(hasMediaPermission())
        }

        suspend fun executeWrite(operation: PendingWriteOperation) {
            when (operation) {
                is PendingWriteOperation.Replace -> EditedMediaStore(this@MainActivity).replace(operation.item, operation.editedUri, operation.mime, operation.extension)
                is PendingWriteOperation.Rename -> {
                    val requested = operation.newName.trim()
                    require(requested.isNotBlank() && '/' !in requested && '\\' !in requested) { "Недопустимое имя файла" }
                    val extension = operation.item.name.substringAfterLast('.', "")
                    val finalName = if ('.' !in requested && extension.isNotBlank()) "$requested.$extension" else requested
                    val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, finalName) }
                    check(contentResolver.update(operation.item.uri, values, null, null) == 1) { "Переименование не выполнено" }
                }
                is PendingWriteOperation.Move -> {
                    val rawPath = operation.relativePath.trim()
                    val qualified = rawPath.startsWith("/") || rawPath.startsWith("content://")
                    val path = if (qualified) rawPath else rawPath.replace('\\', '/').trim('/') + "/"
                    require(path.isNotBlank() && path != "/") { "Недопустимая папка" }
                    var firstFailure: Exception? = null
                    val remaining = mutableListOf<MediaItem>()
                    for (item in operation.items) {
                        try {
                            if (qualified) { StorageAccess.move(this@MainActivity, item, path); continue }
                            if (item.relativePath == path) continue
                            if (StorageFolders.hasFileAccess()) {
                                StorageFolders.move(this@MainActivity, item, path)
                                continue
                            }
                            val values = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, path) }
                            // Use the exact URI Android granted, never the Files alias.
                            check(contentResolver.update(item.uri, values, null, null) == 1) { "Файл ${item.name} не перемещён" }
                            val actual = contentResolver.query(item.uri, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)?.use {
                                if (it.moveToFirst()) it.getString(0) else null
                            }
                            check(actual == path) { "Папка ${item.name} не изменилась" }
                        } catch (e: Exception) { remaining += item; if (firstFailure == null) firstFailure = e }
                    }
                    firstFailure?.let { throw PartialMoveException(remaining, it) }
                }
            }
        }

        fun operationUris(operation: PendingWriteOperation): List<Uri> = when (operation) {
            is PendingWriteOperation.Rename -> listOf(operation.item.uri)
            is PendingWriteOperation.Move -> operation.items.map { it.uri }
            is PendingWriteOperation.Replace -> listOf(operation.item.uri)
        }

        suspend fun performWrite(operation: PendingWriteOperation): Exception? = withContext(Dispatchers.IO) {
            try { executeWrite(operation); null } catch (e: Exception) { e }
        }

        fun retainFailedMove(failure: Exception?) {
            if (failure is PartialMoveException) {
                pendingMoveItems = failure.remaining
                selectedIds = failure.remaining.mapTo(mutableSetOf()) { it.id }
            }
        }
        val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val operation = pendingWrite
            pendingWrite = null
            if (operation == null) return@rememberLauncherForActivityResult
            if (result.resultCode != Activity.RESULT_OK) {
                notify("Операция отменена. Выбор файлов сохранён.")
            } else uiScope.launch {
                operationBusy = true
                val failure = performWrite(operation)
                retainFailedMove(failure)
                operationBusy = false
                if (failure == null) { if (operation is PendingWriteOperation.Replace) selectedItem = null; finishOperation(); notify("Готово") }
                else { viewModel.refresh(); notify("Не удалось завершить операцию: ${failure.localizedMessage}. Выбор сохранён.") }
            }
        }

        fun requestWrite(operation: PendingWriteOperation, skipConfirmation: Boolean = false) {
            if (operationBusy || pendingWrite != null) return
            val needsConfirmation = when (operation) {
                is PendingWriteOperation.Move -> state.confirmMove
                is PendingWriteOperation.Rename -> state.confirmRename
                is PendingWriteOperation.Replace -> false // Explicit overwrite dialog already accepted.
            }
            if (!skipConfirmation && needsConfirmation) { pendingWriteConfirmation = operation; return }
            if (operation is PendingWriteOperation.Move && !operation.relativePath.startsWith("content://") && Build.VERSION.SDK_INT >= 30 && !StorageFolders.hasFileAccess()) {
                pendingAccessWrite = operation
                return
            }
            operationMessage = when (operation) {
                is PendingWriteOperation.Move -> "Перемещение в ${operation.relativePath}"
                is PendingWriteOperation.Rename -> "Переименование"
                is PendingWriteOperation.Replace -> "Сохранение с резервной копией оригинала"
            }
            operationBusy = true
            uiScope.launch {
                val failure = performWrite(operation)
                retainFailedMove(failure)
                operationBusy = false
                if (failure == null) { finishOperation(); notify("Готово"); return@launch }
                val security = if (failure is PartialMoveException) failure.cause as? SecurityException else failure as? SecurityException
                if (security != null) {
                    runCatching {
                        val sender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            MediaStore.createWriteRequest(contentResolver, operationUris(operation)).intentSender
                        } else (security as? RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
                        check(sender != null) { "Android не предоставил разрешение" }
                        pendingWrite = operation
                        writeLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    }.onFailure {
                        pendingWrite = null
                        notify("Android не разрешил операцию: ${it.localizedMessage}. Выберите папку через проводник.")
                    }
                } else {
                    viewModel.refresh()
                    notify("Не удалось завершить операцию: ${failure.localizedMessage}. Выбор сохранён; проверьте папки при частичном перемещении.")
                }
            }
        }

        val fileAccessLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            folderRevision++
            viewModel.onPermissionChanged(hasMediaPermission())
            val operation = waitingAccessWrite
            waitingAccessWrite = null
            if (operation != null && StorageFolders.hasFileAccess()) requestWrite(operation, skipConfirmation = true)
            else if (operation != null) notify("Доступ не выдан. Операция не выполнена, выбор сохранён.")
        }
        fun openFileAccessSettings(operation: PendingWriteOperation? = null) {
            waitingAccessWrite = operation
            runCatching {
                fileAccessLauncher.launch(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            }.onFailure {
                waitingAccessWrite = null
                notify("Не удалось открыть настройки доступа. Откройте их в настройках Android для FGallery.")
            }
        }

        fun rollbackTreeCopies(uris: List<Uri>) {
            uris.forEach { uri ->
                runCatching { contentResolver.delete(uri, null, null) }
            }
        }

        val treeMoveDeleteLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            val pending = pendingTreeMove
            pendingTreeMove = null

            if (pending == null) return@rememberLauncherForActivityResult

            if (result.resultCode == Activity.RESULT_OK) {
                pendingMoveItems = emptyList()
                moveQuery = ""
                moveDestinationAlbum = null
                selectedIds = emptySet()
                viewModel.refresh()
            } else {
                uiScope.launch(Dispatchers.IO) {
                    pending.sourceItems.zip(pending.copiedUris).forEach { (source, copy) ->
                        val sourceExists = runCatching { contentResolver.openInputStream(source.uri)?.use { true } == true }.getOrDefault(false)
                        if (sourceExists) rollbackTreeCopies(listOf(copy))
                    }
                }
                Toast.makeText(
                    this@MainActivity,
                    "Перемещение отменено",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }

        suspend fun copyItemsToTree(
            items: List<MediaItem>,
            treeUri: Uri,
        ): List<Uri>? = withContext(Dispatchers.IO) {
            val parentDocumentUri = runCatching {
                DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri),
                )
            }.getOrNull() ?: return@withContext null

            val copied = mutableListOf<Uri>()

            for (item in items) {
                val destinationUri = runCatching {
                    DocumentsContract.createDocument(
                        contentResolver,
                        parentDocumentUri,
                        item.mimeType ?: "application/octet-stream",
                        item.name,
                    )
                }.getOrNull()

                if (destinationUri == null) {
                    rollbackTreeCopies(copied)
                    return@withContext null
                }

                val success = runCatching {
                    val written = contentResolver.openInputStream(item.uri)?.use { input ->
                        contentResolver.openOutputStream(destinationUri, "w")?.use { output ->
                            input.copyTo(output)
                        } ?: error("Cannot open destination")
                    } ?: error("Cannot open source")
                    check(written > 0 && (item.sizeBytes <= 0 || written == item.sizeBytes))
                    val verified = contentResolver.openInputStream(destinationUri)?.use { input ->
                        var size = 0L
                        val buffer = ByteArray(65536)
                        while (true) { val n = input.read(buffer); if (n < 0) break; size += n }
                        size
                    }
                    check(verified == written)
                    true
                }.getOrDefault(false)

                if (!success) {
                    runCatching { contentResolver.delete(destinationUri, null, null) }
                    rollbackTreeCopies(copied)
                    return@withContext null
                }

                copied += destinationUri
            }

            copied
        }

        fun finishTreeMove(
            sourceItems: List<MediaItem>,
            copiedUris: List<Uri>,
        ) {
            if (StorageFolders.hasFileAccess()) {
                operationBusy = true
                uiScope.launch {
                    val removed = withContext(Dispatchers.IO) {
                        sourceItems.map { item -> runCatching {
                            if (item.uri.scheme == "file") java.io.File(item.uri.path!!).delete()
                            else contentResolver.delete(item.uri, null, null) > 0
                        }.getOrDefault(false) }
                    }
                    operationBusy = false
                    if (removed.all { it }) { finishOperation(); notify("Перемещение завершено") }
                    else {
                        pendingMoveItems = sourceItems.zip(removed).filterNot { it.second }.map { it.first }
                        viewModel.refresh()
                        notify("Часть оригиналов не удалось удалить. Копии сохранены в целевой папке; проверьте их перед повтором.")
                    }
                }
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching {
                    pendingTreeMove = PendingTreeMove(sourceItems, copiedUris)
                    val pendingIntent = MediaStore.createDeleteRequest(
                        contentResolver,
                        sourceItems.map { it.uri },
                    )
                    treeMoveDeleteLauncher.launch(
                        IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    )
                }.onFailure {
                    pendingTreeMove = null
                    rollbackTreeCopies(copiedUris)
                }
                return
            }

            val deleted = sourceItems.map { item ->
                runCatching {
                    contentResolver.delete(item.uri, null, null) > 0
                }.getOrDefault(false)
            }

            if (deleted.all { it }) {
                pendingMoveItems = emptyList()
                moveQuery = ""
                moveDestinationAlbum = null
                selectedIds = emptySet()
                viewModel.refresh()
            } else {
                sourceItems.zip(copiedUris).zip(deleted).forEach { (pair, removed) ->
                    if (!removed) rollbackTreeCopies(listOf(pair.second))
                }
                viewModel.refresh()
                Toast.makeText(
                    this@MainActivity,
                    "Перемещение выполнено частично; проверьте папки. Выбор сохранён",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }

        val folderPickerLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
        ) { treeUri ->
            val items = pendingMoveItems
            if (treeUri == null || items.isEmpty()) {
                return@rememberLauncherForActivityResult
            }

            runCatching {
                contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }

            StorageAccess.grant(this@MainActivity, treeUri)
            folderRevision++
            requestWrite(PendingWriteOperation.Move(items,
                DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri)).toString()))
        }
        val storagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
            if (tree != null) runCatching {
                contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                StorageAccess.grant(this@MainActivity, tree)
                folderRevision++
                viewModel.refresh()
            }.onFailure { notify("Не удалось сохранить доступ: ${it.localizedMessage}") }
        }

        fun beginMove(items: List<MediaItem>) {
            if (items.isEmpty()) return
            pendingMoveItems = items
            moveQuery = ""
            moveDestinationAlbum = null
            selectedItem = null
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

            if (operationBusy) return
            uiScope.launch {
                operationBusy = true
                operationMessage = "Перемещение в корзину"
                try {
                    viewModel.moveToRecycleBin(items)
                    selectedItem = if (keepViewerOpen) nextViewerItem else null
                    selectedIds = emptySet()
                } catch (e: Exception) { notify("Не удалось переместить в корзину: ${e.localizedMessage}") }
                finally { operationBusy = false }
            }
        }

        fun restoreFromRecycleBin(items: List<MediaItem>) {
            if (items.isEmpty()) return

            if (operationBusy) return
            uiScope.launch {
                operationBusy = true
                operationMessage = "Восстановление"
                try { viewModel.restoreFromRecycleBin(items); selectedIds = emptySet() }
                catch (e: Exception) { notify("Не удалось восстановить: ${e.localizedMessage}") }
                finally { operationBusy = false }
            }
        }

        fun deleteForever(items: List<MediaItem>) {
            if (items.isEmpty()) return

            if (items.any { it.uri.scheme == "file" || DocumentsContract.isDocumentUri(this@MainActivity, it.uri) }) { directDeleteItems = items; return }
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
            val onboarding = getSharedPreferences("fgallery_onboarding", MODE_PRIVATE)
            if (!onboarding.getBoolean("file_access_shown", false) && !hasMediaPermission()) {
                onboarding.edit().putBoolean("file_access_shown", true).apply()
                if (Build.VERSION.SDK_INT >= 30) firstLaunchAccess = true
                else permissionLauncher.launch(requiredPermissions())
            } else if (!onboarding.getBoolean("file_access_shown", false) && !StorageFolders.hasFileAccess() && Build.VERSION.SDK_INT >= 30) {
                onboarding.edit().putBoolean("file_access_shown", true).apply()
                firstLaunchAccess = true
            }
        }

        DisposableEffect(Unit) {
            var refreshSignal: kotlinx.coroutines.Job? = null
            fun changed() { refreshSignal?.cancel(); refreshSignal = uiScope.launch { kotlinx.coroutines.delay(300); folderRevision++; viewModel.refresh() } }
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(context: android.content.Context?, intent: Intent?) { changed() }
            }
            val filter = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_MEDIA_MOUNTED); addAction(Intent.ACTION_MEDIA_UNMOUNTED)
                addAction(Intent.ACTION_MEDIA_EJECT); addAction(Intent.ACTION_MEDIA_REMOVED); addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
                addDataScheme("file")
            }
            androidx.core.content.ContextCompat.registerReceiver(this@MainActivity, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
            val usbReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(context: android.content.Context?, intent: Intent?) { changed() }
            }
            val usbFilter = android.content.IntentFilter().apply {
                addAction(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            androidx.core.content.ContextCompat.registerReceiver(this@MainActivity, usbReceiver, usbFilter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
            val manager = getSystemService(android.os.storage.StorageManager::class.java)
            val callback = if (Build.VERSION.SDK_INT >= 30) object : android.os.storage.StorageManager.StorageVolumeCallback() {
                override fun onStateChanged(volume: android.os.storage.StorageVolume) { changed() }
            } else null
            if (Build.VERSION.SDK_INT >= 30 && callback != null) manager.registerStorageVolumeCallback(mainExecutor, callback)
            val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) { changed() }
            }
            contentResolver.registerContentObserver(MediaStore.Files.getContentUri("external"), true, observer)
            onDispose { refreshSignal?.cancel(); contentResolver.unregisterContentObserver(observer); unregisterReceiver(receiver); unregisterReceiver(usbReceiver); if (Build.VERSION.SDK_INT >= 30 && callback != null) manager.unregisterStorageVolumeCallback(callback) }
        }
        if (directDeleteItems.isNotEmpty()) AlertDialog(
            onDismissRequest = { directDeleteItems = emptyList() },
            title = { Text("Удалить навсегда?") },
            text = { Text("Файлы: ${directDeleteItems.size}. Восстановить их будет нельзя.") },
            confirmButton = { TextButton(onClick = {
                val deleting = directDeleteItems; directDeleteItems = emptyList()
                uiScope.launch {
                    operationBusy = true
                    val removed = mutableListOf<String>()
                    try {
                        withContext(Dispatchers.IO) { for (item in deleting) {
                            check(StorageAccess.delete(this@MainActivity, item.uri)) { "Не удалось удалить ${item.name}" }
                            removed.add(item.uriKey)
                        } }
                        selectedIds = emptySet()
                    } catch (e: Exception) { notify(e.localizedMessage ?: "Удаление не завершено") }
                    finally { viewModel.onPermanentlyDeleted(removed); operationBusy = false }
                }
            }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { directDeleteItems = emptyList() }) { Text("Отмена") } },
        )

        val current = selectedItem

        BackHandler(enabled = current != null) {
            selectedItem = null
        }

        BackHandler(enabled = current == null && pendingMoveItems.isNotEmpty()) {
            if (moveDestinationAlbum != null) {
                moveDestinationAlbum = null
            } else {
                pendingMoveItems = emptyList()
                moveQuery = ""
            }
        }

        BackHandler(enabled = current == null && pendingMoveItems.isEmpty() && selectedIds.isNotEmpty()) {
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
            enabled = current == null && pendingMoveItems.isEmpty() &&
                selectedIds.isEmpty() &&
                !state.recycleBinVisible &&
                state.selectedAlbum != null,
        ) {
            viewModel.closeAlbum()
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (pendingMoveItems.isNotEmpty()) {
                MoveDestinationScreen(
                    albums = state.albums + extraFolders.filterNot { folder -> state.albums.any { it.cover.folderTarget == folder.target } }.map(::emptyAlbum),
                    movingCount = pendingMoveItems.size,
                    query = moveQuery,
                    selectedAlbum = moveDestinationAlbum,
                    onQueryChanged = { moveQuery = it },
                    onSelectAlbum = { moveDestinationAlbum = it },
                    onBackFromAlbum = { moveDestinationAlbum = null },
                    onCancel = {
                        pendingMoveItems = emptyList()
                        moveQuery = ""
                        moveDestinationAlbum = null
                    },
                    onMoveHere = { album ->
                        requestWrite(
                            PendingWriteOperation.Move(
                                items = pendingMoveItems,
                                relativePath = album.cover.folderTarget.ifBlank { album.cover.relativePath },
                            )
                        )
                    },
                    onChooseOtherFolder = { folderPickerLauncher.launch(null) },
                    onCreateFolder = {
                        if (StorageFolders.hasFileAccess() || moveDestinationAlbum?.cover?.folderTarget?.startsWith("content://") == true) createFolderVisible = true
                        else openFileAccessSettings()
                    },
                )
            } else if (state.recycleBinVisible) {
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
                    onAddStorage = { storagePickerLauncher.launch(null) },
                    onImportDocuments = { documentsLauncher.launch(arrayOf("application/pdf", "image/svg+xml", "image/tiff", "image/x-tiff")) },
                    onManageFileAccess = { openFileAccessSettings() },
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
                    onConfirmMoveChanged = viewModel::setConfirmMove,
                    onConfirmRenameChanged = viewModel::setConfirmRename,
                    livePreviewEnabled = current == null && pendingMoveItems.isEmpty(),
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
                    onMoveSelected = ::beginMove,
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
                        active = videoCropItem == null && !operationBusy,
                        onTrash = { requestTrash(listOf(it), keepViewerOpen = true) },
                        quickExifEnabled = state.quickExifEnabled,
                        cleanupMode = cleanupMode,
                        onShare = ::shareMedia,
                        onCrop = ::cropMedia,
                        onRename = { item, name ->
                            requestWrite(PendingWriteOperation.Rename(item, name))
                        },
                        onMove = { item ->
                            beginMove(listOf(item))
                        },
                    )
                }
            }
        }
        if (firstLaunchAccess) AlertDialog(
            onDismissRequest = { firstLaunchAccess = false },
            title = { Text("Доступ к файлам для FGallery") },
            text = { Text("Чтобы видеть фото, видео, RAW, PDF и SVG во всех доступных папках, создавать папки и перемещать файлы без повторных подтверждений, разрешите доступ ко всем файлам на следующем экране Android.") },
            confirmButton = { TextButton(onClick = { firstLaunchAccess = false; openFileAccessSettings() }) { Text("Разрешить доступ") } },
            dismissButton = { TextButton(onClick = { firstLaunchAccess = false; permissionLauncher.launch(requiredPermissions()) }) { Text("Только фото и видео") } },
        )
        pendingAccessWrite?.let { operation ->
            AlertDialog(
                onDismissRequest = { pendingAccessWrite = null },
                title = { Text("Доступ для перемещения без повторных окон") },
                text = { Text("Один раз разрешите FGallery доступ ко всем файлам в настройках Android. После этого выбранные файлы перемещаются сразу; доступны пустые папки, создание папок и поиск PDF/SVG.") },
                confirmButton = { TextButton(onClick = { pendingAccessWrite = null; openFileAccessSettings(operation) }) { Text("Настроить доступ") } },
                dismissButton = { TextButton(onClick = { pendingAccessWrite = null }) { Text("Отмена") } },
            )
        }
        if (createFolderVisible) AlertDialog(
            onDismissRequest = { createFolderVisible = false },
            title = { Text("Создать папку") },
            text = { OutlinedTextField(createFolderPath, { createFolderPath = it }, label = { Text("Имя папки") }) },
            confirmButton = { TextButton(onClick = {
                uiScope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching {
                        val parent = moveDestinationAlbum?.cover?.folderTarget?.takeIf { it.isNotBlank() }
                            ?: android.os.Environment.getExternalStorageDirectory().path
                        StorageAccess.directory(this@MainActivity, parent, createFolderPath.trim())
                    } }
                    result.onSuccess { path ->
                        createFolderVisible = false
                        folderRevision++
                        moveDestinationAlbum = emptyAlbum(StorageFolder(path, createFolderPath.trim(), moveDestinationAlbum?.cover?.storageId ?: "external_primary"))
                    }.onFailure { notify(it.localizedMessage ?: "Не удалось создать папку") }
                }
            }) { Text("Создать") } },
            dismissButton = { TextButton(onClick = { createFolderVisible = false }) { Text("Отмена") } },
        )
        pendingWriteConfirmation?.let { operation ->
            val isMove = operation is PendingWriteOperation.Move
            AlertDialog(
                onDismissRequest = { pendingWriteConfirmation = null },
                title = { Text(if (isMove) "Переместить файл?" else "Переименовать файл?") },
                text = { Text(if (operation is PendingWriteOperation.Move) "Перемещение ${operation.items.size} файлов в ${operation.relativePath}" else "Имя файла будет изменено.") },
                confirmButton = {
                    TextButton(onClick = {
                        pendingWriteConfirmation = null
                        requestWrite(operation, skipConfirmation = true)
                    }) { Text(if (isMove) "Переместить" else "Переименовать") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingWriteConfirmation = null }) { Text("Отмена") }
                },
            )
        }

        pendingCropSave?.let { (source, croppedUri) ->
            val png = source.mimeType == "image/png"
            val mime = if (png) "image/png" else "image/jpeg"
            val extension = if (png) "png" else "jpg"
            val canReplace = source.kind == MediaKind.IMAGE && source.mimeType in listOf("image/jpeg", "image/png")
            AlertDialog(
                onDismissRequest = { pendingCropSave = null },
                title = { Text("Сохранить кадрирование") },
                confirmButton = {
                    TextButton(onClick = {
                        pendingCropSave = null
                        operationBusy = true
                        operationMessage = "Сохранение кадрированной копии"
                        uiScope.launch {
                            val result = withContext(Dispatchers.IO) { runCatching { EditedMediaStore(this@MainActivity).saveCopy(source, croppedUri, mime, extension) } }
                            operationBusy = false
                            result.onSuccess { viewModel.refresh(); notify("Копия сохранена") }.onFailure { notify("Не удалось сохранить копию: ${it.localizedMessage}") }
                        }
                    }) { Text("Сохранить копию") }
                },
                dismissButton = {
                    androidx.compose.foundation.layout.Row {
                        if (canReplace) TextButton(onClick = {
                            pendingCropSave = null
                            requestWrite(PendingWriteOperation.Replace(source, croppedUri, mime, extension), skipConfirmation = true)
                        }) { Text("Заменить оригинал") }
                        TextButton(onClick = { pendingCropSave = null }) { Text("Отмена") }
                    }
                },
            )
        }
        videoCropItem?.let { item ->
            VideoCropDialog(item = item, onDismiss = { videoCropItem = null }, onExported = { uri, replace ->
                videoCropItem = null
                if (replace) requestWrite(PendingWriteOperation.Replace(item, uri, "video/mp4", "mp4"), skipConfirmation = true)
                else {
                    operationBusy = true
                    operationMessage = "Сохранение видео"
                    uiScope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { EditedMediaStore(this@MainActivity).saveCopy(item, uri, "video/mp4", "mp4") } }
                        operationBusy = false
                        result.onSuccess { viewModel.refresh(); notify("Видео сохранено") }.onFailure { notify("Не удалось сохранить видео: ${it.localizedMessage}") }
                    }
                }
            })
        }
        if (operationBusy) {
            BackHandler { }
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
                androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().navigationBarsPadding())
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
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

    private fun hasMediaPermission(): Boolean =
        StorageFolders.hasFileAccess() || requiredPermissions().all {
            ContextCompat.checkSelfPermission(
                this,
                it,
            ) == PackageManager.PERMISSION_GRANTED
        }
}


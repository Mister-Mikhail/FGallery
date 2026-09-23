package com.mistermikhail.fgallery

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
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
import com.mistermikhail.fgallery.ui.ViewerScreen
import com.mistermikhail.fgallery.ui.theme.FGalleryTheme

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

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            viewModel.onPermissionChanged(hasMediaPermission())
        }

        val trashLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                selectedItem = null
                viewModel.refresh()
            }
        }

        fun requestTrash(item: MediaItem) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val pendingIntent = MediaStore.createTrashRequest(
                        contentResolver,
                        listOf(item.uri),
                        true,
                    )
                    trashLauncher.launch(
                        IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    )
                    return@runCatching
                }

                try {
                    contentResolver.delete(item.uri, null, null)
                    selectedItem = null
                    viewModel.refresh()
                } catch (securityException: SecurityException) {
                    val recoverable = securityException as? RecoverableSecurityException
                    val sender: IntentSender? = recoverable?.userAction?.actionIntent?.intentSender
                    if (sender != null) {
                        trashLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            viewModel.onPermissionChanged(hasMediaPermission())
        }

        val current = selectedItem

        BackHandler(enabled = current != null) { selectedItem = null }
        BackHandler(enabled = current == null && state.selectedAlbum != null) { viewModel.closeAlbum() }

        Box(modifier = Modifier.fillMaxSize()) {
            // Keep the gallery in composition while the viewer is open. This preserves
            // the exact scroll position so Back returns to the tile the user opened.
            GalleryScreen(
                state = state,
                onRequestPermission = { permissionLauncher.launch(requiredPermissions()) },
                onRefresh = viewModel::refresh,
                onOpenAlbum = viewModel::openAlbum,
                onBackToAlbums = viewModel::closeAlbum,
                onOpenMedia = { selectedItem = it },
                onToggleGridMode = viewModel::toggleGridMode,
                onToggleSearch = viewModel::toggleSearch,
                onQueryChanged = viewModel::setQuery,
                onFilterChanged = viewModel::setFilter,
                onSortChanged = viewModel::setSortMode,
                onShowSettings = viewModel::showSettings,
                onHideSettings = viewModel::hideSettings,
                onQuickExifChanged = viewModel::setQuickExifEnabled,
                cleanupMode = cleanupMode,
                onCleanupModeChanged = { cleanupMode = it },
            )

            if (current != null) {
                ViewerScreen(
                    items = state.visibleItems,
                    initialItem = current,
                    onBack = { selectedItem = null },
                    onTrash = ::requestTrash,
                    quickExifEnabled = state.quickExifEnabled,
                    cleanupMode = cleanupMode,
                )
            }
        }
    }

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    private fun hasMediaPermission(): Boolean =
        requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
}

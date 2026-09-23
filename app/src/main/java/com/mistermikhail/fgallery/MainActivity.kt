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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            viewModel.onPermissionChanged(hasMediaPermission())
        }

        val trashLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) viewModel.refresh()
        }

        fun requestTrash(item: MediaItem) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent = MediaStore.createTrashRequest(
                    contentResolver,
                    listOf(item.uri),
                    true,
                )
                trashLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                return
            }

            try {
                contentResolver.delete(item.uri, null, null)
                viewModel.refresh()
            } catch (securityException: SecurityException) {
                val recoverable = securityException as? RecoverableSecurityException
                val sender: IntentSender? = recoverable?.userAction?.actionIntent?.intentSender
                if (sender != null) {
                    trashLauncher.launch(IntentSenderRequest.Builder(sender).build())
                }
            }
        }

        LaunchedEffect(Unit) {
            viewModel.onPermissionChanged(hasMediaPermission())
        }

        val current = selectedItem

        BackHandler(enabled = current != null) { selectedItem = null }
        BackHandler(enabled = current == null && state.selectedAlbum != null) { viewModel.closeAlbum() }

        if (current == null) {
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
            )
        } else {
            ViewerScreen(
                items = state.visibleItems,
                initialItem = current,
                onBack = { selectedItem = null },
                onTrash = ::requestTrash,
            )
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

package com.mistermikhail.fgallery.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewQuilt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mistermikhail.fgallery.data.MediaItem
import com.mistermikhail.fgallery.data.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    state: GalleryUiState,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onBackToAlbums: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    onToggleGridMode: () -> Unit,
    onToggleSearch: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onFilterChanged: (MediaFilter) -> Unit,
    onSortChanged: (SortMode) -> Unit,
    onShowSettings: () -> Unit,
    onHideSettings: () -> Unit,
    onQuickExifChanged: (Boolean) -> Unit,
    cleanupMode: Boolean,
    onCleanupModeChanged: (Boolean) -> Unit,
    selectedIds: Set<Long>,
    onToggleSelection: (MediaItem) -> Unit,
    onClearSelection: () -> Unit,
    onTrashSelected: (List<MediaItem>) -> Unit,
    onRenameSelected: (MediaItem, String) -> Unit,
    onMoveSelected: (List<MediaItem>, String) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<MediaItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    var moveDialogVisible by remember { mutableStateOf(false) }
    var movePath by remember { mutableStateOf("") }

    val inAlbum = state.selectedAlbum != null
    val selectedItems = state.visibleItems.filter { it.id in selectedIds }
    val selectionMode = selectedItems.isNotEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                if (selectionMode) {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = onClearSelection) {
                                Icon(Icons.Outlined.Close, contentDescription = "Отменить выбор")
                            }
                        },
                        title = {
                            Text(
                                text = "${selectedItems.size} выбрано",
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        actions = {
                            IconButton(
                                onClick = {
                                    movePath = selectedItems.firstOrNull()?.relativePath.orEmpty()
                                    moveDialogVisible = true
                                },
                            ) {
                                Icon(Icons.Outlined.DriveFileMove, contentDescription = "Переместить")
                            }

                            if (selectedItems.size == 1) {
                                IconButton(
                                    onClick = {
                                        renameTarget = selectedItems.first()
                                        renameText = selectedItems.first().name
                                    },
                                ) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Переименовать")
                                }
                            }

                            IconButton(onClick = { onTrashSelected(selectedItems) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "В корзину")
                            }
                        },
                    )
                } else {
                    TopAppBar(
                        navigationIcon = {
                            if (inAlbum) {
                                IconButton(onClick = onBackToAlbums) {
                                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Назад к альбомам")
                                }
                            }
                        },
                        title = {
                            Column {
                                Text(text = state.selectedAlbum ?: "FGallery", fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (inAlbum) "${state.visibleItems.size} объектов" else "${state.albums.size} альбомов",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                        actions = {
                            IconButton(onClick = onToggleSearch) {
                                Icon(Icons.Outlined.Search, contentDescription = "Поиск")
                            }
                            if (inAlbum) {
                                IconButton(onClick = onToggleGridMode) {
                                    Icon(
                                        if (state.gridMode == GridMode.MOSAIC) Icons.Outlined.GridOn else Icons.Outlined.ViewQuilt,
                                        contentDescription = "Вид сетки",
                                    )
                                }
                            }
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(Icons.Outlined.MoreVert, contentDescription = "Меню")
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                ) {
                                    fun apply(action: () -> Unit) {
                                        menuExpanded = false
                                        action()
                                    }

                                    DropdownMenuItem(text = { Text("Все файлы") }, onClick = { apply { onFilterChanged(MediaFilter.ALL) } })
                                    DropdownMenuItem(text = { Text("Фото") }, onClick = { apply { onFilterChanged(MediaFilter.PHOTOS) } })
                                    DropdownMenuItem(text = { Text("Видео") }, onClick = { apply { onFilterChanged(MediaFilter.VIDEOS) } })
                                    DropdownMenuItem(text = { Text("RAW") }, onClick = { apply { onFilterChanged(MediaFilter.RAW) } })

                                    if (inAlbum) {
                                        DropdownMenuItem(text = { Text("Сначала новые") }, onClick = { apply { onSortChanged(SortMode.DATE_DESC) } })
                                        DropdownMenuItem(text = { Text("Сначала старые") }, onClick = { apply { onSortChanged(SortMode.DATE_ASC) } })
                                        DropdownMenuItem(text = { Text("Имя А–Я") }, onClick = { apply { onSortChanged(SortMode.NAME_ASC) } })
                                        DropdownMenuItem(text = { Text("Имя Я–А") }, onClick = { apply { onSortChanged(SortMode.NAME_DESC) } })
                                        DropdownMenuItem(text = { Text("Сначала крупные") }, onClick = { apply { onSortChanged(SortMode.SIZE_DESC) } })
                                        DropdownMenuItem(
                                            text = { Text(if (cleanupMode) "Выключить режим уборки" else "Режим уборки") },
                                            onClick = { apply { onCleanupModeChanged(!cleanupMode) } },
                                        )
                                    }

                                    DropdownMenuItem(text = { Text("Обновить") }, onClick = { apply(onRefresh) })
                                    DropdownMenuItem(text = { Text("Настройки") }, onClick = { apply(onShowSettings) })
                                }
                            }
                        },
                    )
                }

                if (state.searchVisible && !selectionMode) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChanged,
                        singleLine = true,
                        placeholder = { Text(if (inAlbum) "Поиск в альбоме" else "Поиск альбома") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        },
    ) { innerPadding ->
        when {
            !state.hasPermission -> PermissionState(innerPadding, onRequestPermission)
            state.isLoading -> MessageState("Загрузка…", innerPadding)
            !inAlbum && state.albums.isEmpty() -> MessageState("Альбомы не найдены", innerPadding)
            inAlbum && state.visibleItems.isEmpty() -> MessageState("Медиа не найдено", innerPadding)
            !inAlbum -> AlbumsGrid(
                albums = state.albums,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                onOpenAlbum = onOpenAlbum,
            )
            state.gridMode == GridMode.MOSAIC -> MosaicGrid(
                items = state.visibleItems,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                onOpenMedia = onOpenMedia,
                cleanupMode = cleanupMode,
                selectedIds = selectedIds,
                onToggleSelection = onToggleSelection,
            )
            else -> UniformGrid(
                items = state.visibleItems,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                onOpenMedia = onOpenMedia,
                cleanupMode = cleanupMode,
                selectedIds = selectedIds,
                onToggleSelection = onToggleSelection,
            )
        }
    }

    renameTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Переименовать файл") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Новое имя") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val value = renameText.trim()
                        if (value.isNotBlank()) {
                            onRenameSelected(item, value)
                            renameTarget = null
                        }
                    },
                ) {
                    Text("Переименовать")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Отмена")
                }
            },
        )
    }

    if (moveDialogVisible) {
        AlertDialog(
            onDismissRequest = { moveDialogVisible = false },
            title = {
                Text(
                    if (selectedItems.size == 1) "Переместить файл"
                    else "Переместить ${selectedItems.size} файлов"
                )
            },
            text = {
                Column {
                    Text(
                        text = "Укажи папку относительно памяти телефона. Например: Pictures/Travel/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    OutlinedTextField(
                        value = movePath,
                        onValueChange = { movePath = it },
                        singleLine = true,
                        label = { Text("Папка") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val value = movePath.trim()
                        if (value.isNotBlank()) {
                            onMoveSelected(selectedItems, value)
                            moveDialogVisible = false
                        }
                    },
                ) {
                    Text("Переместить")
                }
            },
            dismissButton = {
                TextButton(onClick = { moveDialogVisible = false }) {
                    Text("Отмена")
                }
            },
        )
    }

    if (state.settingsVisible) {
        SettingsSheet(
            quickExifEnabled = state.quickExifEnabled,
            onQuickExifChanged = onQuickExifChanged,
            onDismiss = onHideSettings,
        )
    }
}

@Composable
private fun PermissionState(innerPadding: PaddingValues, onRequestPermission: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Нужен доступ к фото и видео")
            TextButton(onClick = onRequestPermission) { Text("Разрешить доступ") }
        }
    }
}

@Composable
private fun MessageState(text: String, innerPadding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
        Text(text)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumsGrid(
    albums: List<AlbumSummary>,
    modifier: Modifier,
    onOpenAlbum: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(156.dp),
        modifier = modifier,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(albums, key = { it.name }) { album ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .combinedClickable(onClick = { onOpenAlbum(album.name) }),
            ) {
                MediaPreview(
                    item = album.cover,
                    modifier = Modifier.fillMaxWidth().height(156.dp),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    Text(album.name, maxLines = 1, fontWeight = FontWeight.Medium)
                    Text(
                        "${album.count}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MosaicGrid(
    items: List<MediaItem>,
    modifier: Modifier,
    onOpenMedia: (MediaItem) -> Unit,
    cleanupMode: Boolean,
    selectedIds: Set<Long>,
    onToggleSelection: (MediaItem) -> Unit,
) {
    val gap = if (cleanupMode) 5.dp else 3.dp

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.background(if (cleanupMode) Color(0xFFFF5A36) else Color.Transparent),
        contentPadding = PaddingValues(gap),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        itemsIndexed(
            items = items,
            key = { _, item -> item.id },
            span = { index, _ ->
                val wide = index % 8 == 0 || index % 13 == 5
                GridItemSpan(if (wide) 2 else 1)
            },
        ) { index, item ->
            val wide = index % 8 == 0 || index % 13 == 5
            val ratio = if (wide) {
                item.aspectRatio.coerceIn(1.15f, 2.1f)
            } else {
                item.aspectRatio.coerceIn(0.72f, 1.35f)
            }

            MediaTile(
                item = item,
                aspectRatio = ratio,
                onOpenMedia = onOpenMedia,
                selected = item.id in selectedIds,
                selectionMode = selectedIds.isNotEmpty(),
                onToggleSelection = onToggleSelection,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UniformGrid(
    items: List<MediaItem>,
    modifier: Modifier,
    onOpenMedia: (MediaItem) -> Unit,
    cleanupMode: Boolean,
    selectedIds: Set<Long>,
    onToggleSelection: (MediaItem) -> Unit,
) {
    val gap = if (cleanupMode) 5.dp else 3.dp

    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        modifier = modifier.background(if (cleanupMode) Color(0xFFFF5A36) else Color.Transparent),
        contentPadding = PaddingValues(gap),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        items(items, key = { it.id }) { item ->
            MediaTile(
                item = item,
                aspectRatio = 1f,
                onOpenMedia = onOpenMedia,
                selected = item.id in selectedIds,
                selectionMode = selectedIds.isNotEmpty(),
                onToggleSelection = onToggleSelection,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaTile(
    item: MediaItem,
    aspectRatio: Float,
    onOpenMedia: (MediaItem) -> Unit,
    selected: Boolean,
    selectionMode: Boolean,
    onToggleSelection: (MediaItem) -> Unit,
) {
    val shape = RoundedCornerShape(4.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .combinedClickable(
                onClick = {
                    if (selectionMode) onToggleSelection(item) else onOpenMedia(item)
                },
                onLongClick = { onToggleSelection(item) },
            ),
    ) {
        MediaPreview(
            item = item,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio.coerceAtLeast(0.45f)),
        )

        val badge = when (item.kind) {
            MediaKind.VIDEO -> "VIDEO"
            MediaKind.RAW -> "RAW"
            MediaKind.IMAGE -> null
        }

        if (badge != null) {
            Text(
                badge,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(
                        MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                        RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }

        if (selected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            )
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = "Выбрано",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp),
            )
        }
    }
}

@Composable
private fun MediaPreview(item: MediaItem, modifier: Modifier) {
    if (item.kind == MediaKind.VIDEO) {
        VideoThumbnail(item, modifier)
    } else {
        AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

@Composable
private fun VideoThumbnail(item: MediaItem, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = item.id,
    ) {
        value = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, item.uri)
                retriever.getFrameAtTime(
                    0L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                )
            } catch (_: Exception) {
                null
            } finally {
                retriever.release()
            }
        }
    }

    val frame = bitmap
    if (frame != null) {
        Image(
            bitmap = frame.asImageBitmap(),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        )
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

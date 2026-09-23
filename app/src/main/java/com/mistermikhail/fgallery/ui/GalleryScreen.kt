package com.mistermikhail.fgallery.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.videoFrameMillis
import com.mistermikhail.fgallery.data.MediaItem
import com.mistermikhail.fgallery.data.MediaKind
import com.mistermikhail.fgallery.data.ThumbnailCache
import kotlinx.coroutines.delay

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
    onOpenRecycleBin: () -> Unit,
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
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<MediaItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    var moveDialogVisible by remember { mutableStateOf(false) }
    var movePath by remember { mutableStateOf("") }

    val albumsGridState = rememberLazyGridState()
    val mosaicListState = rememberLazyListState()
    val uniformGridState = rememberLazyGridState()
    var activeLiveVideoId by remember { mutableStateOf<Long?>(null) }

    val inAlbum = state.selectedAlbum != null
    val visibleMosaicRows = remember(state.visibleItems) {
        buildMosaicRows(state.visibleItems)
    }

    val visibleVideoIds by remember(
        inAlbum,
        state.gridMode,
        state.visibleItems,
        visibleMosaicRows,
    ) {
        derivedStateOf {
            if (!inAlbum) {
                emptyList()
            } else {
                when (state.gridMode) {
                    GridMode.UNIFORM -> uniformGridState.layoutInfo.visibleItemsInfo
                        .mapNotNull { info -> state.visibleItems.getOrNull(info.index) }
                        .filter { it.kind == MediaKind.VIDEO }
                        .map { it.id }

                    GridMode.MOSAIC -> mosaicListState.layoutInfo.visibleItemsInfo
                        .flatMap { info -> visibleMosaicRows.getOrNull(info.index).orEmpty() }
                        .filter { it.kind == MediaKind.VIDEO }
                        .map { it.id }
                }
            }
        }
    }

    val mediaGridScrolling by remember(inAlbum, state.gridMode) {
        derivedStateOf {
            inAlbum && when (state.gridMode) {
                GridMode.UNIFORM -> uniformGridState.isScrollInProgress
                GridMode.MOSAIC -> mosaicListState.isScrollInProgress
            }
        }
    }

    LaunchedEffect(visibleVideoIds, mediaGridScrolling) {
        activeLiveVideoId = null

        if (mediaGridScrolling || visibleVideoIds.isEmpty()) {
            return@LaunchedEffect
        }

        // Never spin up an ExoPlayer during a fling. Wait until the grid is stable,
        // then rotate one muted live preview at a time.
        delay(700L)

        var index = 0
        while (true) {
            activeLiveVideoId = visibleVideoIds[index]
            delay(5_000L)
            index = (index + 1) % visibleVideoIds.size
        }
    }
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
                                Text(
                                    text = state.selectedAlbum ?: "FGallery",
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = if (inAlbum) {
                                        "${state.visibleItems.size} объектов"
                                    } else {
                                        "${state.albums.size} альбомов"
                                    },
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

                            Box {
                                IconButton(onClick = { sortMenuExpanded = true }) {
                                    Icon(Icons.Outlined.Sort, contentDescription = "Сортировка")
                                }
                                DropdownMenu(
                                    expanded = sortMenuExpanded,
                                    onDismissRequest = { sortMenuExpanded = false },
                                ) {
                                    SortMenuItem(
                                        text = "Сначала новые",
                                        selected = state.sortMode == SortMode.DATE_DESC,
                                    ) {
                                        sortMenuExpanded = false
                                        onSortChanged(SortMode.DATE_DESC)
                                    }
                                    SortMenuItem(
                                        text = "Сначала старые",
                                        selected = state.sortMode == SortMode.DATE_ASC,
                                    ) {
                                        sortMenuExpanded = false
                                        onSortChanged(SortMode.DATE_ASC)
                                    }
                                    SortMenuItem(
                                        text = "Имя А–Я",
                                        selected = state.sortMode == SortMode.NAME_ASC,
                                    ) {
                                        sortMenuExpanded = false
                                        onSortChanged(SortMode.NAME_ASC)
                                    }
                                    SortMenuItem(
                                        text = "Имя Я–А",
                                        selected = state.sortMode == SortMode.NAME_DESC,
                                    ) {
                                        sortMenuExpanded = false
                                        onSortChanged(SortMode.NAME_DESC)
                                    }
                                    SortMenuItem(
                                        text = "Сначала крупные",
                                        selected = state.sortMode == SortMode.SIZE_DESC,
                                    ) {
                                        sortMenuExpanded = false
                                        onSortChanged(SortMode.SIZE_DESC)
                                    }
                                    SortMenuItem(
                                        text = "Сначала мелкие",
                                        selected = state.sortMode == SortMode.SIZE_ASC,
                                    ) {
                                        sortMenuExpanded = false
                                        onSortChanged(SortMode.SIZE_ASC)
                                    }
                                }
                            }

                            if (inAlbum) {
                                IconButton(onClick = onToggleGridMode) {
                                    Icon(
                                        if (state.gridMode == GridMode.MOSAIC) {
                                            Icons.Outlined.GridOn
                                        } else {
                                            Icons.Outlined.ViewQuilt
                                        },
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

                                    DropdownMenuItem(
                                        text = { Text("Все файлы") },
                                        onClick = { apply { onFilterChanged(MediaFilter.ALL) } },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Фото") },
                                        onClick = { apply { onFilterChanged(MediaFilter.PHOTOS) } },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Видео") },
                                        onClick = { apply { onFilterChanged(MediaFilter.VIDEOS) } },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("RAW") },
                                        onClick = { apply { onFilterChanged(MediaFilter.RAW) } },
                                    )

                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (cleanupMode) {
                                                    "Выключить режим уборки"
                                                } else {
                                                    "Режим уборки"
                                                },
                                                color = Color(0xFFFF5A36),
                                            )
                                        },
                                        onClick = {
                                            apply {
                                                onCleanupModeChanged(!cleanupMode)
                                            }
                                        },
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Корзина") },
                                        onClick = { apply(onOpenRecycleBin) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Обновить") },
                                        onClick = { apply(onRefresh) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Настройки") },
                                        onClick = { apply(onShowSettings) },
                                    )
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
                        placeholder = {
                            Text(if (inAlbum) "Поиск в альбоме" else "Поиск альбома")
                        },
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
                state = albumsGridState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onOpenAlbum = onOpenAlbum,
                thumbnailCacheVersion = state.thumbnailCacheVersion,
                cleanupMode = cleanupMode,
            )

            state.gridMode == GridMode.MOSAIC -> MosaicGrid(
                items = state.visibleItems,
                state = mosaicListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onOpenMedia = onOpenMedia,
                cleanupMode = cleanupMode,
                selectedIds = selectedIds,
                onToggleSelection = onToggleSelection,
                thumbnailCacheVersion = state.thumbnailCacheVersion,
                activeLiveVideoId = activeLiveVideoId,
            )

            else -> UniformGrid(
                items = state.visibleItems,
                state = uniformGridState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onOpenMedia = onOpenMedia,
                cleanupMode = cleanupMode,
                selectedIds = selectedIds,
                onToggleSelection = onToggleSelection,
                thumbnailCacheVersion = state.thumbnailCacheVersion,
                activeLiveVideoId = activeLiveVideoId,
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
                    if (selectedItems.size == 1) {
                        "Переместить файл"
                    } else {
                        "Переместить ${selectedItems.size} файлов"
                    }
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
private fun SortMenuItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(if (selected) "✓  $text" else "   $text")
        },
        onClick = onClick,
    )
}

@Composable
private fun PermissionState(
    innerPadding: PaddingValues,
    onRequestPermission: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Нужен доступ к фото и видео")
            TextButton(onClick = onRequestPermission) {
                Text("Разрешить доступ")
            }
        }
    }
}

@Composable
private fun MessageState(
    text: String,
    innerPadding: PaddingValues,
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumsGrid(
    albums: List<AlbumSummary>,
    state: LazyGridState,
    modifier: Modifier,
    onOpenAlbum: (String) -> Unit,
    thumbnailCacheVersion: Long,
    cleanupMode: Boolean,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(156.dp),
        state = state,
        modifier = modifier.background(
            if (cleanupMode) Color(0xFFFF5A36) else MaterialTheme.colorScheme.background
        ),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        gridItems(albums, key = { it.name }) { album ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .combinedClickable(onClick = { onOpenAlbum(album.name) }),
            ) {
                MediaPreview(
                    item = album.cover,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(156.dp),
                    thumbnailCacheVersion = thumbnailCacheVersion,
                    livePreview = false,
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.72f)
                        )
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    Text(
                        album.name,
                        maxLines = 1,
                        fontWeight = FontWeight.Medium,
                    )
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

private fun buildMosaicRows(items: List<MediaItem>): List<List<MediaItem>> {
    if (items.isEmpty()) return emptyList()

    val rows = mutableListOf<List<MediaItem>>()
    var current = mutableListOf<MediaItem>()
    var ratioSum = 0f

    fun flush() {
        if (current.isNotEmpty()) {
            rows += current.toList()
            current = mutableListOf()
            ratioSum = 0f
        }
    }

    items.forEach { item ->
        val ratio = item.aspectRatio.coerceIn(0.55f, 2.4f)
        current += item
        ratioSum += ratio

        if ((ratioSum >= 2.75f && current.size >= 2) || current.size >= 4) {
            flush()
        }
    }

    flush()
    return rows
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MosaicGrid(
    items: List<MediaItem>,
    state: LazyListState,
    modifier: Modifier,
    onOpenMedia: (MediaItem) -> Unit,
    cleanupMode: Boolean,
    selectedIds: Set<Long>,
    onToggleSelection: (MediaItem) -> Unit,
    thumbnailCacheVersion: Long,
    activeLiveVideoId: Long?,
) {
    val gap = if (cleanupMode) 5.dp else 3.dp
    val rows = remember(items) { buildMosaicRows(items) }

    LazyColumn(
        state = state,
        modifier = modifier.background(
            if (cleanupMode) Color(0xFFFF5A36) else Color.Transparent
        ),
        contentPadding = PaddingValues(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        lazyItems(
            items = rows,
            key = { row -> row.joinToString(separator = ":") { it.id.toString() } },
        ) { row ->
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val ratios = row.map { it.aspectRatio.coerceIn(0.55f, 2.4f) }
                val ratioSum = ratios.sum().coerceAtLeast(0.5f)
                val totalGap = gap.value * (row.size - 1).coerceAtLeast(0)
                val availableWidth = (maxWidth.value - totalGap).coerceAtLeast(1f)
                val rowHeight = (availableWidth / ratioSum)
                    .dp
                    .coerceIn(108.dp, 225.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight),
                ) {
                    row.forEachIndexed { index, item ->
                        if (index > 0) {
                            Spacer(modifier = Modifier.width(gap))
                        }

                        MediaTile(
                            item = item,
                            modifier = Modifier
                                .weight(ratios[index])
                                .fillMaxHeight(),
                            onOpenMedia = onOpenMedia,
                            selected = item.id in selectedIds,
                            selectionMode = selectedIds.isNotEmpty(),
                            onToggleSelection = onToggleSelection,
                            thumbnailCacheVersion = thumbnailCacheVersion,
                            livePreview = false,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UniformGrid(
    items: List<MediaItem>,
    state: LazyGridState,
    modifier: Modifier,
    onOpenMedia: (MediaItem) -> Unit,
    cleanupMode: Boolean,
    selectedIds: Set<Long>,
    onToggleSelection: (MediaItem) -> Unit,
    thumbnailCacheVersion: Long,
    activeLiveVideoId: Long?,
) {
    val gap = if (cleanupMode) 5.dp else 3.dp

    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        state = state,
        modifier = modifier.background(
            if (cleanupMode) Color(0xFFFF5A36) else Color.Transparent
        ),
        contentPadding = PaddingValues(gap),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        gridItems(items, key = { it.id }) { item ->
            MediaTile(
                item = item,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                onOpenMedia = onOpenMedia,
                selected = item.id in selectedIds,
                selectionMode = selectedIds.isNotEmpty(),
                onToggleSelection = onToggleSelection,
                thumbnailCacheVersion = thumbnailCacheVersion,
                livePreview = item.id == activeLiveVideoId,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaTile(
    item: MediaItem,
    modifier: Modifier,
    onOpenMedia: (MediaItem) -> Unit,
    selected: Boolean,
    selectionMode: Boolean,
    onToggleSelection: (MediaItem) -> Unit,
    thumbnailCacheVersion: Long,
    livePreview: Boolean,
) {
    val shape = RoundedCornerShape(4.dp)

    Box(
        modifier = modifier
            .then(
                if (selected) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = shape,
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .combinedClickable(
                onClick = {
                    if (selectionMode) {
                        onToggleSelection(item)
                    } else {
                        onOpenMedia(item)
                    }
                },
                onLongClick = { onToggleSelection(item) },
            ),
    ) {
        MediaPreview(
            item = item,
            modifier = Modifier.fillMaxSize(),
            thumbnailCacheVersion = thumbnailCacheVersion,
            livePreview = livePreview,
        )

        when (item.kind) {
            MediaKind.VIDEO -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .size(22.dp)
                        .background(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                            RoundedCornerShape(11.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Видео",
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            MediaKind.RAW -> {
                Text(
                    "RAW",
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

            MediaKind.IMAGE -> Unit
        }

        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
private fun MediaPreview(
    item: MediaItem,
    modifier: Modifier,
    thumbnailCacheVersion: Long,
    livePreview: Boolean,
) {
    val context = LocalContext.current
    val fastCachedFile = remember(
        item.id,
        item.dateModifiedMillis,
        thumbnailCacheVersion,
    ) {
        ThumbnailCache.fileFor(
            context = context,
            item = item,
            highQuality = false,
        )
    }

    val highCachedFile = remember(
        item.id,
        item.dateModifiedMillis,
        thumbnailCacheVersion,
    ) {
        ThumbnailCache.fileFor(
            context = context,
            item = item,
            highQuality = true,
        )
    }

    val cachedModel = when {
        highCachedFile.exists() -> highCachedFile
        fastCachedFile.exists() -> fastCachedFile
        else -> null
    }

    when {
        item.kind == MediaKind.VIDEO && livePreview -> {
            InlineVideoPreview(
                item = item,
                modifier = modifier,
            )
        }

        cachedModel != null -> {
            AsyncImage(
                model = cachedModel,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }

        else -> {
            val fallbackRequest = remember(item.uri, item.dateModifiedMillis) {
                ImageRequest.Builder(context)
                    .data(item.uri)
                    .size(960, 960)
                    .build()
            }

            if (item.kind == MediaKind.VIDEO) {
                VideoThumbnail(
                    item = item,
                    modifier = modifier,
                )
            } else {
                AsyncImage(
                    model = fallbackRequest,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
    }
}

@Composable
private fun InlineVideoPreview(
    item: MediaItem,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val player = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(PlayerMediaItem.fromUri(item.uri))
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            seekTo(750L)
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                isClickable = false
                isFocusable = false
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun VideoThumbnail(
    item: MediaItem,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val frameMillis = if (item.durationMillis > 2_000L) {
        item.durationMillis / 2L
    } else {
        1_000L
    }

    val request = remember(item.uri, frameMillis) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .videoFrameMillis(frameMillis)
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = item.name,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

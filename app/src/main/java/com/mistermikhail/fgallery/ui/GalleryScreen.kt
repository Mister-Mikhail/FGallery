package com.mistermikhail.fgallery.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewQuilt
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mistermikhail.fgallery.data.MediaItem
import com.mistermikhail.fgallery.data.MediaKind

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
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val inAlbum = state.selectedAlbum != null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
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
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
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
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                fun apply(action: () -> Unit) { menuExpanded = false; action() }
                                DropdownMenuItem(text = { Text("Все файлы") }, onClick = { apply { onFilterChanged(MediaFilter.ALL) } })
                                DropdownMenuItem(text = { Text("Фото") }, onClick = { apply { onFilterChanged(MediaFilter.PHOTOS) } })
                                DropdownMenuItem(text = { Text("Видео") }, onClick = { apply { onFilterChanged(MediaFilter.VIDEOS) } })
                                DropdownMenuItem(text = { Text("RAW") }, onClick = { apply { onFilterChanged(MediaFilter.RAW) } })
                                DropdownMenuItem(text = { Text("Обновить") }, onClick = { apply(onRefresh) })
                            }
                        }
                    },
                )

                if (state.searchVisible) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChanged,
                        singleLine = true,
                        placeholder = { Text(if (inAlbum) "Поиск в альбоме" else "Поиск альбома") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
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
            )
            else -> UniformGrid(
                items = state.visibleItems,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                onOpenMedia = onOpenMedia,
            )
        }
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
    Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { Text(text) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumsGrid(albums: List<AlbumSummary>, modifier: Modifier, onOpenAlbum: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(156.dp),
        modifier = modifier,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(albums, key = { it.name }) { album ->
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                    .combinedClickable(onClick = { onOpenAlbum(album.name) }),
            ) {
                AsyncImage(
                    model = album.cover.uri,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(156.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    Text(album.name, maxLines = 1, fontWeight = FontWeight.Medium)
                    Text("${album.count}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MosaicGrid(items: List<MediaItem>, modifier: Modifier, onOpenMedia: (MediaItem) -> Unit) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(112.dp),
        modifier = modifier,
        contentPadding = PaddingValues(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalItemSpacing = 3.dp,
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { _, item ->
            MediaTile(item, item.aspectRatio.coerceIn(0.62f, 1.65f), onOpenMedia)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UniformGrid(items: List<MediaItem>, modifier: Modifier, onOpenMedia: (MediaItem) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        modifier = modifier,
        contentPadding = PaddingValues(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items(items, key = { it.id }) { item -> MediaTile(item, 1f, onOpenMedia) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaTile(item: MediaItem, aspectRatio: Float, onOpenMedia: (MediaItem) -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).combinedClickable(
            onClick = { onOpenMedia(item) },
        ),
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth()
                .height((112.dp / aspectRatio.coerceAtLeast(0.5f)).coerceIn(82.dp, 210.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )

        val badge = when (item.kind) {
            MediaKind.VIDEO -> "VIDEO"
            MediaKind.RAW -> "RAW"
            MediaKind.IMAGE -> null
        }
        if (badge != null) {
            Text(
                badge,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

package com.mistermikhail.fgallery.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MoveDestinationScreen(
    albums: List<AlbumSummary>,
    movingCount: Int,
    query: String,
    selectedAlbum: AlbumSummary?,
    onQueryChanged: (String) -> Unit,
    onSelectAlbum: (AlbumSummary) -> Unit,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onBackFromAlbum: () -> Unit,
    onCancel: () -> Unit,
    onMoveHere: (AlbumSummary) -> Unit,
    onChooseOtherFolder: () -> Unit,
    onCreateFolder: () -> Unit,
) {
    var sortMode by remember { mutableStateOf(0) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val filtered = remember(albums, query, sortMode) {
        val matches = albums.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        when (sortMode) {
            1 -> matches.sortedByDescending { it.name.lowercase() }
            2 -> matches.sortedByDescending { it.newestDateMillis }
            3 -> matches.sortedBy { it.newestDateMillis }
            4 -> matches.sortedByDescending { it.count }
            5 -> matches.sortedBy { it.count }
            else -> matches.sortedBy { it.name.lowercase() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = if (selectedAlbum != null) onBackFromAlbum else onCancel,
                    ) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = {
                    Column {
                        Text(
                            selectedAlbum?.name ?: "Куда переместить",
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "$movingCount объектов",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(Icons.Outlined.Sort, contentDescription = "Сортировка папок")
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false },
                        ) {
                            listOf(
                                "Имя: А–Я", "Имя: Я–А",
                                "Сначала новые", "Сначала старые",
                                "Больше файлов", "Меньше файлов",
                            ).forEachIndexed { index, label ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { sortMode = index; sortMenuExpanded = false },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onCreateFolder) {
                        Icon(
                            Icons.Outlined.CreateNewFolder,
                            contentDescription = "Создать папку",
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (selectedAlbum != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(12.dp),
                ) {
                    Button(
                        onClick = { onMoveHere(selectedAlbum) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Переместить сюда")
                    }
                    TextButton(
                        onClick = onChooseOtherFolder,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Другая или новая папка…")
                    }
                }
            } else {
                TextButton(
                    onClick = onChooseOtherFolder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(8.dp),
                ) {
                    Text("Найти или создать другую папку…")
                }
            }
        },
    ) { innerPadding ->
        if (selectedAlbum != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp),
                ) {
                    AsyncImage(
                        model = selectedAlbum.cover.uri,
                        contentDescription = selectedAlbum.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                    Text(
                        selectedAlbum.name,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        "${selectedAlbum.count} объектов · ${selectedAlbum.cover.relativePath}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChanged,
                    singleLine = true,
                    placeholder = { Text("Поиск папки") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(156.dp),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(filtered, key = { it.cover.folderTarget.ifBlank { "${it.cover.storageId}/${it.cover.relativePath}" } }) { album ->
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .combinedClickable(
                                    onClick = { onSelectAlbum(album) },
                                    onLongClick = { onOpenAlbum(album) },
                                ),
                        ) {
                            AsyncImage(
                                model = album.cover.uri,
                                contentDescription = album.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp),
                            )
                            Text(
                                album.cover.relativePath,
                                maxLines = 2,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                album.name,
                                maxLines = 1,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                            IconButton(onClick = { onOpenAlbum(album) }) {
                                Icon(Icons.Outlined.ChevronRight, contentDescription = "Открыть папку ${album.name}")
                            }
                            }
                        }
                    }
                }
            }
        }
    }
}


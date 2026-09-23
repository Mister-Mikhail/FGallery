package com.mistermikhail.fgallery.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mistermikhail.fgallery.data.MediaItem
import com.mistermikhail.fgallery.data.ThumbnailCache

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    items: List<MediaItem>,
    selectedIds: Set<Long>,
    onBack: () -> Unit,
    onToggleSelection: (MediaItem) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onRestore: (List<MediaItem>) -> Unit,
    onDeleteForever: (List<MediaItem>) -> Unit,
) {
    val selectedItems = items.filter { it.id in selectedIds }
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = {
                    Column {
                        Text("Корзина", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (selectedItems.isEmpty()) {
                                "${items.size} объектов"
                            } else {
                                "${selectedItems.size} выбрано"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    if (selectedItems.isNotEmpty()) {
                        IconButton(onClick = { onRestore(selectedItems) }) {
                            Icon(
                                Icons.Outlined.RestoreFromTrash,
                                contentDescription = "Восстановить",
                            )
                        }
                        IconButton(onClick = { onDeleteForever(selectedItems) }) {
                            Icon(
                                Icons.Outlined.DeleteForever,
                                contentDescription = "Удалить навсегда",
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            enabled = items.isNotEmpty(),
                        ) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Меню корзины")
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Выбрать всё") },
                                onClick = {
                                    menuExpanded = false
                                    onSelectAll()
                                },
                            )

                            if (selectedItems.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Снять выделение") },
                                    onClick = {
                                        menuExpanded = false
                                        onClearSelection()
                                    },
                                )
                            }

                            DropdownMenuItem(
                                text = { Text("Очистить корзину") },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteForever(items)
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Корзина пуста",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                items(items, key = { item -> item.uri.toString() }) { item ->
                    RecycleBinTile(
                        item = item,
                        selected = item.id in selectedIds,
                        onToggleSelection = onToggleSelection,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecycleBinTile(
    item: MediaItem,
    selected: Boolean,
    onToggleSelection: (MediaItem) -> Unit,
) {
    val shape = RoundedCornerShape(4.dp)
    val context = LocalContext.current
    val cachedFile = remember(item.id, item.dateModifiedMillis) {
        ThumbnailCache.fileFor(context, item)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
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
                onClick = { onToggleSelection(item) },
                onLongClick = { onToggleSelection(item) },
            ),
    ) {
        AsyncImage(
            model = if (cachedFile.exists()) cachedFile else item.uri,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )

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

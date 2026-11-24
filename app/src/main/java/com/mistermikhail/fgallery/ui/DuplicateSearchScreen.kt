package com.mistermikhail.fgallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mistermikhail.fgallery.data.CatalogMediaRow
import com.mistermikhail.fgallery.data.ExactDuplicateReviewItem
import com.mistermikhail.fgallery.data.GalleryThumb
import com.mistermikhail.fgallery.data.ThumbnailCache
import com.mistermikhail.fgallery.data.formatDuplicateBytes

private val duplicateColors = darkColorScheme(
    primary = Color.White, onPrimary = Color.Black,
    secondaryContainer = Color(0xFFE5E5E5), onSecondaryContainer = Color.Black,
    surfaceContainer = Color(0xFF171717), surfaceContainerHigh = Color(0xFF202020),
    surfaceContainerHighest = Color(0xFF242424), surfaceTint = Color.Transparent,
    background = Color.Black,
    onBackground = Color(0xFFF1F1F1),
    surface = Color.Black,
    onSurface = Color(0xFFF1F1F1),
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFFDDDDDD),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DuplicateSearchScreen(
    controller: DuplicateSearchController,
    onClose: () -> Unit,
    onRequestAccess: () -> Unit,
) {
    val state by controller.state.collectAsState()
    val busy = state.trashInProgress || state.preparingConfirmation
    var grid by rememberSaveable { mutableStateOf(false) }
    val preview = state.preview
    fun back() {
        if (busy) return
        when {
            preview != null -> controller.closePreview()
            state.confirmation != null -> controller.dismissConfirmation()
            state.group != null -> controller.showGroup(null)
            else -> onClose()
        }
    }
    BackHandler { back() }

    MaterialTheme(colorScheme = duplicateColors) {
        Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = duplicateColors.onBackground) {
            Box(Modifier.fillMaxSize()) {
                Scaffold(
                    modifier = Modifier.testTag("duplicate-search").semantics {
                        if (preview != null) hideFromAccessibility()
                    },
                    containerColor = Color.Black,
                    contentColor = duplicateColors.onBackground,
                    topBar = {
                        Column {
                            TopAppBar(
                                title = { Text(if (state.tab == DuplicateTab.EXACT) "Точные дубликаты" else "Похожие изображения") },
                                navigationIcon = {
                                    IconButton(onClick = ::back, enabled = !busy) {
                                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад")
                                    }
                                },
                                actions = {
                                    if (state.tab == DuplicateTab.EXACT && !state.catalogue?.cards.isNullOrEmpty()) {
                                        IconButton(onClick = { grid = !grid }) {
                                            Icon(if (grid) Icons.Outlined.ViewList else Icons.Outlined.GridView,
                                                if (grid) "Список" else "Сетка")
                                        }
                                    }
                                    IconButton(onClick = { controller.refresh() }, enabled = !busy && state.scanProgress == null) {
                                        Icon(Icons.Outlined.Refresh, "Обновить поиск")
                                    }
                                },
                            )
                            if (state.group == null) {
                                PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                                    Tab(
                                        text = { Text("Точные копии") },
                                        selected = state.tab == DuplicateTab.EXACT,
                                        onClick = { controller.showTab(DuplicateTab.EXACT) },
                                        enabled = !busy,
                                    )
                                    Tab(
                                        text = { Text("Похожие изображения") },
                                        selected = state.tab == DuplicateTab.VISUAL,
                                        onClick = { controller.showTab(DuplicateTab.VISUAL) },
                                        enabled = !busy,
                                    )
                                }
                            }
                        }
                    },
                    bottomBar = {
                        if (state.tab == DuplicateTab.EXACT && state.selected.isNotEmpty()) {
                            DuplicateActions(state, controller)
                        }
                    },
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        state.notice?.let { SearchMessage(it) }
                        val catalogue = state.catalogue
                        when {
                            state.scanProgress != null -> {
                                SearchMessage("Ищем одинаковые файлы")
                                SearchMessage("Во всех доступных папках устройства")
                                SearchMessage(state.scanProgress.orEmpty())
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                OutlinedButton(onClick = onClose, modifier = Modifier.padding(12.dp)) { Text("Отменить") }
                            }
                            state.error != null -> SearchMessage(state.error.orEmpty())
                            catalogue?.requiresAccess == true -> {
                                SearchMessage("Разрешите доступ к файлам, чтобы начать поиск.")
                                TextButton(onClick = onRequestAccess) { Text("Настроить доступ") }
                            }
                            catalogue != null -> {
                                if (catalogue.disconnectedStorages.isNotEmpty()) {
                                    SearchMessage("Носители отключены: ${catalogue.disconnectedStorages.joinToString()}")
                                }
                                if (catalogue.pendingFingerprints > 0 && state.group == null && state.tab == DuplicateTab.EXACT) {
                                    SearchMessage("Для ${catalogue.pendingFingerprints} файлов проверка пока не завершена или чтение недоступно.")
                                }
                                when (state.tab) {
                                    DuplicateTab.EXACT -> ExactCopies(state, catalogue, controller, busy, grid, onClose)
                                    DuplicateTab.VISUAL -> VisualCandidates(state, catalogue, controller)
                                }
                            }
                        }
                    }
                }
                if (preview != null) {
                    DisposableEffect(Unit) {
                        ThumbnailCache.setViewerBusy(true)
                        onDispose { ThumbnailCache.setViewerBusy(false) }
                    }
                    Surface(Modifier.fillMaxSize(), color = Color.Black) {
                        ViewerScreen(
                            items = remember(preview) { listOf(preview) },
                            initialItem = preview,
                            onBack = controller::closePreview,
                            quickExifEnabled = true,
                            cleanupMode = false,
                            onTrash = {},
                            onShare = {},
                            onCrop = {},
                            onRename = { _, _ -> },
                            onMove = {},
                            readOnly = true,
                        )
                    }
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
                        color = duplicateColors.surface,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        IconButton(onClick = controller::closePreview) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "К результатам")
                        }
                    }
                }
            }
            state.confirmation?.let { consent ->
                AlertDialog(
                    onDismissRequest = controller::dismissConfirmation,
                    title = { Text("Подтверждение корзины") },
                    text = { Text(consent.prompt.message) },
                    confirmButton = {
                        TextButton(onClick = { controller.confirmTrash(consent) }) { Text("Проверить и переместить") }
                    },
                    dismissButton = {
                        TextButton(onClick = controller::dismissConfirmation) { Text("Отмена") }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DuplicateActions(state: DuplicateSearchUiState, controller: DuplicateSearchController) {
    val enabled = !state.trashInProgress && !state.preparingConfirmation
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("duplicate-action-bar"),
        tonalElevation = 6.dp,
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = controller::clearSelection, enabled = enabled) {
                    Icon(Icons.Outlined.Close, "Снять выбор")
                }
                Column(Modifier.weight(1f)) {
                    Text("Выбрано ${state.selected.size}", style = MaterialTheme.typography.titleSmall)
                    val bytes = remember(state.selected, state.catalogue) {
                        state.selected.sumOf { state.catalogue?.rows?.get(it)?.sizeBytes ?: 0L }
                    }
                    Text(formatDuplicateBytes(bytes), style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = controller::requestConfirmation, enabled = enabled) { Text("В корзину…") }
            }
            if (!enabled) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ExactCopies(
    state: DuplicateSearchUiState,
    catalogue: DuplicateCatalogue,
    controller: DuplicateSearchController,
    busy: Boolean,
    grid: Boolean,
    onClose: () -> Unit,
) {
    var type by rememberSaveable { mutableStateOf("Все") }
    var large by rememberSaveable { mutableStateOf(false) }
    val cards = remember(catalogue, type, large) {
        catalogue.cards.filter { card ->
            val kind = catalogue.rows.getValue(card.items.first().uri).kind
            (!large || card.sizeBytes > 5L * 1024 * 1024) && when (type) {
                "Фото" -> kind in setOf("IMAGE", "RAW", "SVG")
                "Видео" -> kind == "VIDEO"
                else -> true
            }
        }
    }
    fun resetChoice() {
        controller.clearSelection()
        controller.showGroup(null)
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (grid) 2 else 1),
        modifier = Modifier.fillMaxSize().testTag(if (state.group == null) "duplicate-groups" else "duplicate-copy-grid"),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item("summary", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                if (!catalogue.valid) SearchMessage("Неоднозначные записи каталога. Обновите поиск.")
                else if (catalogue.cards.isEmpty()) {
                    SearchMessage(if (catalogue.pendingFingerprints == 0) "Точных дубликатов не найдено" else "Подтверждённых групп пока нет")
                    Button(onClick = onClose) { Text("К альбомам") }
                } else {
                    catalogue.reclaimableBytes?.let {
                        Text("Можно освободить ${formatDuplicateBytes(it)}", style = MaterialTheme.typography.titleLarge)
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Все", "Фото", "Видео").forEach { label ->
                            FilterChip(type == label, { if (type != label) { resetChoice(); type = label } }, { Text(label) }, enabled = !busy)
                        }
                        FilterChip(large, { resetChoice(); large = !large }, { Text("> 5 МБ") }, enabled = !busy)
                    }
                    if (cards.isEmpty()) SearchMessage("По этим фильтрам совпадений нет")
                    else TextButton(onClick = { controller.selectCopies(cards) }, enabled = !busy) { Text("Выбрать лишние копии") }
                }
            }
        }
        if (catalogue.valid) cards.forEach { card ->
            val expanded = state.group === card
            item("group/${card.sha256}", span = { GridItemSpan(maxLineSpan) }) {
                Surface(shape = MaterialTheme.shapes.medium, color = duplicateColors.surfaceContainer) {
                    Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { controller.showGroup(if (expanded) null else card) }
                        .testTag("group-${card.sha256}").padding(8.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val row = catalogue.rows.getValue(card.items.first().uri)
                        DuplicateTile(row, Modifier.size(48.dp), interactive = false) {}
                        Column(Modifier.weight(1f)) {
                            Text("Файлов: ${card.items.size}", style = MaterialTheme.typography.titleMedium)
                            if (catalogue.reclaimableBytes != null) Text("Можно освободить ${formatDuplicateBytes(card.sizeBytes * card.items.lastIndex)}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                    }
                }
            }
            if (expanded) items(card.items, key = { "file/${it.uri}" }) { item ->
                val row = catalogue.rows.getValue(item.uri)
                val open = { controller.openPreview(item.uri) }
                Surface(Modifier.fillMaxWidth().testTag("duplicate-card-${item.uri}"), shape = MaterialTheme.shapes.small,
                    color = duplicateColors.surfaceContainer) {
                    if (grid) Column {
                        Box {
                            DuplicateTile(row, onOpen = open)
                            Box(Modifier.align(Alignment.TopEnd)) { CopyCheck(item, state, controller, busy) }
                        }
                        CopyInfo(item, row, Modifier.clickable(onClick = open).padding(8.dp))
                    } else Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DuplicateTile(row, Modifier.size(60.dp), onOpen = open)
                        CopyInfo(item, row, Modifier.weight(1f).clickable(onClick = open))
                        CopyCheck(item, state, controller, busy)
                    }
                }
            }
        }
    }
}

@Composable
private fun CopyCheck(item: ExactDuplicateReviewItem, state: DuplicateSearchUiState, controller: DuplicateSearchController, busy: Boolean) {
    if (!item.protectedOriginal) Surface(shape = MaterialTheme.shapes.small, color = duplicateColors.surfaceContainer) {
        Checkbox(item.uri in state.selected, { controller.setSelected(item.uri, it) }, enabled = !busy,
            modifier = Modifier.testTag("duplicate-select-${item.uri}").semantics { contentDescription = "Выбрать копию ${item.displayName}" })
    }
}

@Composable
private fun CopyInfo(item: ExactDuplicateReviewItem, row: CatalogMediaRow, modifier: Modifier) {
    Column(modifier) {
        Text(item.displayName.ifBlank { "Без имени" }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        Text(row.relativePath.ifBlank { row.folderTarget.ifBlank { row.storageName } }, maxLines = 2,
            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = duplicateColors.onSurfaceVariant)
        Text(formatDuplicateBytes(row.sizeBytes), style = MaterialTheme.typography.bodySmall)
        if (item.protectedOriginal) Text("Оставить", style = MaterialTheme.typography.labelMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VisualCandidates(
    state: DuplicateSearchUiState,
    catalogue: DuplicateCatalogue,
    controller: DuplicateSearchController,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("duplicate-visual-list"),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item("visual-summary") {
            Column {
                Text("Похожие изображения (кандидаты)", style = MaterialTheme.typography.titleMedium)
                Text("Результаты проверки доступны только для просмотра.", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = state.visualPage > 0,
                        onClick = { controller.showVisualPage(state.visualPage - 1) },
                    ) { Text("Предыдущая") }
                    OutlinedButton(
                        enabled = state.visualPage + 1 < catalogue.visualPageCount,
                        onClick = { controller.showVisualPage(state.visualPage + 1) },
                    ) { Text("Следующая") }
                }
                Text("Страница ${state.visualPage + 1} из ${catalogue.visualPageCount}")
            }
        }
        val pairs = state.visualPairs
        when {
            state.visualError != null -> item("visual-error") {
                SearchMessage(state.visualError)
                TextButton(onClick = { controller.showVisualPage(state.visualPage) }) { Text("Повторить") }
            }
            pairs == null -> item("visual-progress") {
                SearchMessage("Ищем кандидатов на этой странице…")
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            pairs.isEmpty() -> item("visual-empty") { SearchMessage("На этой странице кандидатов нет.") }
            else -> items(pairs, key = { catalogue.rows.getValue(it.first).uri + "\n" + it.second }) { pair ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(pair.first, pair.second).forEach { uri ->
                                val row = catalogue.rows.getValue(uri)
                                Column(Modifier.weight(1f)) {
                                    DuplicateTile(row) { controller.openPreview(uri) }
                                    Text(row.name, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Text(
                            if (state.visualChecking == pair) "Проверяем исходные пиксели…"
                            else state.visualStatuses[pair] ?: "Не проверено"
                        )
                        OutlinedButton(
                            enabled = state.visualChecking == null,
                            onClick = { controller.checkVisual(pair) },
                        ) { Text("Проверить исходные пиксели") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicateTile(row: CatalogMediaRow, modifier: Modifier = Modifier.fillMaxWidth().height(112.dp), interactive: Boolean = true, onOpen: () -> Unit) {
    val media = remember(row) { row.toDuplicateMediaItem() }
    val model = remember(media) { media?.let { GalleryThumb(it, highQuality = false) } }
    var failed by remember(model) { mutableStateOf(false) }
    Surface(
        modifier = if (!interactive) modifier else modifier.clickable(enabled = media != null, onClick = onOpen).testTag("duplicate-preview-${row.uri}")
            .semantics { contentDescription = "Открыть ${row.name}" },
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (failed || model == null) {
                Text(
                    if (model == null) "Формат: ${row.kind}" else "Нет миниатюры · открыть",
                    modifier = Modifier.padding(6.dp).testTag("duplicate-preview-fallback-${row.uri}"),
                    style = MaterialTheme.typography.labelMedium,
                )
            } else {
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { failed = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun SearchMessage(message: String) {
    Text(message, Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
}

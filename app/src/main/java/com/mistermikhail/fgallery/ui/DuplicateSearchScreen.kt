package com.mistermikhail.fgallery.ui

import androidx.activity.compose.BackHandler
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
    background = Color.Black,
    onBackground = Color(0xFFF1F1F1),
    surface = Color(0xFF161616),
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
                                title = { Text(if (state.group == null) "Дубликаты" else "Точные копии") },
                                navigationIcon = {
                                    IconButton(onClick = ::back, enabled = !busy) {
                                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад")
                                    }
                                },
                                actions = {
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
                                SearchMessage(state.scanProgress.orEmpty())
                                LinearProgressIndicator(Modifier.fillMaxWidth())
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
                                    DuplicateTab.EXACT -> ExactCopies(state, catalogue, controller, busy)
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
        Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text("Выбрано ${state.selected.size}", style = MaterialTheme.typography.titleSmall)
            if (state.trashInProgress || state.preparingConfirmation) {
                Text(
                    if (state.trashInProgress) "Проверяем SHA-256 и переносим копии…" else "Проверяем выбор…",
                    style = MaterialTheme.typography.bodySmall,
                )
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = controller::requestConfirmation, enabled = enabled) { Text("В корзину…") }
                OutlinedButton(onClick = controller::clearSelection, enabled = enabled) { Text("Снять выбор") }
            }
        }
    }
}

@Composable
private fun ExactCopies(
    state: DuplicateSearchUiState,
    catalogue: DuplicateCatalogue,
    controller: DuplicateSearchController,
    busy: Boolean,
) {
    val group = state.group
    if (group != null) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().testTag("duplicate-copy-grid"),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "group-heading", span = { GridItemSpan(maxLineSpan) }) {
                SearchMessage("${group.items.size} файлов · ${formatDuplicateBytes(group.sizeBytes)} каждый")
            }
            items(group.items, key = { it.uri }) { item ->
                ExactCopyCard(
                    item, catalogue.rows.getValue(item.uri), item.uri in state.selected, !busy,
                    onOpen = { controller.openPreview(item.uri) },
                    onSelect = { controller.setSelected(item.uri, it) },
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("duplicate-groups"),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item("summary") {
                Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Вся доступная память", style = MaterialTheme.typography.titleMedium)
                    when {
                        !catalogue.valid -> Text("Каталог содержит неоднозначные записи. Выбор заблокирован. Обновите поиск.")
                        catalogue.cards.isEmpty() -> Text(
                            if (catalogue.pendingFingerprints == 0) "Точных копий не найдено" else "Подтверждённых групп пока нет"
                        )
                        else -> {
                            Text("Групп: ${catalogue.cards.size} · файлов: ${catalogue.duplicateFileCount}")
                            Text(
                                "Можно освободить после очистки корзины: " +
                                    (catalogue.reclaimableBytes?.let(::formatDuplicateBytes) ?: "объём превышает предел подсчёта"),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            if (catalogue.valid) items(catalogue.cards, key = { it.sha256 }) { card ->
                val keeper = card.items.first()
                Card(
                    onClick = { controller.showGroup(card) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(keeper.displayName.ifBlank { "Без имени" }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${card.items.size} файла(ов) · ${formatDuplicateBytes(card.sizeBytes)} каждый",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExactCopyCard(
    item: ExactDuplicateReviewItem,
    row: CatalogMediaRow,
    selected: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onSelect: (Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth().testTag("duplicate-card-${item.uri}")) {
        DuplicateTile(row, onOpen = onOpen)
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.displayName.ifBlank { "Без имени" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(formatDuplicateBytes(row.sizeBytes), style = MaterialTheme.typography.bodySmall)
            Text(
                "${row.storageName.ifBlank { "Неизвестный носитель" }} / " +
                    row.relativePath.ifBlank { row.folderTarget.ifBlank { "Папка неизвестна" } },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.protectedOriginal) {
                Text("Сохранить · защищено", style = MaterialTheme.typography.labelLarge)
                Text(item.explanation, style = MaterialTheme.typography.bodySmall)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = onSelect,
                        enabled = enabled,
                        modifier = Modifier.testTag("duplicate-select-${item.uri}")
                            .semantics { contentDescription = "Выбрать копию ${row.name}" },
                    )
                    Text("Копия", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
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
private fun DuplicateTile(row: CatalogMediaRow, onOpen: () -> Unit) {
    val media = remember(row) { row.toDuplicateMediaItem() }
    val model = remember(media) { media?.let { GalleryThumb(it, highQuality = false) } }
    var failed by remember(model) { mutableStateOf(false) }
    Surface(
        onClick = onOpen,
        enabled = media != null,
        modifier = Modifier.fillMaxWidth().height(112.dp).testTag("duplicate-preview-${row.uri}")
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
                    contentScale = ContentScale.Fit,
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

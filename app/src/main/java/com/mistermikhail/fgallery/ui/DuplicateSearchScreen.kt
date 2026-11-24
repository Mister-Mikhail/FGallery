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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
    val comparisonOpen = state.visualComparison != null
    fun back() {
        if (busy) return
        when {
            preview != null -> controller.closePreview()
            state.confirmation != null -> controller.dismissConfirmation()
            comparisonOpen -> controller.closeVisualComparison()
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
                                title = {
                                    Text(when {
                                        comparisonOpen -> "Сравнение"
                                        state.tab == DuplicateTab.EXACT -> "Точные дубликаты"
                                        else -> "Похожие изображения"
                                    })
                                },
                                navigationIcon = {
                                    IconButton(onClick = ::back, enabled = !busy) {
                                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад")
                                    }
                                },
                                actions = {
                                    if (!comparisonOpen) {
                                        val hasResults = when (state.tab) {
                                            DuplicateTab.EXACT -> !state.catalogue?.cards.isNullOrEmpty()
                                            DuplicateTab.VISUAL -> !state.visualGroups.isNullOrEmpty()
                                        }
                                        if (hasResults) {
                                            IconButton(onClick = { grid = !grid }) {
                                                Icon(if (grid) Icons.Outlined.ViewList else Icons.Outlined.GridView,
                                                    if (grid) "Список" else "Сетка")
                                            }
                                        }
                                        IconButton(onClick = { controller.refresh() }, enabled = !busy && state.scanProgress == null) {
                                            Icon(Icons.Outlined.Refresh, "Обновить поиск")
                                        }
                                    }
                                },
                            )
                            if (state.group == null && !comparisonOpen) {
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
                                    DuplicateTab.VISUAL -> VisualCandidates(state, catalogue, controller, grid)
                                }
                            }
                        }
                    }
                }
                if (preview != null) {
                    val comparison = state.visualComparison
                    val catalogue = state.catalogue
                    val items = remember(preview, comparison, catalogue) {
                        val pair = comparison?.let {
                            listOfNotNull(
                                catalogue?.rows?.get(it.first)?.toDuplicateMediaItem(),
                                catalogue?.rows?.get(it.second)?.toDuplicateMediaItem(),
                            )
                        }
                        pair?.takeIf { it.size == 2 && it.any { item -> item.uriKey == preview.uriKey } } ?: listOf(preview)
                    }
                    var visibleName by remember(preview) { mutableStateOf(preview.name) }
                    DisposableEffect(Unit) {
                        ThumbnailCache.setViewerBusy(true)
                        onDispose { ThumbnailCache.setViewerBusy(false) }
                    }
                    Surface(Modifier.fillMaxSize(), color = Color.Black) {
                        ViewerScreen(
                            items = items,
                            initialItem = preview,
                            onBack = controller::closePreview,
                            onVisibleItemChanged = { visibleName = it.name },
                            quickExifEnabled = true,
                            cleanupMode = false,
                            onTrash = {},
                            onShare = {},
                            onCrop = {},
                            onRename = { _, _ -> },
                            onMove = {},
                            readOnly = true,
                            loopPages = comparisonOpen,
                        )
                    }
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
                        color = if (comparisonOpen) duplicateColors.surface.copy(alpha = 0.85f) else duplicateColors.surface,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = controller::closePreview) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, if (comparisonOpen) "К сравнению" else "К результатам")
                            }
                            if (comparisonOpen) {
                                Text(
                                    visibleName.ifBlank { "Без имени" },
                                    modifier = Modifier.padding(end = 12.dp, top = 4.dp, bottom = 4.dp)
                                        .testTag("comparison-viewer-file-name"),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
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
                        runCatching {
                            state.selected.fold(0L) { total, uri ->
                                Math.addExact(total, state.catalogue?.rows?.get(uri)?.sizeBytes ?: 0L)
                            }
                        }.getOrNull()
                    }
                    bytes?.let { Text(formatDuplicateBytes(it), style = MaterialTheme.typography.bodySmall) }
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
                        FilterChip(large, { resetChoice(); large = !large }, { Text("> 5 МиБ") }, enabled = !busy)
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
                        if (!expanded) DuplicateTile(row, Modifier.size(48.dp), interactive = false) {}
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

@Composable
private fun VisualCandidates(
    state: DuplicateSearchUiState,
    catalogue: DuplicateCatalogue,
    controller: DuplicateSearchController,
    grid: Boolean,
) {
    val groups = state.visualGroups
    val listState = rememberLazyGridState()
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var selected by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    fun toggle(uri: String) {
        selected = ArrayList(if (uri in selected) selected - uri else (selected + uri).take(2))
    }
    val comparison = state.visualComparison
    if (comparison != null) {
        VisualComparison(
            first = catalogue.rows.getValue(comparison.first),
            second = catalogue.rows.getValue(comparison.second),
            status = state.visualStatuses[comparison],
            checking = state.visualChecking == comparison,
            verificationAvailable = state.visualChecking == null,
            onOpen = controller::openPreview,
            onCheck = controller::checkVisual,
        )
        return
    }
    val pair = if (selected.size == 2) selected[0] to selected[1] else null
    Column(Modifier.fillMaxSize()) {
        LazyVerticalGrid(columns = GridCells.Fixed(if (grid) 2 else 1),
            state = listState,
            modifier = Modifier.weight(1f).testTag("duplicate-visual-list"),
            contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                state.visualError != null -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Column { SearchMessage(state.visualError); TextButton(onClick = controller::loadVisualGroups) { Text("Повторить") } }
                }
                groups == null -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Column { SearchMessage("Сравниваем изображения во всех папках…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }
                groups.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) { SearchMessage("Похожих изображений не найдено") }
                else -> groups.forEach { group ->
                    val first = catalogue.rows.getValue(group.first())
                    val open = expanded == first.uri
                    item("visual-group/${first.uri}", span = { GridItemSpan(maxLineSpan) }) {
                        Surface(shape = MaterialTheme.shapes.medium, color = duplicateColors.surfaceContainer) {
                            Row(Modifier.fillMaxWidth().clickable {
                                expanded = if (open) null else first.uri
                                selected = arrayListOf()
                            }.padding(8.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (!open) DuplicateTile(first, Modifier.size(48.dp), interactive = false) {}
                                Column(Modifier.weight(1f)) {
                                    Text("Файлов: ${group.size}", style = MaterialTheme.typography.titleMedium)
                                    Text(first.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                }
                                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                            }
                        }
                    }
                    if (open) items(group, key = { "visual-file/$it" }) { uri ->
                        val row = catalogue.rows.getValue(uri)
                        val preview = { controller.openPreview(uri) }
                        val checkbox: @Composable () -> Unit = {
                            Checkbox(uri in selected, { toggle(uri) }, enabled = uri in selected || selected.size < 2,
                                modifier = Modifier.semantics { contentDescription = "Сравнить ${row.name}" })
                        }
                        Surface(shape = MaterialTheme.shapes.small, color = duplicateColors.surfaceContainer) {
                            if (grid) Column {
                                Box {
                                    DuplicateTile(row, onOpen = preview)
                                    Surface(Modifier.align(Alignment.TopEnd), color = duplicateColors.surfaceContainer) { checkbox() }
                                }
                                VisualInfo(row, Modifier.clickable(onClick = preview).padding(8.dp))
                            } else Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DuplicateTile(row, Modifier.size(60.dp), onOpen = preview)
                                VisualInfo(row, Modifier.weight(1f).clickable(onClick = preview))
                                checkbox()
                            }
                        }
                    }
                }
            }
        }
        if (selected.isNotEmpty()) Surface(color = duplicateColors.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selected = arrayListOf() }) { Icon(Icons.Outlined.Close, "Снять выбор") }
                    Text("Выбрано ${selected.size} из 2", Modifier.weight(1f))
                    Button(onClick = { pair?.let(controller::openVisualComparison) }, enabled = pair != null) {
                        Text("Сравнить")
                    }
                }
                if (pair == null) Text("Выберите второй снимок для сравнения", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun VisualComparison(
    first: CatalogMediaRow,
    second: CatalogMediaRow,
    status: String?,
    checking: Boolean,
    verificationAvailable: Boolean,
    onOpen: (String) -> Unit,
    onCheck: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("duplicate-visual-comparison"),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("images") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(first, second).forEach { row ->
                    DuplicateTile(
                        row,
                        Modifier.weight(1f).aspectRatio(1f),
                        contentScale = ContentScale.Fit,
                        highQuality = true,
                        onOpen = { onOpen(row.uri) },
                    )
                }
            }
        }
        item("names") {
            ComparisonRow("Имя файла", first.name.ifBlank { "Без имени" }, second.name.ifBlank { "Без имени" })
        }
        item("sizes") {
            ComparisonRow("Размер файла", visualSize(first) ?: "Неизвестен", visualSize(second) ?: "Неизвестен")
        }
        item("resolution") {
            ComparisonRow("Разрешение", visualResolution(first) ?: "Неизвестно", visualResolution(second) ?: "Неизвестно")
        }
        item("dates") {
            ComparisonRow("Дата", visualDate(first) ?: "Неизвестна", visualDate(second) ?: "Неизвестна")
        }
        item("folders") {
            ComparisonRow("Папка", visualFolder(first).ifBlank { "Неизвестна" }, visualFolder(second).ifBlank { "Неизвестна" })
        }
        item("verification") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = onCheck, enabled = verificationAvailable) {
                    Text("Проверить исходные пиксели")
                }
                if (checking) {
                    Text("Проверяем пиксели…")
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    status?.let { Text(it) }
                }
            }
        }
    }
}

@Composable
private fun ComparisonRow(label: String, first: String, second: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = duplicateColors.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(first, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(second, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun visualFolder(row: CatalogMediaRow): String =
    row.relativePath.ifBlank { row.folderTarget.ifBlank { row.storageName } }

private fun visualResolution(row: CatalogMediaRow): String? =
    if (row.width > 0 && row.height > 0) "${row.width}×${row.height}" else null

private fun visualSize(row: CatalogMediaRow): String? =
    row.sizeBytes.takeIf { it > 0L }?.let(::formatDuplicateBytes)

@Composable
private fun visualDate(row: CatalogMediaRow): String? = remember(row.dateTakenMillis, row.dateModifiedMillis) {
    val taken = row.dateTakenMillis > 0
    val millis = if (taken) row.dateTakenMillis else row.dateModifiedMillis
    if (millis <= 0) null else (if (taken) "Снят " else "Изменён ") +
        java.text.DateFormat.getDateTimeInstance().format(java.util.Date(millis))
}

@Composable
private fun VisualInfo(row: CatalogMediaRow, modifier: Modifier) {
    Column(modifier) {
        Text(row.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        Text(visualFolder(row), style = MaterialTheme.typography.bodySmall)
        Text(
            listOfNotNull(visualResolution(row), visualSize(row), row.mimeType).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
        )
        visualDate(row)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun DuplicateTile(
    row: CatalogMediaRow,
    modifier: Modifier = Modifier.fillMaxWidth().height(112.dp),
    interactive: Boolean = true,
    contentScale: ContentScale = ContentScale.Crop,
    highQuality: Boolean = false,
    onOpen: () -> Unit,
) {
    val media = remember(row) { row.toDuplicateMediaItem() }
    val model = remember(media, highQuality) { media?.let { GalleryThumb(it, highQuality = highQuality) } }
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
                    contentScale = contentScale,
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

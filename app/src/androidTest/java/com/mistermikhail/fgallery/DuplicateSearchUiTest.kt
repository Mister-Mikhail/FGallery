package com.mistermikhail.fgallery

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.mistermikhail.fgallery.data.CatalogMediaRow
import com.mistermikhail.fgallery.data.DriveRecycleBin
import com.mistermikhail.fgallery.data.DuplicateSearchInput
import com.mistermikhail.fgallery.data.ExactDuplicateReview
import com.mistermikhail.fgallery.data.StorageRoot
import com.mistermikhail.fgallery.data.VisualDuplicateSourceVerification
import com.mistermikhail.fgallery.data.comparisonTrashSha256
import com.mistermikhail.fgallery.data.verifyCurrentExactDuplicateSelection
import com.mistermikhail.fgallery.data.formatDuplicateBytes
import com.mistermikhail.fgallery.ui.DuplicateSearchController
import com.mistermikhail.fgallery.ui.DuplicateSearchScreen
import com.mistermikhail.fgallery.ui.toDuplicateMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class DuplicateSearchUiTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun readableTextTwoColumnsAndPinnedActionsAtLargeFontScale() {
        val rows = generatedRows(24)
        showSearch(rows, fontScale = 1.5f)
        waitForTag("duplicate-groups")
        rule.onNodeWithContentDescription("Сетка").performClick()
        openFirstGroup(rows)
        val keeper = rule.onNodeWithTag("duplicate-card-${rows[0].uri}")
        val copy = rule.onNodeWithTag("duplicate-card-${rows[1].uri}")
        val keeperBounds = keeper.getUnclippedBoundsInRoot()
        val copyBounds = copy.getUnclippedBoundsInRoot()
        assertEquals(keeperBounds.top, copyBounds.top)
        assertTrue("Exactly two separate columns", keeperBounds.right < copyBounds.left)
        val keeperWidth = keeperBounds.right - keeperBounds.left
        val copyWidth = copyBounds.right - copyBounds.left
        assertTrue(kotlin.math.abs(keeperWidth.value - copyWidth.value) <= 1.dp.value)
        rule.onNodeWithTag("duplicate-select-${rows[0].uri}").assertDoesNotExist()
        val title = rule.onNodeWithText(rows[1].name).assertIsDisplayed()
        val pixels = title.captureToImage().toPixelMap()
        assertTrue("Card text must contribute light pixels over the dark card",
            (0 until pixels.width).any { x ->
                (0 until pixels.height).any { y ->
                    val pixel = pixels[x, y]
                    pixel.red > .7f && pixel.green > .7f && pixel.blue > .7f
                }
            })
        rule.onAllNodes(hasText("96 байт"))[0].assertIsDisplayed()
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasTestTag("duplicate-preview-fallback-${rows[1].uri}"), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("duplicate-preview-fallback-${rows[1].uri}", useUnmergedTree = true)
            .assertIsDisplayed()
        val checkbox = rule.onNodeWithTag("duplicate-select-${rows[1].uri}")
        checkbox.assertIsOff().performClick()
        rule.onNodeWithText("Выбрано 1").assertIsDisplayed()
        val panel = rule.onNodeWithTag("duplicate-action-bar")
        val panelBounds = panel.getUnclippedBoundsInRoot()
        val grid = rule.onNodeWithTag("duplicate-copy-grid")
        grid.performScrollToNode(hasTestTag("duplicate-select-${rows[5].uri}"))
        rule.onNodeWithTag("duplicate-select-${rows[5].uri}").performClick()
        rule.onNodeWithText("Выбрано 2").assertIsDisplayed()
        grid.performScrollToIndex(20)
        assertEquals(panelBounds, panel.getUnclippedBoundsInRoot())
        rule.onNodeWithText("В корзину…").assertIsDisplayed()
        rule.onNodeWithContentDescription("Снять выбор").assertIsDisplayed()
        screenshot("exact-selection-large-font")
        grid.performScrollToNode(hasTestTag("duplicate-select-${rows[1].uri}"))
        checkbox.assertIsOn()
        rule.onNodeWithContentDescription("Снять выбор").performClick()
        panel.assertDoesNotExist()
    }

    @Test fun smartSelectionProtectsKeeperAndClearsWhenFilterChanges() {
        val rows = generatedRows(3)
        showSearch(rows)
        waitForTag("duplicate-groups")
        rule.onNodeWithText("Выбрать лишние копии").performClick()
        rule.onNodeWithText("Выбрано 2").assertIsDisplayed()
        rule.onNodeWithText("Видео").performClick()
        rule.onNodeWithTag("duplicate-action-bar").assertDoesNotExist()
        rule.onNodeWithText("По этим фильтрам совпадений нет").assertIsDisplayed()
        rule.onNodeWithText("Все").performClick()
        openFirstGroup(rows)
        rule.onNodeWithTag("duplicate-select-${rows[0].uri}").assertDoesNotExist()
        rule.onNodeWithTag("duplicate-select-${rows[1].uri}").assertIsOff()
    }

    @Test fun svgUsesTheSharedViewerAndRendersItsContent() {
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "duplicate-svg-").toFile()
        try {
            val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64"><rect width="64" height="64" fill="orange"/></svg>"""
            val keeper = File(directory, "keeper.svg").apply { writeText(svg) }
            val copy = File(directory, "copy.svg").apply { writeText(svg) }
            val exact = listOf(fileRow(keeper, 1), fileRow(copy, 2))
            showSearch(exact)
            openFirstGroup(exact)
            rule.onNodeWithTag("duplicate-preview-${exact[0].uri}").performClick()
            waitForTag("svg-page")
            val viewer = rule.onNodeWithTag("viewer-root").assertIsDisplayed()
            screenshot("shared-svg-viewer")
            val pixels = viewer.captureToImage().toPixelMap()
            assertTrue("The viewer must display visible orange pixels from the SVG fixture",
                (0 until pixels.width step 4).any { x ->
                    (0 until pixels.height step 4).any { y ->
                        val color = pixels[x, y]
                        color.red > .9f && color.green > .4f && color.blue < .1f
                    }
                })
            rule.onNodeWithContentDescription("К результатам").performClick()
            rule.onNodeWithTag("duplicate-copy-grid").assertIsDisplayed()
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun visualComparisonShowsBothFilesAndVerifiesPixelsOnRequest() {
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "duplicate-visual-").toFile()
        try {
            val jpeg = jpegFixture()
            val visual = (0..1).map { index ->
                val bytes = if (index == 0) jpeg else jpeg.copyOf(jpeg.size + 768)
                val file = File(directory, "visual-$index.jpg").apply { writeBytes(bytes) }
                fileRow(file, index + 1L).copy(
                    fullHash = null,
                    perceptualHash = "0123456789abcdef",
                    dateTakenMillis = if (index == 0) 1_720_000_000_000L else 0L,
                )
            }
            showSearch(visual)
            rule.onNodeWithText("Похожие изображения", substring = false).performClick()
            rule.waitUntil(10_000) { rule.onAllNodes(hasText("Файлов: 2")).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Файлов: 2").performClick()
            visual.forEach { rule.onNodeWithContentDescription("Сравнить ${it.name}").performClick() }
            rule.onNodeWithText("Выбрано 2 из 2").assertIsDisplayed()
            rule.onNodeWithText("Сравнить").performClick()
            val comparison = rule.onNodeWithTag("duplicate-visual-comparison").assertIsDisplayed()
            val first = rule.onNodeWithTag("duplicate-preview-${visual[0].uri}").getUnclippedBoundsInRoot()
            val second = rule.onNodeWithTag("duplicate-preview-${visual[1].uri}").getUnclippedBoundsInRoot()
            assertTrue(first.right < second.left)
            visual.forEach {
                comparison.performScrollToNode(hasText(it.name))
                rule.onNodeWithText(it.name).assertIsDisplayed()
                val size = formatDuplicateBytes(it.sizeBytes)
                comparison.performScrollToNode(hasText(size))
                rule.onNodeWithText(size).assertIsDisplayed()
            }
            comparison.performScrollToIndex(0)
            rule.onAllNodes(hasText("128×128"))[0].assertIsDisplayed()
            rule.onAllNodes(hasText("128×128"))[1].assertIsDisplayed()
            rule.onNodeWithText("Снят ", substring = true).assertIsDisplayed()
            rule.onNodeWithText("Изменён ", substring = true).assertIsDisplayed()
            comparison.performScrollToIndex(0)
            rule.onNodeWithContentDescription("Открыть ${visual[0].name}").performClick()
            waitForTag("viewer-root")
            val viewer = rule.onNodeWithTag("viewer-root")
            fun expectName(index: Int) {
                val matcher = hasTestTag("comparison-viewer-file-name") and hasText(visual[index].name)
                rule.waitUntil(10_000) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
                rule.onNode(matcher).assertIsDisplayed()
            }
            expectName(0)
            viewer.performTouchInput { swipeLeft() }
            expectName(1)
            screenshot("visual-comparison-viewer")
            viewer.performTouchInput { swipeLeft() }
            expectName(0)
            viewer.performTouchInput { swipeRight() }
            expectName(1)
            viewer.performTouchInput { swipeRight() }
            expectName(0)
            rule.onNodeWithContentDescription("К сравнению").performClick()
            comparison.assertIsDisplayed()

            comparison.performScrollToNode(hasText("Проверить исходные пиксели"))
            rule.onNodeWithText("Подтверждено · пиксели совпадают").assertDoesNotExist()
            rule.onNodeWithText("Проверить исходные пиксели").performClick()
            rule.waitUntil(10_000) {
                rule.onAllNodes(
                    hasText("Подтверждено · пиксели совпадают") or hasText("Не подтверждено", substring = true)
                ).fetchSemanticsNodes().isNotEmpty()
            }
            comparison.performScrollToNode(hasText("Подтверждено · пиксели совпадают"))
            rule.onNodeWithText("Подтверждено · пиксели совпадают").assertIsDisplayed()
            rule.onNodeWithText("В корзину…").assertDoesNotExist()
            comparison.performScrollToIndex(0)
            screenshot("visual-candidates-read-only")
            rule.onNodeWithContentDescription("Назад").performClick()
            rule.onNodeWithText("Выбрано 2 из 2").assertIsDisplayed()
            rule.onNodeWithText("Сравнить").assertIsDisplayed()
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun comparisonShowsAndChecksFourPhotosAndBackClearsSelectionFirst() {
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "comparison-selection-").toFile()
        try {
            val jpeg = jpegFixture()
            val rows = (1..4).map { index ->
                val bytes = if (index == 4) jpegFixture(blueOffset = 120) else jpeg
                val photo = File(directory, "compare-$index.jpg").apply { writeBytes(bytes.copyOf(bytes.size + index * 64)) }
                fileRow(photo, index.toLong()).copy(fullHash = null, perceptualHash = "0123456789abcdef")
            }
            var exits = 0
            showSearch(rows, onClose = { exits++ })
            rule.onNodeWithText("Похожие изображения", substring = false).performClick()
            val header = hasText("Файлов: 4")
            rule.waitUntil(10_000) { rule.onAllNodes(header).fetchSemanticsNodes().isNotEmpty() }
            rule.onNode(header).performClick()
            val groups = rule.onNodeWithTag("duplicate-visual-list")
            rows.forEach {
                val check = "Сравнить ${it.name}"
                groups.performScrollToNode(hasContentDescription(check))
                rule.onNodeWithContentDescription(check).performClick().assertIsOn()
            }
            rule.onNodeWithText("Выбрано 4 из 4").assertIsDisplayed()
            rule.onNodeWithText("Сравнить").performClick()
            rule.onNodeWithText("Выбрано 4 · Листайте вбок").assertIsDisplayed()
            val comparison = rule.onNodeWithTag("duplicate-visual-comparison")
            val photos = rule.onNodeWithTag("comparison-photos")
            fun showPhoto(index: Int) {
                comparison.performScrollToIndex(0)
                photos.performScrollToIndex(index)
            }
            rows.forEachIndexed { index, row ->
                showPhoto(index)
                rule.onNodeWithContentDescription("Открыть ${row.name}").assertIsDisplayed()
                rule.onNode(hasText(row.name) and hasAnyAncestor(hasTestTag("comparison-photo-${row.uri}")))
                    .assertIsDisplayed()
            }
            comparison.performScrollToNode(hasText("Проверить исходные пиксели"))
            rule.onNodeWithText("Проверка по снимку «${rows.first().name}»").assertIsDisplayed()
            rule.onNodeWithText("Проверить исходные пиксели").performClick()
            val checkButton = hasText("Проверить исходные пиксели")
            rule.waitUntil(10_000) { rule.onAllNodes(checkButton).fetchSemanticsNodes().isNotEmpty() }
            rows.drop(1).forEachIndexed { index, row ->
                showPhoto(index + 1)
                val expected = if (row == rows.last()) "Не подтверждено" else "Подтверждено · пиксели совпадают"
                rule.onNode(hasText(expected) and hasAnyAncestor(hasTestTag("comparison-photo-${row.uri}")))
                    .assertIsDisplayed()
            }
            rule.onNodeWithContentDescription("Открыть ${rows[3].name}").performTouchInput { longClick() }
            rule.onNodeWithText("Взять за образец").performClick()
            comparison.performScrollToNode(hasText("Проверка по снимку «${rows.last().name}»"))
            showPhoto(3)
            rule.onNodeWithContentDescription("Открыть ${rows[3].name}").performClick()
            waitForTag("viewer-root")
            val viewer = rule.onNodeWithTag("viewer-root")
            (listOf(rows.last()) + rows).forEachIndexed { index, row ->
                if (index > 0) viewer.performTouchInput { swipeLeft() }
                val name = hasTestTag("comparison-viewer-file-name") and hasText(row.name)
                rule.waitUntil(10_000) { rule.onAllNodes(name).fetchSemanticsNodes().isNotEmpty() }
                rule.onNode(name).assertIsDisplayed()
            }
            pressBack()
            rule.onNodeWithTag("duplicate-visual-comparison").assertIsDisplayed()
            pressBack()
            rule.onNodeWithText("Выбрано 4 из 4").assertIsDisplayed()

            rule.onNodeWithContentDescription("Назад").performClick()
            rule.onNodeWithText("Выбрано 4 из 4").assertDoesNotExist()
            val lastCheck = "Сравнить ${rows.last().name}"
            groups.performScrollToNode(hasContentDescription(lastCheck))
            rule.onNodeWithContentDescription(lastCheck).assertIsOff().performClick()
            pressBack()
            rule.onNodeWithContentDescription(lastCheck).assertIsOff()
            rule.runOnIdle { assertEquals(0, exits) }
            pressBack()
            rule.runOnIdle { assertEquals(1, exits) }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun comparisonCanTrashFromViewerAndMenuWithoutReloadingResults() {
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "comparison-bin-").toFile()
        val firstBytes = jpegFixture()
        val secondBytes = firstBytes.copyOf(firstBytes.size + 128)
        val first = File(directory, "keep-visible.jpg").apply { writeBytes(firstBytes) }
        val second = File(directory, "move-visible.jpg").apply { writeBytes(secondBytes) }
        val third = File(directory, "move-from-menu.jpg").apply { writeBytes(firstBytes.copyOf(firstBytes.size + 256)) }
        val thirdBytes = third.readBytes()
        val rows = listOf(fileRow(first, 1), fileRow(second, 2), fileRow(third, 3)).map {
            it.copy(fullHash = null, perceptualHash = "0123456789abcdef")
        }
        val root = StorageRoot("duplicate-fixture", "Fixture", directory.path, directory)
        val bin = DriveRecycleBin(context, { listOf(root) }, { emptyList() })
        fun currentRows() = rows.filter { File(it.sourcePath).isFile }
        fun binFiles() = runBlocking { bin.load().filter { it.folderTarget.startsWith(directory.path + "/") } }
        fun checkSource(row: CatalogMediaRow) =
            comparisonTrashSha256(context, row, checkNotNull(row.toDuplicateMediaItem()), currentRows())
        var loads = 0
        try {
            showSearch(rows, load = { loads++; currentRows() }, trashComparedImage = { row ->
                val sha256 = withContext(Dispatchers.IO) { checkSource(row) }
                bin.trashWithPreMoveCheck(listOf(checkNotNull(row.toDuplicateMediaItem()))) { item ->
                    item.uriKey == row.uri && checkSource(row) == sha256
                }
            })
            rule.onNodeWithText("Похожие изображения", substring = false).performClick()
            rule.waitUntil(10_000) { rule.onAllNodes(hasText("Файлов: 3")).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Файлов: 3").performClick()
            rows.take(2).forEach { rule.onNodeWithContentDescription("Сравнить ${it.name}").performClick() }
            rule.onNodeWithText("Сравнить").performClick()
            rule.onNodeWithContentDescription("Открыть ${first.name}").performClick()
            waitForTag("viewer-root")
            val viewer = rule.onNodeWithTag("viewer-root")
            val activate = rule.onNodeWithContentDescription("Включить режим уборки")
            val deactivate = rule.onNodeWithContentDescription("Выключить режим уборки")
            val confirm = hasText("Переместить снимок в корзину?")
            activate.assertIsDisplayed()
            viewer.performTouchInput { doubleClick() }
            assertArrayEquals(firstBytes, first.readBytes())
            assertArrayEquals(secondBytes, second.readBytes())
            assertTrue(binFiles().isEmpty())
            activate.assertIsDisplayed().performClick()
            deactivate.assertIsDisplayed().performClick()
            activate.assertIsDisplayed().performClick()
            rule.onNodeWithContentDescription("К сравнению").performClick()
            rule.onNodeWithContentDescription("Открыть ${first.name}").performClick()
            activate.assertIsDisplayed().performClick()
            rule.onNodeWithText("Двойной тап — в корзину").assertIsDisplayed()
            viewer.performTouchInput { swipeLeft() }
            val visible = hasTestTag("comparison-viewer-file-name") and hasText(second.name)
            rule.waitUntil(10_000) { rule.onAllNodes(visible).fetchSemanticsNodes().isNotEmpty() }
            rule.onNode(visible).assertIsDisplayed()
            assertArrayEquals(firstBytes, first.readBytes())
            assertArrayEquals(secondBytes, second.readBytes())
            assertTrue(binFiles().isEmpty())
            viewer.performTouchInput { doubleClick() }
            val movedNotice = hasText("${second.name} перемещён в корзину.")
            rule.waitUntil(10_000) { rule.onAllNodes(movedNotice).fetchSemanticsNodes().isNotEmpty() }
            rule.onNode(confirm).assertDoesNotExist()
            assertArrayEquals(firstBytes, first.readBytes())
            assertFalse(second.exists())
            assertEquals(second.name, binFiles().single().name)
            rule.onNodeWithText("Файлов: 2").assertIsDisplayed()
            rule.onNodeWithText("Выбрано 1 из 2").assertIsDisplayed()
            rule.onNodeWithContentDescription("Сравнить ${second.name}").assertDoesNotExist()
            rule.onNodeWithContentDescription("Сравнить ${third.name}").performClick()
            rule.onNodeWithText("Сравнить").performClick()
            rule.onNodeWithContentDescription("Открыть ${third.name}").performTouchInput { longClick() }
            rule.onNodeWithText("В корзину", substring = false).assertIsDisplayed()
            assertArrayEquals(thirdBytes, third.readBytes())
            rule.onNodeWithText("В корзину", substring = false).performClick()
            val menuNotice = hasText("${third.name} перемещён в корзину.")
            rule.waitUntil(10_000) { rule.onAllNodes(menuNotice).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Похожих изображений не найдено").assertIsDisplayed()
            rule.runOnIdle { assertEquals(1, loads) }
            assertArrayEquals(firstBytes, first.readBytes())
            assertFalse(third.exists())
            val moved = binFiles()
            assertEquals(setOf(second.name, third.name), moved.map { it.name }.toSet())
            runBlocking { bin.restore(moved) }
            assertArrayEquals(secondBytes, second.readBytes())
            assertArrayEquals(thirdBytes, third.readBytes())
            assertTrue(binFiles().isEmpty())
        } finally {
            val remaining = binFiles()
            runBlocking { bin.restore(remaining) }
            directory.deleteRecursively()
        }
    }

    @Test fun cancelLeavesFixturesIntactAndConfirmedCopyCanBeRestored() {
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "duplicate-bin-").toFile()
        val payload = ByteArray(1500) { it.toByte() }
        val keeper = File(directory, "keeper.jpg").apply { writeBytes(payload) }
        val copy = File(directory, "copy.jpg").apply { writeBytes(payload) }
        val other = File(directory, "other.jpg").apply { writeBytes(payload) }
        val rows = listOf(fileRow(keeper, 1), fileRow(copy, 2), fileRow(other, 3))
        val root = StorageRoot("duplicate-fixture", "Fixture", directory.path, directory)
        val bin = DriveRecycleBin(context, { listOf(root) }, { emptyList() })
        fun binFiles() = runBlocking { bin.load().filter { it.folderTarget.startsWith(directory.path + "/") } }
        var loads = 0
        try {
            showSearch(rows, load = { loads++; rows }) { review ->
                val verified = verifyCurrentExactDuplicateSelection(context, review, rows, explicitlyConfirmed = true)
                check(verified == setOf(rows[1].uri))
                bin.trashWithPreMoveCheck(listOf(rows[1].toDuplicateMediaItem()!!)) { item ->
                    item.uriKey in verifyCurrentExactDuplicateSelection(context, review, rows, explicitlyConfirmed = true)
                }
                verified
            }
            openFirstGroup(rows)
            rule.onNodeWithTag("duplicate-select-${rows[1].uri}").performClick()
            rule.onNodeWithText("В корзину…").performClick()
            rule.waitUntil(10_000) { rule.onAllNodes(hasText("Отмена")).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("1500 байт", substring = true).assertIsDisplayed()
            rule.onNodeWithText("Отмена").performClick()
            assertTrue(binFiles().isEmpty())
            assertArrayEquals(payload, keeper.readBytes())
            assertArrayEquals(payload, copy.readBytes())
            rule.onNodeWithText("В корзину…").performClick()
            rule.waitUntil(10_000) {
                rule.onAllNodes(hasText("Проверить и переместить")).fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText("Проверить и переместить").performClick()
            val movedNotice = hasText("В корзину перемещено: 1 из 1.")
            rule.waitUntil(10_000) { rule.onAllNodes(movedNotice).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("duplicate-copy-grid").performScrollToIndex(1)
            rule.onNodeWithText("Файлов: 2").assertIsDisplayed()
            rule.onNodeWithTag("duplicate-copy-grid").assertIsDisplayed()
            rule.onNodeWithTag("duplicate-select-${rows[1].uri}").assertDoesNotExist()
            rule.onNodeWithTag("duplicate-select-${rows[2].uri}").assertIsOff()
            rule.runOnIdle { assertEquals(1, loads) }
            assertArrayEquals(payload, keeper.readBytes())
            assertArrayEquals(payload, other.readBytes())
            assertFalse(copy.exists())
            val item = binFiles().single()
            runBlocking { bin.restore(listOf(item)) }
            assertArrayEquals(payload, copy.readBytes())
            assertTrue(binFiles().isEmpty())
        } finally {
            val remaining = binFiles()
            runBlocking { bin.restore(remaining) }
            directory.deleteRecursively()
        }
    }

    @Test fun hundredFileNavigationTiming() = recordNavigationTiming(100)

    @Test fun twelveHundredFileNavigationTiming() = recordNavigationTiming(1200)

    private fun recordNavigationTiming(count: Int) {
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "duplicate-benchmark-").toFile()
        try {
            val jpeg = jpegFixture()
            val digest = fullDigest(jpeg)
            val rows = (0 until count).map { index ->
                val file = File(directory, "Sample-${index.toString().padStart(4, '0')}.jpg")
                file.writeBytes(jpeg)
                row(file, index + 1L, jpeg.size.toLong(), digest)
            }
            val start = SystemClock.elapsedRealtime()
            showSearch(rows)
            openFirstGroup(rows)
            val opened = SystemClock.elapsedRealtime()
            val grid = rule.onNodeWithTag("duplicate-copy-grid")
            grid.performScrollToIndex(count - 2)
            grid.performScrollToNode(hasTestTag("duplicate-select-${rows[1].uri}"))
            val scrolled = SystemClock.elapsedRealtime()
            val checkbox = rule.onNodeWithTag("duplicate-select-${rows[1].uri}")
            repeat(20) { checkbox.performClick() }
            checkbox.assertIsOff()
            val toggled = SystemClock.elapsedRealtime()
            rule.onNodeWithContentDescription("Назад").performClick()
            openFirstGroup(rows)
            checkbox.assertIsOff()
            val reopened = SystemClock.elapsedRealtime()
            val metrics = listOf(opened - start, scrolled - opened, toggled - scrolled, reopened - toggled)
            val csv = "files,open_ms,scroll_to_end_and_back_ms,toggle_20_ms,return_to_group_ms\n" +
                (listOf(count.toLong()) + metrics).joinToString(",") + "\n"
            logArtifact("performance-$count.csv", csv.toByteArray())
            assertTrue("UI interactions exceeded the fixture timeout: $metrics", metrics.drop(1).all { it < 15_000 })
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun showSearch(
        rows: List<CatalogMediaRow>,
        fontScale: Float = 1f,
        onClose: () -> Unit = {},
        load: suspend () -> List<CatalogMediaRow> = { rows },
        trashComparedImage: suspend (CatalogMediaRow) -> Unit = { error("Unexpected mutation") },
        trash: suspend (ExactDuplicateReview) -> Set<String> = { error("Unexpected mutation") },
    ) {
        rule.setContent {
            val scope = rememberCoroutineScope()
            val controller = remember {
                DuplicateSearchController(
                    scope,
                    load = { _, _ -> DuplicateSearchInput(load()) },
                    verifyVisual = { first, second -> VisualDuplicateSourceVerification.verify(context, first, second) },
                    trash = trash,
                    trashComparedImage = trashComparedImage,
                )
            }
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density, fontScale),
                LocalContentColor provides Color.Black,
            ) {
                LaunchedEffect(controller) { controller.refresh(force = false) }
                DuplicateSearchScreen(controller, onClose = onClose, onRequestAccess = {})
            }
        }
    }

    private fun openFirstGroup(rows: List<CatalogMediaRow>) {
        waitForTag("duplicate-groups")
        rule.onNodeWithTag("group-${rows[0].fullHash}").performClick()
        rule.onNodeWithTag("duplicate-copy-grid").performScrollToIndex(2)
    }

    private fun waitForTag(tag: String) {
        rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun screenshot(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val bytes = ByteArrayOutputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            it.toByteArray()
        }
        logArtifact("$name.png", bytes)
    }

    private fun logArtifact(name: String, bytes: ByteArray) {
        require(name.matches(Regex("[a-z0-9-]+[.](png|csv)")))
        // The Gradle runner may uninstall the app before collecting artifacts.
        // A shell-owned scratch directory remains available to adb pull.
        val directory = "/data/local/tmp/fgallery-duplicate-qa"
        val shell = InstrumentationRegistry.getInstrumentation().uiAutomation
        ParcelFileDescriptor.AutoCloseInputStream(shell.executeShellCommand("mkdir -p $directory"))
            .use { it.readBytes() }
        val path = "$directory/$name"
        val pipes = shell.executeShellCommandRw("dd of=$path")
        ParcelFileDescriptor.AutoCloseInputStream(pipes[0]).use { result ->
            ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]).use { it.write(bytes) }
            result.readBytes()
        }
        val size = ParcelFileDescriptor.AutoCloseInputStream(shell.executeShellCommand("wc -c $path"))
            .use { it.bufferedReader().readText().trim().substringBefore(' ').toLongOrNull() }
        check(size == bytes.size.toLong()) { "Could not write $name" }
    }

    private fun generatedRows(count: Int) = (0 until count).map { index ->
        val name = "Sample-" + index.toString().padStart(4, '0') + ".jpg"
        val file = File(context.cacheDir, "missing-fixtures/$name")
        row(file, index + 1L, 96, "a".repeat(64))
    }

    private fun jpegFixture(blueOffset: Int = 0): ByteArray {
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.setPixels(
                IntArray(128 * 128) { i -> AndroidColor.rgb(i % 128, i / 128, (i * 7 + blueOffset) % 256) },
                0, 128, 0, 0, 128, 128,
            )
            ByteArrayOutputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it))
                it.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun fullDigest(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    private fun fileRow(file: File, order: Long): CatalogMediaRow =
        row(file, order, file.length(), fullDigest(file.readBytes()))

    private fun row(file: File, order: Long, size: Long, hash: String) = CatalogMediaRow(
        uri = Uri.fromFile(file).toString(), mediaId = order, name = file.name,
        mimeType = if (file.extension == "svg") "image/svg+xml" else "image/jpeg",
        kind = if (file.extension == "svg") "SVG" else "IMAGE",
        dateTakenMillis = order, width = 128, height = 128, durationMillis = 0,
        album = "Fixtures", relativePath = "Fixtures", sizeBytes = size, dateModifiedMillis = file.lastModified(),
        storageId = "duplicate-fixture", storageName = "Тестовый носитель",
        folderTarget = file.parent.orEmpty(), sourcePath = file.path, fullHash = hash,
    )
}

package com.mistermikhail.fgallery

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mistermikhail.fgallery.data.MediaItem
import com.mistermikhail.fgallery.data.MediaKind
import com.mistermikhail.fgallery.ui.AlbumSummary
import com.mistermikhail.fgallery.ui.MoveDestinationScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MoveDestinationInteractionTest {
    @get:Rule val rule = createComposeRule()

    private val album = AlbumSummary("Путешествие",
        MediaItem(1L, Uri.EMPTY, "cover.jpg", "image/jpeg", MediaKind.IMAGE, 0L,
            10, 10, 0L, "Путешествие", "Pictures/Путешествие/", 100L,
            folderTarget = "/storage/emulated/0/Pictures/Путешествие"), 4, 0L, 400L)

    @Test fun ordinaryAlbumTapMovesWithoutEnteringAlbumDetails() {
        var moveCount = 0
        var openCount = 0
        rule.setContent {
            MoveDestinationScreen(albums = listOf(album), movingCount = 1, query = "",
                selectedAlbum = null, onQueryChanged = {}, onSelectAlbum = { moveCount++ },
                onOpenAlbum = { openCount++ }, onBackFromAlbum = {}, onCancel = {},
                onMoveHere = {}, onChooseOtherFolder = {}, onCreateFolder = {})
        }
        rule.onNodeWithText("Путешествие").performClick()
        rule.runOnIdle { assertEquals(1, moveCount); assertEquals(0, openCount) }
    }

    @Test fun chevronEntersFolderWithoutMovingFiles() {
        var moveCount = 0
        var openCount = 0
        rule.setContent {
            MoveDestinationScreen(albums = listOf(album), movingCount = 1, query = "",
                selectedAlbum = null, onQueryChanged = {}, onSelectAlbum = { moveCount++ },
                onOpenAlbum = { openCount++ }, onBackFromAlbum = {}, onCancel = {},
                onMoveHere = {}, onChooseOtherFolder = {}, onCreateFolder = {})
        }
        rule.onNodeWithContentDescription("Открыть папку Путешествие").performClick()
        rule.runOnIdle { assertEquals(0, moveCount); assertEquals(1, openCount) }
    }
}

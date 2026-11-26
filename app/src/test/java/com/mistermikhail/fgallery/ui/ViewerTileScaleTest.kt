package com.mistermikhail.fgallery.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewerTileScaleTest {
    @Test fun landscapePhotoFillsSquareTileWithoutStretch() {
        // Full-screen fit is 400×200. In a 100×100 tile the sides are cropped.
        assertEquals(0.5f, viewerTileScale(400f, 800f, 100f, 100f, 2f), 0.0001f)
    }

    @Test fun portraitPhotoFillsWideMosaicTile() {
        assertEquals(0.5f, viewerTileScale(400f, 800f, 200f, 100f, 0.5f), 0.0001f)
    }
    @Test fun comparisonThumbnailFitsWholePhotoInsideSquare() {
        assertEquals(0.25f, viewerTileScale(400f, 800f, 100f, 100f, 2f, fit = true), 0.0001f)
    }
    @Test fun closingHandsOffToTheRealGridBeforeTheViewerIsRemoved() {
        assertEquals(1f, closingViewerOpacity(.3f), 0f)
        assertEquals(.5f, closingViewerOpacity(.1f), .0001f)
        assertEquals(0f, closingViewerOpacity(0f), 0f)
    }
}

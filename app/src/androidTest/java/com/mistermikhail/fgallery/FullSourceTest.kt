package com.mistermikhail.fgallery

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.mistermikhail.fgallery.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FullSourceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun fixture(name: String) = File(context.cacheDir, name).apply {
        instrumentation.context.assets.open(name).use { input -> outputStream().use { input.copyTo(it) } }
    }
    @Test fun tiffFullSourceRetainsEveryPixelBeyondThumbnailResolution() = runBlocking {
        val file = fixture("detail.tif")
        val item = MediaItem(97701, Uri.fromFile(file), file.name, "image/tiff", MediaKind.IMAGE, 0, 6144, 32, 0, "Tests", "", file.length())
        val full = FullResolutionFiles.prepare(context, item)
        val bitmap = BitmapFactory.decodeFile(full.path)
        assertNotNull(bitmap); assertEquals(6144, bitmap.width); assertEquals(32, bitmap.height)
        for (x in 4000..4050) assertEquals("Native pixel $x", if (x % 2 == 0) android.graphics.Color.BLACK else android.graphics.Color.WHITE, bitmap.getPixel(x, 16))
        bitmap.recycle()
    }
    @Test fun libRawDecodesSensorDataInsteadOfEmbeddedThumbnail() = runBlocking {
        // This DNG intentionally contains no thumbnail or JPEG preview.
        val file = fixture("synthetic.dng")
        val item = MediaItem(97702, Uri.fromFile(file), file.name, "image/x-adobe-dng", MediaKind.RAW, 0, 128, 96, 0, "Tests", "", file.length())
        val full = FullResolutionFiles.prepare(context, item)
        val bitmap = BitmapFactory.decodeFile(full.path)
        assertNotNull(bitmap); assertTrue(bitmap.width >= 120); assertTrue(bitmap.height >= 88)
        assertTrue(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) != android.graphics.Color.BLACK)
        bitmap.recycle()
    }
    @Test fun compatiblePdfRendersOwnerRestrictedDocumentWithoutUserPassword() {
        val file = fixture("owner-only.pdf")
        PdfSession(context, Uri.fromFile(file), compatibleOnly = true).use { document ->
            assertEquals(1, document.pageCount)
            val bitmap = document.render(0, 640)
            assertEquals(640, bitmap.width)
            val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
            assertTrue(android.graphics.Color.red(pixel) > 220 && android.graphics.Color.green(pixel) < 30)
            bitmap.recycle()
        }
    }
}

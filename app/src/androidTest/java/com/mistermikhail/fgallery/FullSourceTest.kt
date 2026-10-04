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
    @Test fun pdfViewportRerenderRetainsDetailAtFullZoom() {
        val file = File(context.cacheDir, "pdf-sharp-lines.pdf")
        val source = android.graphics.pdf.PdfDocument()
        try {
            val page = source.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(320, 240, 1).create())
            page.canvas.drawColor(android.graphics.Color.WHITE)
            val paint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK }
            for (i in 0 until 500) {
                val x = 145f + i * .0625f
                page.canvas.drawRect(x, 100f, x + .04f, 140f, paint)
            }
            source.finishPage(page)
            file.outputStream().use { source.writeTo(it) }
        } finally { source.close() }
        PdfSession(context, Uri.fromFile(file)).use { document ->
            val region = document.renderRegion(0, 1024, 768, 8f, 0f, 0f)
            val base = document.render(0, 1800)
            val magnified = android.graphics.Bitmap.createBitmap(1024, 768, android.graphics.Bitmap.Config.ARGB_8888)
            val matrix = android.graphics.Matrix().apply {
                setScale(8192f / base.width, 6144f / base.height)
                postTranslate(-3584f, -2688f)
            }
            android.graphics.Canvas(magnified).drawBitmap(base, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            fun black(bitmap: android.graphics.Bitmap) = (200 until 800).count { x -> android.graphics.Color.red(bitmap.getPixel(x, 384)) < 60 }
            assertTrue("Native viewport must resolve fine PDF lines", black(region) > black(magnified) + 100)
            region.recycle(); base.recycle(); magnified.recycle()
        }
        file.delete()
    }

}

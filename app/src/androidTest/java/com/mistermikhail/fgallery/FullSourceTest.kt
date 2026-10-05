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
    @Test fun streamingRawPngMatchesThePreviousColorCurvePixelForPixel() {
        val file = fixture("synthetic.dng")
        val output = File(context.cacheDir, "raw-stream-equivalence.png")
        com.homesoft.photo.libraw.LibRaw().use { raw ->
            raw.setHalfSize(false)
            raw.setCameraWhiteBalance(true)
            raw.setOutputColorSpace(com.homesoft.photo.libraw.LibRaw.COLORSPACE_SRGB)
            assertEquals(0, raw.open(file.path))
            raw.setQuality(3)
            assertEquals(0, raw.dcrawProcess())
            val original = raw.getMutableBitmap(android.graphics.Bitmap.Config.ARGB_8888)!!
            val size = TiffNative.writeRawPng(raw, output.path)!!
            val streamed = BitmapFactory.decodeFile(output.path)
            try {
                assertEquals(original.width, size[0]); assertEquals(original.height, size[1])
                assertEquals(original.width, streamed.width); assertEquals(original.height, streamed.height)
                for (y in 0 until original.height) for (x in 0 until original.width)
                    assertEquals("RAW color at $x,$y", original.getPixel(x, y), streamed.getPixel(x, y))
            } finally { original.recycle(); streamed.recycle(); output.delete() }
        }
    }
    @Test fun fullP65SizeRgb16SingleStripTiffRetainsNativePixels() {
        val width = 8984; val height = 6732
        val file = File(context.cacheDir, "p65-rgb16-single-strip.tif")
        val output = File(context.cacheDir, "p65-full.png")
        val tags = sortedMapOf(
            256 to Triple(4, 1, width), 257 to Triple(4, 1, height),
            258 to Triple(3, 3, 158), 259 to Triple(3, 1, 1), 262 to Triple(3, 1, 2),
            273 to Triple(4, 1, 164), 274 to Triple(3, 1, 1), 277 to Triple(3, 1, 3),
            278 to Triple(4, 1, height), 279 to Triple(4, 1, width * height * 6),
            284 to Triple(3, 1, 1), 339 to Triple(3, 1, 1),
        )
        val header = java.nio.ByteBuffer.allocate(164).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        header.put(73.toByte()).put(73.toByte()).putShort(42).putInt(8).putShort(tags.size.toShort())
        for ((tag, value) in tags) header.putShort(tag.toShort()).putShort(value.first.toShort()).putInt(value.second).putInt(value.third)
        header.putInt(0).putShort(16).putShort(16).putShort(16)
        val row = java.nio.ByteBuffer.allocate(width * 6).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (x in 0 until width) row.putShort(if (x % 2 == 0) (-1).toShort() else 0.toShort()).putShort(0).putShort(0)
        try {
            file.outputStream().buffered(128 * 1024).use { stream ->
                stream.write(header.array()); repeat(height) { stream.write(row.array()) }
            }
            assertTrue("Fixture models a 350MiB RGB16 conversion", file.length() > 350L * 1024 * 1024 - 10L * 1024 * 1024)
            val preview = TiffImages.decode(file, 720)
            assertTrue(preview.width <= 720); preview.recycle()
            assertTrue(TiffNative.writePng(file.path, output.path))
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(output.path, bounds)
            assertEquals(width, bounds.outWidth); assertEquals(height, bounds.outHeight)
            @Suppress("DEPRECATION")
            val decoder = android.graphics.BitmapRegionDecoder.newInstance(output.path, false)!!
            try {
                val region = decoder.decodeRegion(android.graphics.Rect(4000, 4000, 4052, 4002), BitmapFactory.Options())
                try { for (x in 0 until region.width) assertEquals(if (x % 2 == 0) android.graphics.Color.RED else android.graphics.Color.BLACK, region.getPixel(x, 0)) }
                finally { region.recycle() }
            } finally { decoder.recycle() }
        } finally { file.delete(); output.delete() }
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
            for (i in 0 until 200) {
                val x = 110f + i * .50f
                page.canvas.drawRect(x, 100f, x + .20f, 140f, paint)
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
            fun sharpEdges(bitmap: android.graphics.Bitmap) = (200 until 800).count { x ->
                kotlin.math.abs(android.graphics.Color.red(bitmap.getPixel(x, 384)) - android.graphics.Color.red(bitmap.getPixel(x + 1, 384))) > 110
            }
            val actual = sharpEdges(region); val previous = sharpEdges(magnified)
            assertTrue("Native viewport fine-line edges $actual vs stretched preview $previous", actual > previous + 20)
            region.recycle(); base.recycle(); magnified.recycle()
        }
        file.delete()
    }

}

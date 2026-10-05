package com.mistermikhail.fgallery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.mistermikhail.fgallery.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LibraryPerformanceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun catalogueSurvivesMemoryResetAndRejectsRevokedAccess() {
        val item = MediaItem(9031, Uri.parse("file:///storage/emulated/0/DCIM/source.tif"), "source.tif", "image/tiff",
            MediaKind.IMAGE, 100, 6000, 4000, 0, "Camera", "DCIM/", 90000000, 101, "external_primary", "Internal", "/storage/emulated/0/DCIM", "/storage/emulated/0/DCIM/source.tif")
        LibrarySnapshot.save(context, LibrarySnapshot.Value(listOf(item), setOf(item.uriKey), true))
        LibrarySnapshot.forgetMemory()
        val restored = LibrarySnapshot.load(context, true)!!
        assertEquals(listOf(item), restored.items); assertEquals(setOf(item.uriKey), restored.bin)
        assertNull("Permission loss must reject a privileged snapshot", LibrarySnapshot.load(context, false))
        LibrarySnapshot.forgetMemory()
        File(context.filesDir, "library_snapshot_v1.json").delete()
    }

    @Test fun losslessFastPngPreservesTransparencyAndNativePixels() {
        val bitmap = Bitmap.createBitmap(2048, 1536, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(120, 220, 40))
        bitmap.setPixel(1024, 768, android.graphics.Color.argb(128, 200, 100, 50))
        val target = File(context.cacheDir, "fast-lossless.png")
        val start = android.os.SystemClock.elapsedRealtime()
        assertTrue(TiffNative.writeBitmapPng(bitmap, target.path))
        android.util.Log.i("FGalleryPerformance", "3MP lossless PNG: ${android.os.SystemClock.elapsedRealtime() - start}ms")
        val decoded = BitmapFactory.decodeFile(target.path)
        assertEquals(2048, decoded.width); assertEquals(1536, decoded.height)
        assertEquals(bitmap.getPixel(800, 900), decoded.getPixel(800, 900))
        assertEquals(bitmap.getPixel(1024, 768), decoded.getPixel(1024, 768))
        bitmap.recycle(); decoded.recycle(); target.delete()
    }

    @Test fun thumbnailReopensFromDiskWithoutSourceAndInvalidatesWhenSourceChanges() = runBlocking {
        val source = File(context.cacheDir, "thumbnail-cache.jpg")
        val bitmap = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.RED)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }; bitmap.recycle()
        val item = MediaItem(9033, Uri.fromFile(source), source.name, "image/jpeg", MediaKind.IMAGE, 0, 4000, 3000, 0, "Tests", "", source.length(), 555)
        val cached = ThumbnailCache.ensure(context, item)!!
        val decoded = BitmapFactory.decodeFile(cached.path)
        assertTrue("Large unindexed picture must be sampled", decoded.width <= 960); decoded.recycle()
        val bytes = cached.readBytes()
        assertEquals(cached, ThumbnailCache.cached(context, item.copy(id = 19033,
            uri = Uri.parse("content://media/external_primary/images/media/19033"), sourcePath = source.path)))
        source.delete()
        val reopened = ThumbnailCache.ensure(context, item)!!
        assertArrayEquals(bytes, reopened.readBytes())
        assertNull(ThumbnailCache.cached(context, item.copy(dateModifiedMillis = 556)))
        assertNull(ThumbnailCache.cached(context, item.copy(sizeBytes = item.sizeBytes + 1)))
        cached.delete()
        Unit
    }
}

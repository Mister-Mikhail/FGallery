package com.mistermikhail.fgallery

import android.graphics.Bitmap
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import com.mistermikhail.fgallery.data.EditedMediaStore
import com.mistermikhail.fgallery.data.MediaItem
import com.mistermikhail.fgallery.data.MediaKind
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CropExifPreservationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun image(path: File) {
        val bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(100, 170, 220))
        path.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 96, it) }
        bitmap.recycle()
    }

    @Test fun rawToJpegCropKeepsPortableExifAndUsesIncrementingCopyNames() {
        val source = File(context.filesDir, "fgallery-exif-source.dng")
        val edited = File(context.cacheDir, "fgallery-exif-edited.jpg")
        val copies = mutableListOf<File>()
        try {
            // Synthetic RAW-path fixture uses EXIF-bearing JPEG bytes; physical DNG/NEF/CR2
            // readers and maker notes still need Samsung S23 acceptance.
            image(source)
            image(edited)
            ExifInterface(source).apply {
                setAttribute(ExifInterface.TAG_MAKE, "Canon")
                setAttribute(ExifInterface.TAG_MODEL, "EOS 5DS R")
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:10:25 12:34:56")
                setAttribute(ExifInterface.TAG_EXPOSURE_TIME, "1/200")
                setAttribute(ExifInterface.TAG_F_NUMBER, "5.6")
                setAttribute(ExifInterface.TAG_GPS_LATITUDE, "55/1,45/1,0/1")
                setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
                setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "37/1,30/1,0/1")
                setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
                saveAttributes()
            }
            val sourceItem = MediaItem(1, Uri.fromFile(source), source.name, "image/x-adobe-dng",
                MediaKind.RAW, 1L, 80, 60, 0, "tests", "", source.length())
            val first = EditedMediaStore(context).saveCopy(sourceItem, Uri.fromFile(edited), "image/jpeg", "jpg")
            val second = EditedMediaStore(context).saveCopy(sourceItem, Uri.fromFile(edited), "image/jpeg", "jpg")
            val one = File(first.path!!)
            val two = File(second.path!!)
            copies += one
            copies += two
            assertEquals("fgallery-exif-source_1.jpg", one.name)
            assertEquals("fgallery-exif-source_2.jpg", two.name)
            for (copy in copies) {
                val exif = ExifInterface(copy)
                assertEquals("Canon", exif.getAttribute(ExifInterface.TAG_MAKE))
                assertEquals("EOS 5DS R", exif.getAttribute(ExifInterface.TAG_MODEL))
                assertEquals("2026:10:25 12:34:56", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
                assertNotNull(exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME))
                assertNotNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
                assertEquals(ExifInterface.ORIENTATION_NORMAL, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1))
            }
            assertTrue(source.exists())
        } finally {
            copies.forEach { it.delete() }
            source.delete()
            edited.delete()
        }
    }
}

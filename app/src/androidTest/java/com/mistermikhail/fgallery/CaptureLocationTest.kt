package com.mistermikhail.fgallery

import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import com.mistermikhail.fgallery.data.CaptureLocation
import com.mistermikhail.fgallery.data.MediaDetailsRepository
import com.mistermikhail.fgallery.data.MediaItem
import com.mistermikhail.fgallery.data.MediaKind
import com.mistermikhail.fgallery.ui.MediaDetailsSheet
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CaptureLocationTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun fileProviderOriginalShowsExifGpsAndMapAction() {
        val photo = jpeg("exif-location.jpg")
        ExifInterface(photo).apply {
            setLatLong(-35.123456, 149.123456)
            setAttribute(ExifInterface.TAG_MODEL, "Test camera")
            saveAttributes()
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", photo)
        val expected = "-35.123456, 149.123456"
        val item = item(photo, uri)
        val before = runBlocking { MediaDetailsRepository(context).load(item) }
        assertEquals(expected, (before.captureLocation as CaptureLocation.Available).coordinates.display())

        rule.setContent { MediaDetailsSheet(item, {}) }
        rule.waitUntil(8_000) { rule.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Место съёмки").assertIsDisplayed()
        rule.onNodeWithText(expected).assertIsDisplayed()
        rule.onNodeWithText("Открыть на карте").performScrollTo().assertIsDisplayed()
    }

    @Test fun missingGpsAndOverwrittenGpsAreDistinguished() = runBlocking {
        val photo = jpeg("exif-updated.jpg")
        val initial = item(photo, Uri.fromFile(photo))
        val repository = MediaDetailsRepository(context)
        assertEquals(CaptureLocation.Missing, repository.load(initial).captureLocation)
        ExifInterface(photo).apply {
            setLatLong(0.0, 0.0)
            saveAttributes()
        }
        val change = initial.copy(dateModifiedMillis = initial.dateModifiedMillis + 1, sizeBytes = photo.length())
        val updated = repository.load(change).captureLocation as CaptureLocation.Available
        assertEquals("0.000000, 0.000000", updated.coordinates.display())
    }

    @Test fun mediaStoreOwnOriginalReadsEmbeddedGps() = runBlocking {
        val photo = jpeg("media-store-location.jpg")
        ExifInterface(photo).apply {
            setLatLong(59.934280, 30.335099)
            saveAttributes()
        }
        val resolver = context.contentResolver
        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, photo.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FGalleryTests/")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }))
        try {
            requireNotNull(resolver.openOutputStream(uri)).use { output -> photo.inputStream().use { it.copyTo(output) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            val result = MediaDetailsRepository(context).load(item(photo, uri))
            assertEquals("59.934280, 30.335099", (result.captureLocation as CaptureLocation.Available).coordinates.display())
        } finally {
            resolver.delete(uri, null, null)
            photo.delete()
        }
        Unit
    }

    private fun jpeg(name: String): File = File(context.cacheDir, name).apply {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        try {
            outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        } finally {
            bitmap.recycle()
        }
    }

    private fun item(file: File, uri: Uri) = MediaItem(
        14, uri, file.name, "image/jpeg", MediaKind.IMAGE, 0, 32, 32, 0, "", "",
        file.length(), file.lastModified(),
    )
}

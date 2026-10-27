package com.mistermikhail.fgallery

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.homesoft.photo.libraw.LibRaw
import com.mistermikhail.fgallery.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RawSensorDimensionsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun sensorResolutionIsReadWithoutDemosaicAndCached() = runBlocking {
        val file = File(context.cacheDir, "raw-dimensions-stage1.dng")
        try {
            InstrumentationRegistry.getInstrumentation().context.assets.open("synthetic.dng").use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
            val item = MediaItem(97001, Uri.fromFile(file), file.name, "image/x-adobe-dng",
                MediaKind.RAW, 0L, 0, 0, 0L, "Tests", "", file.length(),
                dateModifiedMillis = file.lastModified())
            val expected = LibRaw().use { raw ->
                assertEquals(0, raw.open(file.canonicalPath))
                RawSensorDimensions.orientedSize(raw.width, raw.height, raw.orientation)
            }
            assertNotNull(expected)
            assertEquals(expected, RawSensorDimensions.read(context, item))
            assertEquals(expected, RawSensorDimensions.read(context, item))
            val details = MediaDetailsRepository(context).load(item)
            val resolution = details.sections.flatMap { it.fields }
                .single { it.label == "Разрешение" }.value
            assertEquals("${expected!!.first} × ${expected.second}", resolution)
        } finally {
            file.delete()
        }
    }

    @Test fun rotatedSensorAxesSwapExactlyOnce() {
        assertEquals(8742 to 11656, RawSensorDimensions.orientedSize(11656, 8742, LibRaw.ROTATE_90))
        assertEquals(8742 to 11656, RawSensorDimensions.orientedSize(11656, 8742, LibRaw.ROTATE_270))
        assertEquals(11656 to 8742, RawSensorDimensions.orientedSize(11656, 8742, LibRaw.ROTATE_180))
        assertNull(RawSensorDimensions.orientedSize(0, 100, LibRaw.ROTATE_90))
    }
}

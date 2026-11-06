package com.mistermikhail.fgallery

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.platform.app.InstrumentationRegistry
import com.mistermikhail.fgallery.data.VideoPlaybackMime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Real Android container probe, not a claim that every AVI codec is supported. */
class VideoContainerPlaybackTest {
    @Test fun m4vFilenameWithMp4BytesIsRecognizedOnDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "fgallery-container-test.m4v")
        try {
            instrumentation.context.assets.open("crop-video.mp4").use { source ->
                file.outputStream().use { destination -> source.copyTo(destination) }
            }
            assertEquals("video/mp4", VideoPlaybackMime.forFile(file.name, "application/octet-stream"))
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.absolutePath)
                assertTrue("Android must discover at least one MP4 track under .m4v", extractor.trackCount > 0)
                assertTrue("Video track must be recognized", (0 until extractor.trackCount).any { index ->
                    extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
                })
            } finally {
                extractor.release()
            }
        } finally {
            file.delete()
        }
    }

    @Test fun aviIsNotMislabelledAsMp4() {
        assertEquals("video/avi", VideoPlaybackMime.forFile("legacy.AVI", "application/octet-stream"))
    }
}

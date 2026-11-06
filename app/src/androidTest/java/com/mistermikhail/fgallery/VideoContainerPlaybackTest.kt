package com.mistermikhail.fgallery

import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
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


    @Test fun m4vCanBePreparedByActualMedia3Player() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "fgallery-media3-test.m4v")
        try {
            instrumentation.context.assets.open("crop-video.mp4").use { source ->
                file.outputStream().use { destination -> source.copyTo(destination) }
            }
            val ready = CountDownLatch(1)
            val error = AtomicReference<String?>(null)
            val playerRef = AtomicReference<ExoPlayer>()
            instrumentation.runOnMainSync {
                val player = ExoPlayer.Builder(context).build()
                playerRef.set(player)
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) ready.countDown()
                    }
                    override fun onPlayerError(exception: PlaybackException) {
                        error.set(exception.errorCodeName + ": " + exception.message)
                        ready.countDown()
                    }
                })
                player.setMediaItem(
                    PlayerMediaItem.Builder().setUri(Uri.fromFile(file))
                        .setMimeType(VideoPlaybackMime.forFile(file.name, "application/octet-stream"))
                        .build()
                )
                player.prepare()
            }
            try {
                assertTrue("Media3 did not prepare .m4v within 20s: ${error.get()}", ready.await(20, TimeUnit.SECONDS))
                assertEquals("Media3 rejected .m4v", null, error.get())
            } finally {
                instrumentation.runOnMainSync { playerRef.get()?.release() }
            }
        } finally {
            file.delete()
        }
    }

    @Test fun aviIsNotMislabelledAsMp4() {
        assertEquals("video/avi", VideoPlaybackMime.forFile("legacy.AVI", "application/octet-stream"))
    }
}

package com.mistermikhail.fgallery

import android.graphics.SurfaceTexture
import android.view.Surface
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

    /** Preparing alone does not exercise the actual decoder. Play the M4V sample. */
    @Test fun m4vDecodesFramesWithMedia3OnDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "fgallery-m4v-decode-test.m4v")
        try {
            instrumentation.context.assets.open("crop-video.mp4").use { source ->
                file.outputStream().use { destination -> source.copyTo(destination) }
            }
            // Assert first-frame rendering, not playback EOF (which depends on video duration).
            val firstFrame = CountDownLatch(1)
            val error = AtomicReference<String?>(null)
            val playerRef = AtomicReference<ExoPlayer>()
            val textureRef = AtomicReference<SurfaceTexture>()
            val surfaceRef = AtomicReference<Surface>()
            instrumentation.runOnMainSync {
                val player = ExoPlayer.Builder(context).build()
                playerRef.set(player)
                val texture = SurfaceTexture(false)
                val surface = Surface(texture)
                textureRef.set(texture)
                surfaceRef.set(surface)
                player.setVideoSurface(surface)
                player.addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() { firstFrame.countDown() }
                    override fun onPlayerError(exception: PlaybackException) {
                        error.set(exception.errorCodeName + ": " + exception.message)
                        firstFrame.countDown()
                    }
                })
                player.setMediaItem(
                    PlayerMediaItem.Builder().setUri(Uri.fromFile(file))
                        .setMimeType(VideoPlaybackMime.forFile(file.name, "application/octet-stream"))
                        .build()
                )
                player.prepare()
                player.play()
            }
            try {
                assertTrue("Media3 did not render the first M4V frame within 20s: ${error.get()}",
                    firstFrame.await(20, TimeUnit.SECONDS))
                assertEquals("Media3 failed to decode M4V", null, error.get())
            } finally {
                instrumentation.runOnMainSync {
                    playerRef.get()?.release()
                    surfaceRef.get()?.release()
                    textureRef.get()?.release()
                }
            }
        } finally {
            file.delete()
        }
    }


    /** Genuine ISO BMFF M4V/H.264/AAC fixture, generated by FFmpeg in CI. */
    @Test fun genuineH264M4vRendersFirstFrame() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "fgallery-genuine-h264.m4v")
        try {
            instrumentation.context.assets.open("m4v-h264-aac.m4v").use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
            file.inputStream().use { stream ->
                val header = ByteArray(8)
                assertEquals(8, stream.read(header))
                assertEquals("ftyp", String(header, 4, 4, Charsets.US_ASCII))
            }
            val firstFrame = CountDownLatch(1)
            val error = AtomicReference<String?>(null)
            val playerRef = AtomicReference<ExoPlayer>()
            val textureRef = AtomicReference<SurfaceTexture>()
            val surfaceRef = AtomicReference<Surface>()
            instrumentation.runOnMainSync {
                val player = ExoPlayer.Builder(context).build()
                playerRef.set(player)
                val texture = SurfaceTexture(false)
                val surface = Surface(texture)
                textureRef.set(texture)
                surfaceRef.set(surface)
                player.setVideoSurface(surface)
                player.addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() { firstFrame.countDown() }
                    override fun onPlayerError(exception: PlaybackException) {
                        error.set(exception.errorCodeName + ": " + exception.message)
                        firstFrame.countDown()
                    }
                })
                player.setMediaItem(
                    PlayerMediaItem.Builder().setUri(Uri.fromFile(file))
                        .setMimeType(VideoPlaybackMime.forFile(file.name, "application/octet-stream"))
                        .build()
                )
                player.prepare()
                player.play()
            }
            try {
                assertTrue("Genuine H264 M4V did not render within 20s: ${error.get()}",
                    firstFrame.await(20, TimeUnit.SECONDS))
                assertEquals("Genuine H264 M4V decoder error", null, error.get())
            } finally {
                instrumentation.runOnMainSync {
                    playerRef.get()?.release()
                    surfaceRef.get()?.release()
                    textureRef.get()?.release()
                }
            }
        } finally {
            file.delete()
        }
    }

    /** Genuine RIFF AVI sample must never be silently treated as an MP4 file. */
    @Test fun genuineAviHasRiffHeaderAndCorrectMime() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.context.assets.open("avi-mjpeg-pcm.avi").use { input ->
            val header = ByteArray(12)
            assertEquals(12, input.read(header))
            assertEquals("RIFF", String(header, 0, 4, Charsets.US_ASCII))
            assertEquals("AVI ", String(header, 8, 4, Charsets.US_ASCII))
        }
        assertEquals("video/avi",
            VideoPlaybackMime.forFile("avi-mjpeg-pcm.avi", "application/octet-stream"))
    }

    @Test fun aviIsNotMislabelledAsMp4() {
        assertEquals("video/avi", VideoPlaybackMime.forFile("legacy.AVI", "application/octet-stream"))
    }
}

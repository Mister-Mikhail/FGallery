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
import com.mistermikhail.fgallery.data.AviCodecInfo
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
    @Test fun genuineH264M4vRendersFirstFrame() = assertGenuineM4vRendersFirstFrame("m4v-h264-aac.m4v")

    @Test fun genuineHevcM4vRendersFirstFrame() = assertGenuineM4vRendersFirstFrame("m4v-hevc-aac.m4v")

    private fun assertGenuineM4vRendersFirstFrame(assetName: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "fgallery-genuine-" + assetName)
        try {
            instrumentation.context.assets.open(assetName).use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
            file.inputStream().use { stream ->
                val header = ByteArray(8)
                assertEquals(8, stream.read(header))
                assertEquals("ftyp", String(header, 4, 4, Charsets.US_ASCII))
            }
            // Verify that the generated fixture really exercises the requested codec,
            // rather than merely relying on its filename or ISO BMFF header.
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.absolutePath)
                val videoMimes = (0 until extractor.trackCount).mapNotNull { track ->
                    extractor.getTrackFormat(track).getString(MediaFormat.KEY_MIME)
                }.filter { it.startsWith("video/") }
                val expectedVideoMime = if (assetName.contains("hevc")) "video/hevc" else "video/avc"
                assertTrue("$assetName does not contain $expectedVideoMime: $videoMimes",
                    expectedVideoMime in videoMimes)
                val audioMimes = (0 until extractor.trackCount).mapNotNull { track ->
                    extractor.getTrackFormat(track).getString(MediaFormat.KEY_MIME)
                }.filter { it.startsWith("audio/") }
                assertTrue("$assetName must include an AAC track: $audioMimes",
                    "audio/mp4a-latm" in audioMimes)
            } finally {
                extractor.release()
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
                assertTrue("Genuine $assetName did not render within 20s: ${error.get()}",
                    firstFrame.await(20, TimeUnit.SECONDS))
                assertEquals("Genuine $assetName decoder error", null, error.get())
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

    /** Both generated AVI fixtures must be genuine RIFF/AVI containers, not renamed MP4. */
    @Test fun genuineMjpegAviHasRiffHeaderAndCorrectMime() = assertGenuineAviContainer("avi-mjpeg-pcm.avi")

    @Test fun genuineMpeg4AviHasRiffHeaderAndCorrectMime() = assertGenuineAviContainer("avi-mpeg4-mp3.avi")

    private fun assertGenuineAviContainer(assetName: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.context.assets.open(assetName).use { input ->
            val header = ByteArray(12)
            assertEquals("AVI header length for $assetName", 12, input.read(header))
            assertEquals("RIFF", String(header, 0, 4, Charsets.US_ASCII))
            assertEquals("AVI ", String(header, 8, 4, Charsets.US_ASCII))
        }
        assertEquals("video/avi", VideoPlaybackMime.forFile(assetName, "application/octet-stream"))
        // Exercise the bounded RIFF parser on genuine AVI bytes, as used for
        // unsupported-codec diagnostics on files opened through SAF.
        val fourCc = instrumentation.context.assets.open(assetName).use { AviCodecInfo.fourCc(it) }
        assertTrue("Missing AVI video FOURCC: $assetName", !fourCc.isNullOrBlank())
        if (assetName == "avi-mjpeg-pcm.avi") assertEquals("MJPG", fourCc)
        // Container recognition is deliberately separate from decoder support.
        // Neither this test nor MIME detection promises Media3 can demux AVI.
    }

    @Test fun aviIsNotMislabelledAsMp4() {
        assertEquals("video/avi", VideoPlaybackMime.forFile("legacy.AVI", "application/octet-stream"))
    }
}

package com.mistermikhail.fgallery

import android.graphics.SurfaceTexture
import android.net.Uri
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.mistermikhail.fgallery.data.AviCodecInfo
import com.mistermikhail.fgallery.data.VideoPlaybackMime
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * AVI compatibility is not guaranteed by its extension or FOURCC.
 * Each fixture must either render a frame or surface an explicit player error;
 * a silent black screen is a regression.
 */
class AviPlaybackOutcomeTest {
    @Test fun mjpegPcmReportsRealPlaybackOutcome() = assertPlaybackOutcome("avi-mjpeg-pcm.avi")

    @Test fun mpeg4Mp3ReportsRealPlaybackOutcome() = assertPlaybackOutcome("avi-mpeg4-mp3.avi")

    private fun assertPlaybackOutcome(assetName: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "fgallery-outcome-$assetName")
        instrumentation.context.assets.open(assetName).use { source ->
            file.outputStream().use { source.copyTo(it) }
        }
        val firstFrameOrError = CountDownLatch(1)
        val failure = AtomicReference<PlaybackException?>()
        val playerRef = AtomicReference<ExoPlayer>()
        val textureRef = AtomicReference<SurfaceTexture>()
        val surfaceRef = AtomicReference<Surface>()
        try {
            file.inputStream().use { assertNotNull("Missing video FOURCC", AviCodecInfo.fourCc(it)) }
            instrumentation.runOnMainSync {
                val player = ExoPlayer.Builder(context).build()
                playerRef.set(player)
                val texture = SurfaceTexture(false)
                textureRef.set(texture)
                val surface = Surface(texture)
                surfaceRef.set(surface)
                player.setVideoSurface(surface)
                player.addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() { firstFrameOrError.countDown() }
                    override fun onPlayerError(error: PlaybackException) {
                        failure.set(error)
                        firstFrameOrError.countDown()
                    }
                })
                player.setMediaItem(
                    MediaItem.Builder().setUri(Uri.fromFile(file))
                        .setMimeType(VideoPlaybackMime.forFile(file.name, "application/octet-stream"))
                        .build()
                )
                player.prepare()
                player.play()
            }
            assertTrue(
                "$assetName neither rendered a frame nor reported an error in 20s",
                firstFrameOrError.await(20, TimeUnit.SECONDS)
            )
            // A player error is an acceptable outcome for AVI until a compatible
            // demuxer/decoder exists; the UI must display its codec diagnostics.
            failure.get()?.let { assertTrue(it.errorCodeName.isNotBlank()) }
        } finally {
            instrumentation.runOnMainSync {
                playerRef.get()?.release()
                surfaceRef.get()?.release()
                textureRef.get()?.release()
            }
            file.delete()
        }
    }
}

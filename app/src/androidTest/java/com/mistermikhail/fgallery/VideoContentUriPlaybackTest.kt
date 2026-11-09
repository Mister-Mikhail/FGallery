package com.mistermikhail.fgallery

import android.content.ContentValues
import android.provider.MediaStore
import android.net.Uri
import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.mistermikhail.fgallery.data.VideoPlaybackMime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android 16 regression for MediaStore content:// playback, not just file://.
 * Fixture is MP4 bytes with an M4V filename; independent M4V codecs remain unverified.
 */
class VideoContentUriPlaybackTest {
    @Test fun m4vMediaStoreContentUriRendersFirstFrame() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "fgallery-content-uri-test.m4v")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/FGalleryTests/")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        assertNotNull("MediaStore insert failed", uri)
        val mediaUri = requireNotNull(uri)
        try {
            resolver.openOutputStream(mediaUri)?.use { output ->
                instrumentation.context.assets.open("crop-video.mp4").use { input -> input.copyTo(output) }
            } ?: error("Cannot write MediaStore sample")
            resolver.update(mediaUri, ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }, null, null)

            val firstFrame = CountDownLatch(1)
            val failure = AtomicReference<String?>(null)
            val playerRef = AtomicReference<ExoPlayer?>()
            val textureRef = AtomicReference<SurfaceTexture?>()
            val surfaceRef = AtomicReference<Surface?>()
            instrumentation.runOnMainSync {
                val texture = SurfaceTexture(false)
                val surface = Surface(texture)
                textureRef.set(texture)
                surfaceRef.set(surface)
                val player = ExoPlayer.Builder(context).build()
                playerRef.set(player)
                player.setVideoSurface(surface)
                player.addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() { firstFrame.countDown() }
                    override fun onPlayerError(error: PlaybackException) {
                        failure.set("${error.errorCodeName}: ${error.message}")
                        firstFrame.countDown()
                    }
                })
                player.setMediaItem(
                    MediaItem.Builder().setUri(mediaUri)
                        .setMimeType(VideoPlaybackMime.forFile("fgallery-content-uri-test.m4v", "application/octet-stream"))
                        .build()
                )
                player.prepare()
                player.play()
            }
            try {
                assertTrue("No first frame within 20s: ${failure.get()}", firstFrame.await(20, TimeUnit.SECONDS))
                assertEquals("Media3 failed on MediaStore content URI", null, failure.get())
            } finally {
                instrumentation.runOnMainSync {
                    playerRef.get()?.release()
                    surfaceRef.get()?.release()
                    textureRef.get()?.release()
                }
            }
        } finally {
            resolver.delete(mediaUri, null, null)
        }
    }
}

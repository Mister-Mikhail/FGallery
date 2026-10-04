package com.mistermikhail.fgallery

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.view.MotionEvent
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.canhub.cropper.CropImageView
import com.mistermikhail.fgallery.data.EditedMediaStore
import com.mistermikhail.fgallery.data.MediaItem
import com.mistermikhail.fgallery.data.MediaKind
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import java.io.File

class CropSaveTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun fixture(name: String, color: Int): File = File(context.cacheDir, name).apply {
        val bitmap = Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun item(uri: Uri, name: String) = MediaItem(1, uri, name, "image/png", MediaKind.IMAGE, 0, 160, 120, 0, "Tests", "Pictures/FGalleryTests/", 1)

    @Test fun upperCornersMoveDownAndEditorReturnsReadableCrop() {
        val file = fixture("editor-source.png", android.graphics.Color.RED)
        val source = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(context, ImageCropActivity::class.java).setData(source).putExtra("png", true)
        ActivityScenario.launchActivityForResult<ImageCropActivity>(intent).use { scenario ->
            var ready = false
            val deadline = SystemClock.uptimeMillis() + 15000
            while (!ready && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { ready = it.findViewById<CropImageView>(R.id.crop_image_view)?.cropRect?.width()?.let { width -> width > 0 } == true }
                if (!ready) SystemClock.sleep(100)
            }
            assertTrue("Image must load into editor", ready)
            for (right in listOf(false, true)) {
                scenario.onActivity { activity ->
                    val view = activity.findViewById<CropImageView>(R.id.crop_image_view)
                    val rect = view.cropWindowRect!!
                    val before = view.cropRect!!.top
                    val x = if (right) rect.right - 2f else rect.left + 2f
                    val y = rect.top + 2f
                    val distance = rect.height() * .12f
                    val time = SystemClock.uptimeMillis()
                    fun send(action: Int, targetY: Float, elapsed: Long) {
                        val event = MotionEvent.obtain(time, time + elapsed, action, x, targetY, 0)
                        view.dispatchTouchEvent(event)
                        event.recycle()
                    }
                    send(MotionEvent.ACTION_DOWN, y, 0)
                    for (step in 1..12) send(MotionEvent.ACTION_MOVE, y + distance * step / 12, step * 16L)
                    send(MotionEvent.ACTION_UP, y + distance, 220)
                    assertTrue("Upper ${if (right) "right" else "left"} corner must move down", view.cropRect!!.top > before)
                }
            }
            rule.onNodeWithText("Готово").performClick()
            val result = scenario.result
            assertEquals(Activity.RESULT_OK, result.resultCode)
            val bitmap = context.contentResolver.openInputStream(result.resultData.data!!).use { BitmapFactory.decodeStream(it) }
            assertNotNull("Returned URI must contain encoded image", bitmap)
            assertTrue(bitmap.height < 120)
            bitmap.recycle()
        }
    }

    @Test fun copyAndReplaceSaveBytesAndFailedReplaceRestoresOriginal() {
        val original = fixture("save-source.png", android.graphics.Color.RED)
        val edited = fixture("save-result.png", android.graphics.Color.GREEN)
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "fgallery-test.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/FGalleryTests/")
        })!!
        var copy: Uri? = null
        try {
            context.contentResolver.openOutputStream(uri, "w")!!.use { it.write(original.readBytes()) }
            val store = EditedMediaStore(context)
            val source = item(uri, "fgallery-test.png")
            copy = store.saveCopy(source, Uri.fromFile(edited), "image/png", "png")
            fun bytes(from: Uri) = context.contentResolver.openInputStream(from)!!.use { it.readBytes() }
            assertArrayEquals(edited.readBytes(), bytes(copy!!))
            assertArrayEquals(original.readBytes(), bytes(uri))
            store.replace(source, Uri.fromFile(edited), "image/png", "png")
            assertArrayEquals(edited.readBytes(), bytes(uri))
            val empty = File(context.cacheDir, "empty-crop.png").apply { writeBytes(byteArrayOf()) }
            try {
                store.replace(source, Uri.fromFile(empty), "image/png", "png")
                fail("Empty result must fail")
            } catch (_: IllegalStateException) { }
            assertArrayEquals("Failed replacement must restore source", edited.readBytes(), bytes(uri))
        } finally {
            copy?.let { context.contentResolver.delete(it, null, null) }
            context.contentResolver.delete(uri, null, null)
        }
    }
}

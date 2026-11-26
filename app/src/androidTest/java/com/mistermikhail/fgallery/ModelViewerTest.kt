package com.mistermikhail.fgallery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Debug
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.FileProvider
import com.mistermikhail.fgallery.data.MediaItem
import com.mistermikhail.fgallery.data.MediaKind
import com.mistermikhail.fgallery.data.ModelFormat
import com.mistermikhail.fgallery.data.models.ModelPreviewCache
import com.mistermikhail.fgallery.ui.ModelViewer
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ModelViewerTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context get() = rule.activity.applicationContext

    @Test fun glbWithEmbeddedTextureDrawsAndReportsTransformedBounds() {
        val file = File(context.cacheDir, "transformed-textured.glb")
        writeTexturedModelFixture(file)
        val original = file.readBytes()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val item = item(file, uri)
        val startTime = SystemClock.elapsedRealtime()
        val initialPss = Debug.getPss()
        android.util.Log.i("FGallery3DQA", "opening transformed-textured.glb")
        rule.setContent { ModelViewer(item, {}) }
        assertRendered("4 × 3 × 0 м")
        android.util.Log.i("FGallery3DQA",
            "fixture=${file.name},open_ms=${SystemClock.elapsedRealtime() - startTime},pss_delta_kib=${Debug.getPss() - initialPss}")
        rule.onNodeWithText("м").performClick()
        rule.onNodeWithText("400 × 300 × 0 см").assertIsDisplayed()
        rule.onNodeWithText("Вписать").performClick()
        rule.onNodeWithText("Сбросить").performClick()
        rule.onNodeWithText("400 × 300 × 0 см").assertIsDisplayed()

        val cache = File(context.cacheDir, "model-previews").listFiles().orEmpty().filter { it.extension == "glb" }
        val rendered = runBlocking { ModelPreviewCache.load(context, item, ModelFormat.GLB) }
        val (json, bin) = parse(rendered)
        val view = json.getJSONArray("bufferViews").getJSONObject(3)
        val image = ByteArray(view.getInt("byteLength"))
        bin.position(view.getInt("byteOffset"))
        bin.get(image)
        val png = BitmapFactory.decodeByteArray(image, 0, image.size)
        try {
            assertTrue(png.width <= 2048)
            assertTrue(png.hasAlpha())
            assertTrue(Color.alpha(png.getPixel(png.width / 2, png.height / 2)) in 100..250)
        } finally {
            png.recycle()
        }
        assertEquals(cache.map { it.name }.toSet(),
            File(context.cacheDir, "model-previews").listFiles().orEmpty().filter { it.extension == "glb" }.map { it.name }.toSet())
        assertArrayEquals(original, file.readBytes())
        android.util.Log.i("FGallery3DQA", "fixture=${file.name},cache_bytes=${cache.sumOf(File::length)}")
    }

    @Test fun stlDrawsAndViewerCanLeaveReopenAndRejectAnotherModel() {
        val file = File(context.cacheDir, "flat-shape.stl")
        file.writeText("""
            solid plane
              facet normal 0 0 1
                outer loop
                  vertex -3 -2 0
                  vertex 3 -2 0
                  vertex 0 2 0
                endloop
              endfacet
            endsolid plane
        """.trimIndent())
        val bad = File(context.cacheDir, "truncated.glb").apply { writeBytes("glTF".toByteArray()) }
        val selection = mutableStateOf<MediaItem?>(item(file, Uri.fromFile(file)))
        rule.setContent { selection.value?.let { ModelViewer(it, {}) } }
        assertRendered("6 × 4 × 0 ед. модели")
        rule.runOnIdle { selection.value = item(bad, Uri.fromFile(bad)) }
        rule.waitUntil(12_000) { rule.onAllNodesWithTag("model-error").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Открыть в другом приложении").assertIsDisplayed()
        rule.runOnIdle { selection.value = null }
        rule.waitForIdle()
        rule.runOnIdle { selection.value = item(file, Uri.fromFile(file)) }
        assertRendered("6 × 4 × 0 ед. модели")
    }

    private fun assertRendered(bounds: String) {
        rule.waitUntil(22_000) {
            rule.onAllNodesWithTag("model-ready").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithTag("model-error").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("model-ready").assertIsDisplayed()
        rule.onNodeWithText(bounds).assertIsDisplayed()
        android.util.Log.i("FGallery3DQA", "Filament completed the first GPU frame")
    }

    private fun item(file: File, uri: Uri) = MediaItem(
        id = file.name.hashCode().toLong(),
        uri = uri,
        name = file.name,
        mimeType = "application/octet-stream",
        kind = MediaKind.MODEL,
        dateTakenMillis = 0,
        width = 0,
        height = 0,
        durationMillis = 0,
        album = "Tests",
        relativePath = "",
        sizeBytes = file.length(),
        dateModifiedMillis = file.lastModified(),
    )

    private fun parse(source: ByteBuffer): Pair<JSONObject, ByteBuffer> {
        val data = source.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val description = ByteArray(data.getInt(12))
        data.position(20)
        data.get(description)
        data.position(data.position() + 8)
        return JSONObject(description.toString(Charsets.UTF_8).trim()) to data.slice()
    }
}

internal fun writeTexturedModelFixture(file: File) {
    val image = ByteArrayOutputStream()
    val bitmap = Bitmap.createBitmap(2050, 8, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.argb(190, 255, 90, 35))
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, image)
    bitmap.recycle()

    val geometry = ByteBuffer.allocate(96).order(ByteOrder.LITTLE_ENDIAN)
    floatArrayOf(0f, 0f, 0f, 2f, 0f, 0f, 1f, 1f, 0f,
        0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f,
        0f, 0f, 1f, 0f, 0.5f, 1f).forEach { geometry.putFloat(it) }
    val binary = 96 + image.size()
    val json = """
        {"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0]}],
        "nodes":[{"translation":[20,5,0],"children":[1]},{"scale":[-2,3,1],"mesh":0}],
        "meshes":[{"primitives":[{"attributes":{"POSITION":0,"NORMAL":1,"TEXCOORD_0":2},"material":0}]}],
        "materials":[{"doubleSided":true,"alphaMode":"BLEND","pbrMetallicRoughness":{
        "baseColorTexture":{"index":0},"metallicFactor":0,"roughnessFactor":0.8}}],
        "textures":[{"source":0}],"images":[{"bufferView":3,"mimeType":"image/png"}],
        "buffers":[{"byteLength":$binary}],
        "bufferViews":[{"buffer":0,"byteLength":36,"target":34962},
        {"buffer":0,"byteOffset":36,"byteLength":36,"target":34962},
        {"buffer":0,"byteOffset":72,"byteLength":24,"target":34962},
        {"buffer":0,"byteOffset":96,"byteLength":${image.size()}}],
        "accessors":[{"bufferView":0,"componentType":5126,"type":"VEC3","count":3,"min":[0,0,0],"max":[2,1,0]},
        {"bufferView":1,"componentType":5126,"type":"VEC3","count":3},
        {"bufferView":2,"componentType":5126,"type":"VEC2","count":3}]}
    """.trimIndent().toByteArray(Charsets.UTF_8)
    val textPadding = (4 - json.size % 4) % 4
    val binPadding = (4 - binary % 4) % 4
    val size = 28 + json.size + textPadding + binary + binPadding
    val glb = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        .putInt(0x46546C67).putInt(2).putInt(size).putInt(json.size + textPadding).putInt(0x4E4F534A).put(json)
    repeat(textPadding) { glb.put(0x20) }
    glb.putInt(binary + binPadding).putInt(0x004E4942).put(geometry.array()).put(image.toByteArray())
    file.writeBytes(glb.array())
}

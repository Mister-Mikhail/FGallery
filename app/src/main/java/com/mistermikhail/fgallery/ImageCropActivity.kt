package com.mistermikhail.fgallery

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.canhub.cropper.CropImageView
import com.mistermikhail.fgallery.ui.theme.FGalleryTheme
import java.io.File

/** Own toolbar/result flow: the library's deprecated Activity has no toolbar with NoActionBar. */
class ImageCropActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = intent.data ?: run { finish(); return }
        setContent {
            FGalleryTheme {
                var view by remember { mutableStateOf<CropImageView?>(null) }
                var loaded by remember { mutableStateOf(false) }
                var saving by remember { mutableStateOf(false) }
                var error by remember { mutableStateOf<String?>(null) }
                BackHandler { if (!saving) finish() }
                Column(Modifier.fillMaxSize().systemBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { finish() }, enabled = !saving) { Text("Отмена") }
                        TextButton(onClick = { view?.rotateImage(90) }, enabled = loaded && !saving) { Text("Повернуть") }
                        TextButton(onClick = {
                            saving = true
                            error = null
                            try {
                                val png = intent.getBooleanExtra("png", false)
                                val output = File.createTempFile("crop_", if (png) ".png" else ".jpg", cacheDir)
                                view?.croppedImageAsync(
                                    saveCompressFormat = if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                                    saveCompressQuality = 96,
                                    customOutputUri = androidx.core.content.FileProvider.getUriForFile(this@ImageCropActivity, "$packageName.files", output),
                                )
                            } catch (_: Exception) {
                                saving = false
                                error = "Не удалось сохранить кадр. Проверьте свободное место."
                            }
                        }, enabled = loaded && !saving) { Text("Готово") }
                    }
                    error?.let { Text(it, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
                    if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                    AndroidView(
                        factory = { context ->
                            CropImageView(context).apply {
                                id = R.id.crop_image_view
                                guidelines = CropImageView.Guidelines.ON
                                isAutoZoomEnabled = false
                                setFixedAspectRatio(false)
                                setMultiTouchEnabled(true)
                                setMinCropResultSize(1, 1)
                                setOnTouchListener { _, event ->
                                    if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
                                    false
                                }
                                setOnSetImageUriCompleteListener { _, _, failure ->
                                    loaded = failure == null
                                    if (failure != null) error = "Этот формат не удалось открыть для кадрирования. Оригинал сохранён."
                                }
                                setOnCropImageCompleteListener { _, result ->
                                    saving = false
                                    val uri = result.uriContent
                                    if (result.error == null && uri != null) {
                                        setResult(RESULT_OK, Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                        finish()
                                    } else error = "Не удалось кадрировать изображение. Оригинал сохранён."
                                }
                                view = this
                                runCatching { setImageUriAsync(source) }.onFailure {
                                    error = "Файл недоступен для кадрирования"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
                DisposableEffect(Unit) {
                    onDispose {
                        view?.setOnSetImageUriCompleteListener(null)
                        view?.setOnCropImageCompleteListener(null)
                    }
                }
            }
        }
    }
}

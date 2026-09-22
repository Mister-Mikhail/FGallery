package bar.fgallery

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import bar.fgallery.ui.FGalleryApp

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { recreate() }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); requestMediaPermissions(); setContent { FGalleryApp() } }
    private fun requestMediaPermissions() { permissionLauncher.launch(if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)) }
}

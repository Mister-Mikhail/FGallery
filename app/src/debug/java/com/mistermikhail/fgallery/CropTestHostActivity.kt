package com.mistermikhail.fgallery

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.util.concurrent.atomic.AtomicReference

/** Test harness uses the same visible-activity result flow as the gallery. */
class CropTestHostActivity : ComponentActivity() {
    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result.set(it) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(android.widget.FrameLayout(this))
    }
    fun launchCrop(intent: Intent) { launcher.launch(intent) }
    companion object { val result = AtomicReference<ActivityResult?>() }
}

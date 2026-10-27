package com.mistermikhail.fgallery.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stage 2: warm startup, visible gallery scroll, viewer navigation and return.
 *
 * Requires a seeded media fixture for full viewer/RAW paths. Without media this
 * test generates a startup/empty-grid profile, not an image decode profile.
 * Do not claim coverage of RAW/JNI decoding or grid throughput until fixtures
 * are installed and the capture result is checked on a real test device.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val baselineRule = BaselineProfileRule()

    @Test
    fun launchScrollOpenViewerAndSwipe() = baselineRule.collect(
        packageName = "com.mistermikhail.fgallery",
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.waitForIdle()
        val w = device.displayWidth
        val h = device.displayHeight
        // Exercise common Compose grid scroll/rebound. The app must already
        // have sample albums for meaningful (nonempty) scroll coverage.
        repeat(3) {
            device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, 18)
            device.swipe(w / 2, h / 4, w / 2, h * 3 / 4, 18)
        }
        // Viewer flow with prepared fixtures: open tile, swipe media, go Back.
        device.click(w / 4, h / 3)
        device.waitForIdle()
        device.swipe(w * 3 / 4, h / 2, w / 4, h / 2, 24)
        device.swipe(w / 4, h / 2, w * 3 / 4, h / 2, 24)
        device.pressBack()
        device.waitForIdle()
    }
}

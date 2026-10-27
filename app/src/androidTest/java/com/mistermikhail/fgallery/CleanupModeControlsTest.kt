package com.mistermikhail.fgallery

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mistermikhail.fgallery.ui.CleanupEnableDialog
import com.mistermikhail.fgallery.ui.CleanupExitButton
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CleanupModeControlsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun promptRequiresExplicitEnableAndCancelLeavesDisabled() {
        var enabled = false
        rule.setContent {
            var visible by remember { mutableStateOf(true) }
            if (visible) CleanupEnableDialog(
                onCancel = { visible = false },
                onEnable = { visible = false; enabled = true },
            )
        }
        rule.onNodeWithText("Двойной тап будет сразу отправлять файл в корзину FGallery. Включить?").assertExists()
        rule.onNodeWithText("Отмена").performClick()
        rule.waitForIdle()
        assertFalse(enabled)
        rule.onNodeWithText("Включить режим уборки?").assertDoesNotExist()
    }

    @Test fun enablingThenBroomTapDisablesWithoutPrompt() {
        var disabled = false
        rule.setContent {
            var enabled by remember { mutableStateOf(false) }
            if (!enabled) CleanupEnableDialog(
                onCancel = {},
                onEnable = { enabled = true },
            ) else CleanupExitButton(onDisable = { enabled = false; disabled = true })
        }
        rule.onNodeWithText("Включить").performClick()
        rule.onNodeWithContentDescription("Выключить режим уборки").assertExists().performClick()
        rule.waitForIdle()
        assertTrue(disabled)
        rule.onNodeWithContentDescription("Выключить режим уборки").assertDoesNotExist()
    }
}

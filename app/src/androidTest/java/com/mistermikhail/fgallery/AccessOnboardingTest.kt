package com.mistermikhail.fgallery

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mistermikhail.fgallery.ui.AccessOnboarding
import com.mistermikhail.fgallery.ui.AccessWizardStep
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AccessOnboardingTest {
    @get:Rule val rule = createComposeRule()

    @Test fun laterAdvancesExactlyOneStepAndNeverInvokesSystemGrant() {
        var grantCount = 0
        rule.setContent {
            var step by remember { mutableStateOf<AccessWizardStep?>(AccessWizardStep.MEDIA) }
            step?.let {
                AccessOnboarding(it, onGrant = { grantCount++ },
                    onLater = { step = if (it == AccessWizardStep.MEDIA)
                        AccessWizardStep.ALL_FILES else null })
            }
        }
        rule.onNodeWithText("Шаг 1 из 2 · Фото и видео").assertExists()
        rule.onNodeWithText("Доступ ко всем файлам").assertDoesNotExist()
        rule.onNodeWithText("Позже").performClick()
        rule.onNodeWithText("Шаг 2 из 2 · Доступ ко всем файлам").assertExists()
        rule.onNodeWithText("Позже").performClick()
        rule.onNodeWithText("Шаг 2 из 2 · Доступ ко всем файлам").assertDoesNotExist()
        rule.runOnIdle { assertEquals(0, grantCount) }
    }

    @Test fun buttonProvidesOnlyOneGrantRequest() {
        var requests = 0
        rule.setContent {
            AccessOnboarding(AccessWizardStep.MEDIA, onGrant = { requests++ },
                onLater = {})
        }
        rule.onNodeWithText("Разрешить").performClick()
        rule.runOnIdle { assertEquals(1, requests) }
    }
}

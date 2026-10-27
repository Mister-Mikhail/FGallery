package com.mistermikhail.fgallery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import com.mistermikhail.fgallery.ui.RawOpeningIndicator
import com.mistermikhail.fgallery.ui.RAW_INDICATOR_SIZE_DP
import org.junit.Rule
import org.junit.Test

class RawOpeningIndicatorTest {
    @get:Rule val rule = createComposeRule()

    @Test fun showsAnimatedOpenRawProgressInCenter() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                RawOpeningIndicator(Modifier.align(Alignment.Center))
            }
        }
        rule.onNodeWithTag("raw-open-indicator")
            .assertIsDisplayed()
            .assertWidthIsEqualTo(RAW_INDICATOR_SIZE_DP.dp)
            .assertHeightIsEqualTo(RAW_INDICATOR_SIZE_DP.dp)
        rule.onNodeWithContentDescription("Открываем RAW").assertIsDisplayed()
    }
}

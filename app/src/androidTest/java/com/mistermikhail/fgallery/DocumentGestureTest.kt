package com.mistermikhail.fgallery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.mistermikhail.fgallery.ui.ZoomableDocumentPage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DocumentGestureTest {
    @get:Rule val rule = createComposeRule()

    @Test fun draggingOnPageScrollsDocument() {
        rule.setContent {
            LazyColumn(Modifier.fillMaxSize().testTag("pages")) {
                items(4) { index ->
                    ZoomableDocumentPage(Modifier.fillMaxWidth().height(500.dp).testTag("page-$index"), {}) { modifier ->
                        Canvas(modifier) { drawRect(Color.White) }
                    }
                }
            }
        }
        val before = rule.onNodeWithTag("pages").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        rule.onNodeWithTag("page-0").performTouchInput { swipeUp() }
        rule.waitForIdle()
        val after = rule.onNodeWithTag("pages").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("Dragging the page must scroll, not be swallowed", after > before + 50)
    }

    @Test fun doubleTapCycleAndOffCenterPinch() {
        rule.setContent {
            ZoomableDocumentPage(Modifier.fillMaxSize().testTag("page"), {}) { modifier ->
                Canvas(modifier) { drawRect(Color.White) }
            }
        }
        val page = rule.onNodeWithTag("page")
        page.performTouchInput { doubleClick(center) }
        rule.waitForIdle()
        page.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 130%"))
        page.performTouchInput { doubleClick(center) }
        rule.waitForIdle()
        page.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 720%"))
        page.performTouchInput { doubleClick(center) }
        rule.waitForIdle()
        page.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 100%"))
        page.performTouchInput {
            val focus = Offset(width * .35f, height * .4f)
            pinch(start0 = focus - Offset(30f, 0f), end0 = focus - Offset(100f, 0f), start1 = focus + Offset(30f, 0f), end1 = focus + Offset(100f, 0f), durationMillis = 500)
        }
        rule.waitForIdle()
        val value = page.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertTrue(value, value.removePrefix("Zoom ").removeSuffix("%").toInt() > 150)
    }
}

package com.mistermikhail.fgallery

import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mistermikhail.fgallery.data.*
import com.mistermikhail.fgallery.ui.PdfViewer
import com.mistermikhail.fgallery.ui.SvgViewer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class RealDocumentZoomTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun item(file: File, kind: MediaKind) = MediaItem(8001, Uri.fromFile(file), file.name,
        if (kind == MediaKind.PDF) "application/pdf" else "image/svg+xml", kind, 0, 320, 240, 0, "Tests", "", file.length())

    private fun exercise(node: SemanticsNodeInteraction) {
        node.performTouchInput {
            val focus = Offset(width * .35f, height * .4f)
            pinch(start0 = focus - Offset(25f, 0f), end0 = focus - Offset(75f, 0f), start1 = focus + Offset(25f, 0f), end1 = focus + Offset(75f, 0f), durationMillis = 500)
        }
        rule.waitForIdle() // The release event used to feed an unspecified centroid into the transform.
        val scale = node.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertTrue(scale, scale.removePrefix("Zoom ").removeSuffix("%").toInt() > 150)
        node.performTouchInput {
            pinch(start0 = center - Offset(90f, 0f), end0 = center - Offset(5f, 0f), start1 = center + Offset(90f, 0f), end1 = center + Offset(5f, 0f), durationMillis = 500)
        }
        rule.waitForIdle()
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 100%"))
        node.performTouchInput { doubleClick(center) }
        rule.waitForIdle()
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 310%"))
    }

    @Test fun realPdfPinchReleaseAndScroll() {
        val file = File(context.cacheDir, "gesture.pdf")
        val pdf = PdfDocument()
        try {
            repeat(3) { index ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(320, 240, index + 1).create())
                page.canvas.drawColor(android.graphics.Color.WHITE)
                pdf.finishPage(page)
            }
            file.outputStream().use { pdf.writeTo(it) }
        } finally { pdf.close() }
        rule.setContent { PdfViewer(item(file, MediaKind.PDF), {}) }
        rule.waitUntil(15000) { rule.onAllNodesWithTag("pdf-page-0").fetchSemanticsNodes().isNotEmpty() }
        exercise(rule.onNodeWithTag("pdf-page-0"))
    }

    @Test fun realSvgPinchReleaseAndReset() {
        val file = File(context.cacheDir, "gesture.svg")
        file.writeText("""<svg xmlns="http://www.w3.org/2000/svg" width="320" height="240"><rect width="320" height="240" fill="red"/></svg>""")
        rule.setContent { SvgViewer(item(file, MediaKind.SVG), {}) }
        val matcher = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 100%")
        rule.waitUntil(15000) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        exercise(rule.onNodeWithTag("svg-page"))
    }
}

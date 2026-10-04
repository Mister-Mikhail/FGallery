package com.mistermikhail.fgallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.caverock.androidsvg.SVG
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream

/** PdfRenderer requires a seekable descriptor; document providers need not supply one. */
class PdfSession(context: Context, uri: Uri) : Closeable {
    private val file = File.createTempFile("pdf_", ".pdf", context.cacheDir)
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var compatible: io.legere.pdfiumandroid.PdfDocument? = null

    val pageCount: Int
    init {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            } ?: error("Нет доступа к PDF")
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                renderer = PdfRenderer(descriptor!!)
            } catch (_: Exception) {
                descriptor?.close()
                descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                compatible = io.legere.pdfiumandroid.PdfiumCore(context).newDocument(descriptor!!, "")
            }
            pageCount = renderer?.pageCount ?: compatible!!.getPageCount()
            require(pageCount > 0) { "PDF не содержит страниц" }
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    @Synchronized
    fun render(index: Int, edge: Int): Bitmap {
        if (renderer == null) {
            val document = compatible ?: error("PDF закрыт")
            return requireNotNull(document.openPage(index)).use { page ->
                val width = page.getPageWidthPoint().coerceAtLeast(1)
                val height = page.getPageHeightPoint().coerceAtLeast(1)
                val scale = edge.toFloat() / maxOf(width, height)
                val bitmap = Bitmap.createBitmap((width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                try {
                    page.renderPageBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, true)
                    bitmap
                } catch (e: Exception) { bitmap.recycle(); throw e }
            }
        }
        val pdf = renderer ?: error("PDF закрыт")
        return pdf.openPage(index).use { page ->
            val scale = edge.toFloat() / maxOf(page.width, page.height)
            val bitmap = Bitmap.createBitmap(
                (page.width * scale).toInt().coerceAtLeast(1),
                (page.height * scale).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            bitmap.eraseColor(Color.WHITE)
            try {
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } catch (e: Exception) {
                bitmap.recycle()
                throw e
            }
        }
    }

    @Synchronized
    override fun close() {
        compatible?.close()
        compatible = null
        renderer?.close()
        renderer = null
        descriptor?.close()
        descriptor = null
        file.delete()
    }
}

object VisualDocuments {
    fun loadSvg(context: Context, uri: Uri): SVG =
        context.contentResolver.openInputStream(uri)?.use { SVG.getFromInputStream(it) }
            ?: error("Нет доступа к SVG")

    fun svgPreview(context: Context, uri: Uri, edge: Int): Bitmap {
        val svg = loadSvg(context, uri)
        val ratio = svg.documentAspectRatio.takeIf { it > 0f && it.isFinite() } ?: 1f
        val width = if (ratio >= 1f) edge else (edge * ratio).toInt().coerceAtLeast(1)
        val height = if (ratio >= 1f) (edge / ratio).toInt().coerceAtLeast(1) else edge
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            svg.renderToCanvas(Canvas(it), RectF(0f, 0f, width.toFloat(), height.toFloat()))
        }
    }
}

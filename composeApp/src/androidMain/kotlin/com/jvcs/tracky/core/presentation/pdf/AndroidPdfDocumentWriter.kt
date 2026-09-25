package com.jvcs.tracky.core.presentation.pdf

import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * Writes pages with the platform [PdfDocument], replaying each recorded picture onto the page
 * canvas, so text and shapes stay vectors.
 *
 * Compose records graphics layers into a picture by drawing their content directly (a picture
 * canvas is not hardware accelerated), so layered content survives the replay.
 */
class AndroidPdfDocumentWriter : PdfDocumentWriter {

    override fun open(spec: PdfPageSpec): PdfDocumentSession = Session(spec)

    private class Session(spec: PdfPageSpec) : PdfDocumentSession {

        private val document = PdfDocument()
        private val pageWidth =
            spec.pageSize.width.value
                .roundToInt()
        private val pageHeight =
            spec.pageSize.height.value
                .roundToInt()
        private var pageCount = 0

        override fun addPage(page: CapturedPdfPage) {
            pageCount++
            val pdfPage = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageCount).create())

            val canvas = pdfPage.canvas
            // Pictures are recorded in points; scale only if a page was captured at another size.
            if (page.widthPx != pageWidth || page.heightPx != pageHeight) {
                canvas.scale(pageWidth.toFloat() / page.widthPx, pageHeight.toFloat() / page.heightPx)
            }
            canvas.drawPicture(page.picture)
            document.finishPage(pdfPage)
        }

        override fun finish(): ByteArray =
            try {
                ByteArrayOutputStream().also(document::writeTo).toByteArray()
            } finally {
                document.close()
            }

        override fun cancel() = document.close()
    }
}

package com.jvcs.tracky.core.presentation.pdf

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.io.ByteArrayOutputStream

/**
 * Writes pages with Apache PDFBox. Skia pages arrive as 300 dpi raster images, so each page is one
 * losslessly compressed image stretched over the whole sheet.
 */
class PdfBoxDocumentWriter : PdfDocumentWriter {

    override fun open(spec: PdfPageSpec): PdfDocumentSession = Session(spec)

    private class Session(spec: PdfPageSpec) : PdfDocumentSession {

        private val document = PDDocument()
        private val mediaBox = PDRectangle(spec.pageSize.width.value, spec.pageSize.height.value)

        override fun addPage(page: CapturedPdfPage) {
            val pdPage = PDPage(mediaBox)
            document.addPage(pdPage)
            val image = LosslessFactory.createFromImage(document, page.image.toComposeImageBitmap().toAwtImage())
            PDPageContentStream(document, pdPage).use { content ->
                content.drawImage(image, 0f, 0f, mediaBox.width, mediaBox.height)
            }
        }

        override fun finish(): ByteArray = document.use { ByteArrayOutputStream().also(it::save).toByteArray() }

        override fun cancel() = document.close()
    }
}

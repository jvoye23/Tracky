@file:OptIn(ExperimentalForeignApi::class)

package com.jvcs.tracky.core.presentation.pdf

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Color
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawPDFPage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGDataProviderCreateWithCFData
import platform.CoreGraphics.CGDataProviderRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGPDFDocumentCreateWithProvider
import platform.CoreGraphics.CGPDFDocumentGetNumberOfPages
import platform.CoreGraphics.CGPDFDocumentGetPage
import platform.CoreGraphics.CGPDFDocumentRelease
import platform.CoreGraphics.CGPDFPageGetBoxRect
import platform.CoreGraphics.CGPDFPageRef
import platform.CoreGraphics.kCGPDFMediaBox
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.create
import platform.Foundation.writeToFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosPdfDocumentWriterTest {

    private val writer = IosPdfDocumentWriter()

    @Test
    fun portraitDocumentHasEveryPageOnAnA4MediaBox() {
        val bytes = writePdf(PdfPageSpec(), pageCount = 2)

        withPdf(bytes) { pageCount, page ->
            assertEquals(2, pageCount)
            assertEquals(595.0 to 842.0, mediaBox(page))
        }
    }

    @Test
    fun landscapeDocumentSwapsTheMediaBox() {
        val bytes = writePdf(PdfPageSpec(orientation = PageOrientation.Landscape), pageCount = 1)

        withPdf(bytes) { pageCount, page ->
            assertEquals(1, pageCount)
            assertEquals(842.0 to 595.0, mediaBox(page))
        }
    }

    @Test
    fun pageImageIsDrawnUpright() {
        val bytes = writePdf(PdfPageSpec(), pageCount = 1)
        NSTemporaryDirectory().let { dir ->
            bytes.usePinned {
                NSData
                    .create(bytes = it.addressOf(0), length = bytes.size.toULong())
                    .writeToFile("${dir}tracky-sample.pdf", atomically = true)
            }
            println("Sample PDF (${bytes.size} bytes): ${dir}tracky-sample.pdf")
        }

        withPdf(bytes) { _, page ->
            val (top, bottom) = rednessOfTopAndBottomRows(page)
            assertTrue(top > 200 && bottom < 50, "expected red band at the top, got top=$top bottom=$bottom")
        }
    }

    private fun writePdf(spec: PdfPageSpec, pageCount: Int): ByteArray {
        val session = writer.open(spec)
        repeat(pageCount) { session.addPage(syntheticPage(spec)) }
        return session.finish()
    }

    /** A 300 dpi page with a red band across its top tenth and a blue box in the middle. */
    private fun syntheticPage(spec: PdfPageSpec): CapturedPdfPage {
        val width = (spec.pageSize.width.value * renderDensity).toInt()
        val height = (spec.pageSize.height.value * renderDensity).toInt()
        val surface = Surface.makeRasterN32Premul(width, height)
        try {
            surface.canvas.clear(Color.TRANSPARENT)
            surface.canvas.drawRect(Rect.makeWH(width.toFloat(), height / 10f), Paint().apply { color = Color.RED })
            surface.canvas.drawRect(
                Rect.makeXYWH(width / 4f, height / 3f, width / 2f, height / 3f),
                Paint().apply { color = Color.BLUE },
            )
            return CapturedPdfPage(surface.makeImageSnapshot())
        } finally {
            surface.close()
        }
    }

    private fun withPdf(bytes: ByteArray, block: (pageCount: Int, firstPage: CGPDFPageRef?) -> Unit) {
        val data = bytes.usePinned { CFDataCreate(null, it.addressOf(0).reinterpret<UByteVar>(), bytes.size.toLong()) }
        val provider = CGDataProviderCreateWithCFData(data)
        val document = CGPDFDocumentCreateWithProvider(provider)
        try {
            block(CGPDFDocumentGetNumberOfPages(document).toInt(), CGPDFDocumentGetPage(document, 1u))
        } finally {
            CGPDFDocumentRelease(document)
            CGDataProviderRelease(provider)
            CFRelease(data)
        }
    }

    private fun mediaBox(page: CGPDFPageRef?) =
        CGPDFPageGetBoxRect(page, kCGPDFMediaBox).useContents { size.width to size.height }

    /** Renders [page] at 1/10 scale and returns the red channel of its first and last pixel rows. */
    private fun rednessOfTopAndBottomRows(page: CGPDFPageRef?): Pair<Int, Int> {
        val width = 60
        val height = 84
        val pixels = UByteArray(width * height * 4)
        pixels.usePinned { pinned ->
            val colorSpace = CGColorSpaceCreateDeviceRGB()
            val context =
                CGBitmapContextCreate(
                    pinned.addressOf(0),
                    width.toULong(),
                    height.toULong(),
                    8u,
                    (width * 4).toULong(),
                    colorSpace,
                    CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
                )
            CGContextScaleCTM(context, width / 595.0, height / 842.0)
            CGContextDrawPDFPage(context, page)
            CGContextRelease(context)
            CGColorSpaceRelease(colorSpace)
        }
        // Bitmap memory is stored top row first.
        val center = width / 2 * 4
        return pixels[center].toInt() to pixels[(height - 1) * width * 4 + center].toInt()
    }
}

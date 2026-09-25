package com.jvcs.tracky.core.presentation.pdf

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.EncodedImageFormat
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataCreateMutable
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGColorRenderingIntent
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGDataConsumerCreateWithCFData
import platform.CoreGraphics.CGDataConsumerRelease
import platform.CoreGraphics.CGDataProviderCreateWithCFData
import platform.CoreGraphics.CGDataProviderRelease
import platform.CoreGraphics.CGImageCreateWithPNGDataProvider
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGPDFContextBeginPage
import platform.CoreGraphics.CGPDFContextClose
import platform.CoreGraphics.CGPDFContextCreate
import platform.CoreGraphics.CGPDFContextEndPage
import platform.CoreGraphics.CGRectMake

/**
 * Writes pages with the Core Graphics PDF context. Unlike `UIGraphicsBeginPDFContextToData`, a
 * `CGContext` isn't bound to the calling thread's UIKit context stack, so a session may be driven
 * from whichever [kotlinx.coroutines.Dispatchers.Default] thread each call lands on.
 */
class IosPdfDocumentWriter : PdfDocumentWriter {

    override fun open(spec: PdfPageSpec): PdfDocumentSession = CoreGraphicsPdfSession(spec)
}

@OptIn(ExperimentalForeignApi::class)
private class CoreGraphicsPdfSession(spec: PdfPageSpec) : PdfDocumentSession {

    private val pageRect =
        CGRectMake(
            0.0,
            0.0,
            spec.pageSize.width.value
                .toDouble(),
            spec.pageSize.height.value
                .toDouble(),
        )
    private val output = CFDataCreateMutable(null, 0)
    private val consumer = CGDataConsumerCreateWithCFData(output)
    private var context = CGPDFContextCreate(consumer, pageRect, null)

    override fun addPage(page: CapturedPdfPage) {
        val context = checkNotNull(context) { "session already finished" }
        autoreleasepool {
            val image = checkNotNull(pngToCgImage(encodePng(page))) { "could not encode page image" }
            CGPDFContextBeginPage(context, null)
            // Core Graphics' origin is bottom-left, and drawing a CGImage into a rect keeps it upright.
            CGContextDrawImage(context, pageRect, image)
            CGPDFContextEndPage(context)
            CGImageRelease(image)
        }
    }

    override fun finish(): ByteArray {
        CGPDFContextClose(checkNotNull(context) { "session already finished" })
        release()

        val length = CFDataGetLength(output).toInt()
        val bytes = CFDataGetBytePtr(output)?.readBytes(length) ?: ByteArray(0)
        CFRelease(output)
        return bytes
    }

    override fun cancel() {
        if (context == null) return
        release()
        CFRelease(output)
    }

    private fun release() {
        CGContextRelease(context)
        CGDataConsumerRelease(consumer)
        context = null
    }
}

private fun encodePng(page: CapturedPdfPage): ByteArray {
    val data = checkNotNull(page.image.encodeToData(EncodedImageFormat.PNG)) { "could not encode page image" }
    return try {
        data.bytes
    } finally {
        data.close()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun pngToCgImage(png: ByteArray) =
    png.usePinned { pinned ->
        val data = CFDataCreate(null, pinned.addressOf(0).reinterpret<UByteVar>(), png.size.toLong())
        val provider = CGDataProviderCreateWithCFData(data)
        CFRelease(data)

        val image =
            CGImageCreateWithPNGDataProvider(provider, null, true, CGColorRenderingIntent.kCGRenderingIntentDefault)
        CGDataProviderRelease(provider)
        image
    }

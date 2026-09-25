package com.jvcs.tracky.core.presentation.pdf

import androidx.compose.ui.Modifier

/**
 * Turns captured pages into PDF bytes. Pages are streamed through a [PdfDocumentSession], so only
 * one page is held in memory at a time. Sessions are called off the main thread and may block.
 */
interface PdfDocumentWriter {

    fun open(spec: PdfPageSpec): PdfDocumentSession
}

interface PdfDocumentSession {

    fun addPage(page: CapturedPdfPage)

    fun finish(): ByteArray

    /** Called instead of [finish] when rendering fails or its caller is cancelled. */
    fun cancel()
}

/**
 * One rendered page: an `android.graphics.Picture` on Android (vector), a Skia raster image on
 * jvm and iOS. Its pixel size is the page size in points times [renderDensity].
 */
expect class CapturedPdfPage {

    val widthPx: Int

    val heightPx: Int
}

/**
 * Pixels per PDF point the pages are laid out at. Android records vector pictures, so 1px is one
 * point; Skia targets rasterize, so they render at 300 dpi to stay sharp in print.
 */
internal expect val renderDensity: Float

/**
 * Records what this node draws into a [CapturedPdfPage] instead of drawing it to the screen, and
 * hands it to [onCapture] on every draw.
 */
internal expect fun Modifier.capturePdfPage(onCapture: (CapturedPdfPage) -> Unit): Modifier

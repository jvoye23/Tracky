package com.jvcs.tracky.core.presentation.pdf

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.draw
import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import kotlin.math.roundToInt

actual class CapturedPdfPage(val image: Image) {

    actual val widthPx: Int get() = image.width

    actual val heightPx: Int get() = image.height
}

internal actual val renderDensity: Float = 300f / 72f

internal actual fun Modifier.capturePdfPage(onCapture: (CapturedPdfPage) -> Unit): Modifier =
    drawWithCache {
        val width = size.width.roundToInt()
        val height = size.height.roundToInt()
        onDrawWithContent {
            val surface = Surface.makeRasterN32Premul(width, height)
            try {
                surface.canvas.clear(Color.TRANSPARENT)
                draw(this, layoutDirection, surface.canvas.asComposeCanvas(), size) {
                    this@onDrawWithContent.drawContent()
                }
                onCapture(CapturedPdfPage(surface.makeImageSnapshot()))
            } finally {
                surface.close()
            }
        }
    }

package com.jvcs.tracky.core.presentation.pdf

import android.graphics.Picture
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.draw
import kotlin.math.roundToInt

actual class CapturedPdfPage(val picture: Picture) {

    actual val widthPx: Int get() = picture.width

    actual val heightPx: Int get() = picture.height
}

internal actual val renderDensity: Float = 1f

internal actual fun Modifier.capturePdfPage(onCapture: (CapturedPdfPage) -> Unit): Modifier =
    drawWithCache {
        val width = size.width.roundToInt()
        val height = size.height.roundToInt()
        onDrawWithContent {
            val picture = Picture()
            val canvas = Canvas(picture.beginRecording(width, height))
            draw(this, layoutDirection, canvas, size) { this@onDrawWithContent.drawContent() }
            picture.endRecording()
            onCapture(CapturedPdfPage(picture))
        }
    }

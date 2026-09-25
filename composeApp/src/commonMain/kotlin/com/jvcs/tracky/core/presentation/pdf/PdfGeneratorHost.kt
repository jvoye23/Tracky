package com.jvcs.tracky.core.presentation.pdf

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density

/**
 * Renders the documents of [generator]; compose it once in the screen that exports. It takes no
 * space and shows nothing: pages are drawn into [CapturedPdfPage]s instead of onto the screen.
 */
@Composable
fun PdfGeneratorHost(generator: PdfGenerator, modifier: Modifier = Modifier) {
    var frame by remember(generator) { mutableStateOf<PdfFrame?>(null) }
    LaunchedEffect(generator) { generator.serve { frame = it } }
    // Page sizes are points; this density makes one point renderDensity pixels.
    CompositionLocalProvider(LocalDensity provides Density(renderDensity, fontScale = 1f)) {
        // Hidden from accessibility too: the page text is not on screen.
        Box(modifier.clearAndSetSemantics {}) {
            when (val current = frame) {
                is PdfFrame.Measure -> {
                    PdfMeasurer(current.document, current.spec, current.result::complete)
                }

                // A fresh node per page, so each page is captured from its own first draw.
                is PdfFrame.Page -> {
                    key(current) {
                        with(current) { PdfPage(document, spec, placements, info, captured::complete) }
                    }
                }

                null -> {}
            }
        }
    }
}

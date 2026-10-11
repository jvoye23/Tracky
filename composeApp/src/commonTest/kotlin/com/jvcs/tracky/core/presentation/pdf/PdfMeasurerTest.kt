package com.jvcs.tracky.core.presentation.pdf

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlin.math.roundToInt
import kotlin.test.Test

/** Measuring a document only lays it out, so unlike page capture it runs on every platform. */
@OptIn(ExperimentalTestApi::class)
internal class PdfMeasurerTest {

    @Composable
    private fun Block(height: Int) = Spacer(Modifier.height(height.dp))

    @Composable
    private fun AtRenderDensity(content: @Composable () -> Unit) =
        CompositionLocalProvider(LocalDensity provides Density(renderDensity), content = content)

    private fun px(points: Int) = (points * renderDensity).roundToInt()

    @Test
    fun theMeasurerReportsEveryHeightInRenderPixels() =
        runComposeUiTest {
            var measured: PdfMeasurements? = null
            val document =
                buildPdfDocument {
                    header { Block(31) }
                    footer { Block(20) }
                    item { Block(100) }
                    pageBreak()
                    section(repeatingHeader = { Block(50) }) { item { Block(10) } }
                }
            setContent { AtRenderDensity { PdfMeasurer(document, PdfPageSpec()) { measured = it } } }
            waitUntil { measured != null }

            val sizes = checkNotNull(measured)
            assertThat(sizes.contentHeight).isEqualTo(px(842) - 2 * px(40))
            assertThat(sizes.headerHeight).isEqualTo(px(31))
            assertThat(sizes.footerHeight).isEqualTo(px(20))
            assertThat(sizes.blockHeights).containsExactly(px(100), 0, px(10))
            assertThat(sizes.sectionHeaderHeights).isEqualTo(mapOf(0 to px(50)))
        }
}

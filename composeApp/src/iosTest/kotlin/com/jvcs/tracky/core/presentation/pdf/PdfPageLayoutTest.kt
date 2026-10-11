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
import assertk.assertions.isNotNull
import kotlin.math.roundToInt
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
// In iosTest, not commonTest: on an Android device the page capture never fires and these time
// out. Whether that is the test environment or Android's PDF export is still open.
internal class PdfPageLayoutTest {

    @Composable
    private fun Block(height: Int) = Spacer(Modifier.height(height.dp))

    @Composable
    private fun AtRenderDensity(content: @Composable () -> Unit) =
        CompositionLocalProvider(LocalDensity provides Density(renderDensity), content = content)

    private fun px(points: Int) = (points * renderDensity).roundToInt()

    @Test
    fun aLandscapePageIsCapturedAtPageSizeWithItsPageInfo() =
        runComposeUiTest {
            var captured: CapturedPdfPage? = null
            var footerInfo: PdfPageInfo? = null
            val document =
                buildPdfDocument {
                    footer { info -> footerInfo = info }
                    item { Block(10) }
                }
            val landscape = PdfPageSpec(PageOrientation.Landscape)
            val info = PdfPageInfo(pageNumber = 2, pageCount = 3)
            setContent {
                AtRenderDensity {
                    PdfPage(document, landscape, listOf(PdfPlacement.Block(0)), info) { captured = it }
                }
            }
            waitUntil { captured != null }

            val page = checkNotNull(captured)
            assertThat(page.widthPx to page.heightPx).isEqualTo(px(842) to px(595))
            assertThat(footerInfo).isNotNull().isEqualTo(info)
        }
}

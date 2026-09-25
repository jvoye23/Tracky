package com.jvcs.tracky.core.presentation.pdf

import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.jvcs.tracky.designsystem.theme.Inter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
internal class PdfBoxDocumentWriterTest {

    private fun export(spec: PdfPageSpec): ByteArray {
        lateinit var bytes: ByteArray
        runComposeUiTest {
            val generator = PdfGenerator(PdfBoxDocumentWriter())
            lateinit var scope: CoroutineScope
            setContent {
                scope = rememberCoroutineScope()
                PdfGeneratorHost(generator)
            }
            val export =
                scope.async {
                    generator.generate(spec) {
                        item {
                            Text(
                                "Tracky export, page one",
                                style = TextStyle(fontFamily = Inter, fontSize = 24.sp),
                            )
                        }
                        pageBreak()
                        item { Text("Page two", style = TextStyle(fontFamily = Inter, fontSize = 24.sp)) }
                    }
                }
            waitUntil(timeoutMillis = 20_000) { export.isCompleted }
            bytes = export.getCompleted()
        }
        return bytes
    }

    @Test
    fun portraitPagesAreA4() {
        val bytes = export(PdfPageSpec())
        Loader.loadPDF(bytes).use { pdf ->
            assertThat(pdf.numberOfPages).isEqualTo(2)
            val box = pdf.getPage(0).mediaBox
            assertThat(box.width to box.height).isEqualTo(595f to 842f)
            // Fonts load in composition, so the first (captured) draw already shows the text.
            val page = PDFRenderer(pdf).renderImage(0)
            val inked = (0 until TEXT_AREA_BOTTOM).any { y -> (0 until page.width).any { page.getRGB(it, y) != WHITE } }
            assertThat(inked).isTrue()
        }
    }

    @Test
    fun landscapePagesAreA4Landscape() {
        Loader.loadPDF(export(PdfPageSpec(PageOrientation.Landscape))).use { pdf ->
            val box = pdf.getPage(1).mediaBox
            assertThat(box.width to box.height).isEqualTo(842f to 595f)
        }
    }

    private companion object {
        const val WHITE = -1
        const val TEXT_AREA_BOTTOM = 100
    }
}

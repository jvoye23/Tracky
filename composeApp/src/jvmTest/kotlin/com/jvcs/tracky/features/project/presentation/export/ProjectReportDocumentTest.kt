package com.jvcs.tracky.features.project.presentation.export

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.jvcs.tracky.core.presentation.pdf.CapturedPdfPage
import com.jvcs.tracky.core.presentation.pdf.PdfDocumentSession
import com.jvcs.tracky.core.presentation.pdf.PdfDocumentWriter
import com.jvcs.tracky.core.presentation.pdf.PdfGenerator
import com.jvcs.tracky.core.presentation.pdf.PdfGeneratorHost
import com.jvcs.tracky.core.presentation.pdf.PdfPageSpec
import com.jvcs.tracky.features.project.domain.export.SampleProjectFixture
import com.jvcs.tracky.features.project.domain.export.toProjectReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.datetime.TimeZone
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test

/** Set to a directory to get the sample report's pages there as PNGs, for comparing with the design. */
private const val SAMPLE_PAGES_DIR = "TRACKY_SAMPLE_PAGES_DIR"

@OptIn(ExperimentalTestApi::class)
class ProjectReportDocumentTest {

    private class CollectingWriter : PdfDocumentWriter {
        val pages = mutableListOf<Image>()

        override fun open(spec: PdfPageSpec) =
            object : PdfDocumentSession {
                override fun addPage(page: CapturedPdfPage) {
                    pages += page.image
                }

                override fun finish() = ByteArray(0)

                override fun cancel() = Unit
            }
    }

    @Test
    fun sampleProjectFillsTheDesignsFivePages() =
        runComposeUiTest {
            val report =
                SampleProjectFixture
                    .load()
                    .toProjectReport(TimeZone.of("Europe/Berlin"), SampleProjectFixture.exportedAt)
                    .toProjectReportUi()
            val writer = CollectingWriter()
            val generator = PdfGenerator(writer)
            lateinit var scope: CoroutineScope
            setContent {
                scope = rememberCoroutineScope()
                PdfGeneratorHost(generator)
            }
            val export =
                scope.async { generator.generate(ProjectReportPageSpec) { projectReportDocument(report) } }
            waitUntil(timeoutMillis = 60_000) { export.isCompleted }
            export.getCompleted()

            System.getenv(SAMPLE_PAGES_DIR)?.let { dir ->
                writer.pages.forEachIndexed { index, page ->
                    val png = checkNotNull(page.encodeToData(EncodedImageFormat.PNG)).bytes
                    File(dir, "page-${index + 1}.png").apply { parentFile.mkdirs() }.writeBytes(png)
                }
            }
            assertThat(writer.pages).hasSize(5)
            // A4 at 300 dpi.
            assertThat(writer.pages.map { it.width to it.height }).each { it.isEqualTo(2479 to 3508) }
        }
}

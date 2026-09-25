package com.jvcs.tracky.features.project.presentation.projectdetail

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import com.jvcs.tracky.core.presentation.pdf.CapturedPdfPage
import com.jvcs.tracky.core.presentation.pdf.PdfDocumentSession
import com.jvcs.tracky.core.presentation.pdf.PdfDocumentWriter
import com.jvcs.tracky.core.presentation.pdf.PdfGenerator
import com.jvcs.tracky.core.presentation.pdf.PdfGeneratorHost
import com.jvcs.tracky.core.presentation.pdf.PdfPageSpec
import com.jvcs.tracky.features.project.domain.export.SampleProjectFixture
import com.jvcs.tracky.features.project.domain.export.toProjectReport
import com.jvcs.tracky.features.project.presentation.export.toProjectReportUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.datetime.TimeZone
import kotlin.test.Test

/** The Root's half of the PDF round trip: what [renderPdf] answers the view model with. */
@OptIn(ExperimentalTestApi::class)
internal class RenderPdfTest {

    private class FakeWriter(private val failOnFinish: Boolean) : PdfDocumentWriter {
        override fun open(spec: PdfPageSpec) =
            object : PdfDocumentSession {
                override fun addPage(page: CapturedPdfPage) = Unit

                override fun finish(): ByteArray = if (failOnFinish) error("disk full") else byteArrayOf(7)

                override fun cancel() = Unit
            }
    }

    private fun ComposeUiTest.render(writer: PdfDocumentWriter): ProjectDetailAction {
        val report =
            SampleProjectFixture
                .load()
                .toProjectReport(TimeZone.UTC, SampleProjectFixture.exportedAt)
                .toProjectReportUi()
        val generator = PdfGenerator(writer)
        lateinit var scope: CoroutineScope
        setContent {
            scope = rememberCoroutineScope()
            PdfGeneratorHost(generator)
        }
        val action = scope.async { renderPdf(generator, report) }
        waitUntil(timeoutMillis = 60_000) { action.isCompleted }
        return action.getCompleted()
    }

    @Test
    fun renderedReportAnswersWithItsBytes() =
        runComposeUiTest {
            assertThat(render(FakeWriter(failOnFinish = false)))
                .isInstanceOf<ProjectDetailAction.OnPdfRendered>()
                .prop(ProjectDetailAction.OnPdfRendered::bytes)
                .transform { it.toList() }
                .isEqualTo(listOf<Byte>(7))
        }

    @Test
    fun writerFailureAnswersWithRenderFailed() =
        runComposeUiTest {
            assertThat(render(FakeWriter(failOnFinish = true))).isEqualTo(ProjectDetailAction.OnPdfRenderFailed)
        }
}

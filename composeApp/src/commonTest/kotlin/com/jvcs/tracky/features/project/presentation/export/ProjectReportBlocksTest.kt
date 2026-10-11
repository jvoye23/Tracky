package com.jvcs.tracky.features.project.presentation.export

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import assertk.assertThat
import assertk.assertions.isNotEmpty
import com.jvcs.tracky.core.presentation.pdf.PdfBlock
import com.jvcs.tracky.core.presentation.pdf.PdfPageInfo
import com.jvcs.tracky.core.presentation.pdf.buildPdfDocument
import com.jvcs.tracky.core.presentation.pdf.sectionsWithHeaders
import com.jvcs.tracky.features.project.domain.export.SampleProjectFixture
import com.jvcs.tracky.features.project.domain.export.toProjectReport
import kotlinx.datetime.TimeZone
import kotlin.test.Test

/**
 * Every block of the sample report composes and carries the sample's data.
 *
 * The page images themselves are checked by the iOS-only ProjectReportDocumentTest, which captures
 * them through the PDF pipeline. This renders the same blocks straight into a column, so it runs on
 * every platform.
 */
@OptIn(ExperimentalTestApi::class)
internal class ProjectReportBlocksTest {

    private val report =
        SampleProjectFixture
            .load()
            .toProjectReport(TimeZone.of("Europe/Berlin"), SampleProjectFixture.exportedAt)
            .toProjectReportUi()

    @Test
    fun theSampleReportComposesWithItsTitleTasksAndTotals() =
        runComposeUiTest {
            val document = buildPdfDocument { projectReportDocument(report) }
            val info = PdfPageInfo(pageNumber = 1, pageCount = 5)
            setContent {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    document.header?.invoke(info)
                    document.sectionsWithHeaders().values.forEach { it.repeatingHeader?.invoke() }
                    document.blocks.filterIsInstance<PdfBlock.Item>().forEach { it.content() }
                    document.footer?.invoke(info)
                }
            }

            listOf("API Testing update", "Testing Tasks Update", "Neuer Task", "08:46:20", "04:21:39")
                .forEach { expected ->
                    assertThat(onAllNodesWithText(expected, substring = true).fetchSemanticsNodes()).isNotEmpty()
                }
        }
}

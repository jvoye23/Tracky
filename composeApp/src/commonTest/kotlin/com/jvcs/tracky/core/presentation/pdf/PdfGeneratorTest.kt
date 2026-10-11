package com.jvcs.tracky.core.presentation.pdf

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
internal class PdfGeneratorTest {

    private class FakeWriter : PdfDocumentWriter {
        var pages = 0
        var cancelled = false
        var onAddPage: () -> Unit = {}

        override fun open(spec: PdfPageSpec) =
            object : PdfDocumentSession {
                override fun addPage(page: CapturedPdfPage) {
                    pages++
                    onAddPage()
                }

                override fun finish() = byteArrayOf(pages.toByte())

                override fun cancel() {
                    cancelled = true
                }
            }
    }

    @Composable
    private fun Block(height: Int) = Spacer(Modifier.height(height.dp))

    // Frames only advance while the test waits, so the export runs in the composition's scope.
    private fun ComposeUiTest.export(writer: FakeWriter, content: PdfDocumentScope.() -> Unit): Deferred<ByteArray> {
        val generator = PdfGenerator(writer)
        lateinit var scope: CoroutineScope
        setContent {
            scope = rememberCoroutineScope()
            PdfGeneratorHost(generator)
        }
        return scope.async { generator.generate(content = content) }
    }

    @Test
    fun blocksAreSpreadOverPagesWithTheirPageInfo() =
        runComposeUiTest {
            val footers = mutableSetOf<PdfPageInfo>()
            // 842 - 2 * 40 margins - 31 header - 31 footer leaves 700pt per page.
            val export =
                export(FakeWriter()) {
                    header { Block(31) }
                    footer { info ->
                        footers += info
                        Block(31)
                    }
                    repeat(3) { item { Block(300) } } // two per page
                    pageBreak()
                    section(repeatingHeader = { Block(100) }) {
                        repeat(3) { item { Block(250) } } // header + two, then header + one
                    }
                }
            waitUntil { export.isCompleted }

            assertThat(export.getCompleted().single().toInt()).isEqualTo(4)
            assertThat(footers.filter { it.pageCount == 4 }.map { it.pageNumber }).containsExactly(1, 2, 3, 4)
        }

    @Test
    fun cancellingTheCallerCancelsTheSession() =
        runComposeUiTest {
            val writer = FakeWriter()
            val export = export(writer) { item { Block(10) } }
            writer.onAddPage = { export.cancel() }

            waitUntil { writer.cancelled }
            assertThat(export.isCancelled).isTrue()
        }

    @Test
    fun generatingWithoutAHostFailsFast() =
        runTest {
            assertFailure { PdfGenerator(FakeWriter()).generate {} }.isInstanceOf<IllegalStateException>()
        }
}

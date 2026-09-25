package com.jvcs.tracky.core.presentation.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Renders [PdfDocumentScope] documents into PDF bytes. Rendering needs a composition, so a
 * [PdfGeneratorHost] for this generator must be composed while [generate] runs.
 */
@Stable
class PdfGenerator internal constructor(
    private val writer: PdfDocumentWriter,
    private val writerDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val requests = Channel<PdfRenderRequest>(Channel.UNLIMITED)
    private val hosts = MutableStateFlow(0)

    /**
     * Renders the document page by page. Cancelling the caller cancels the render.
     *
     * @throws IllegalStateException right away when no [PdfGeneratorHost] is composed, rather
     * than suspending until one might appear.
     */
    suspend fun generate(spec: PdfPageSpec = PdfPageSpec(), content: PdfDocumentScope.() -> Unit): ByteArray {
        check(hosts.value > 0) { "PdfGenerator.generate() needs a composed PdfGeneratorHost(generator)" }
        val request = PdfRenderRequest(buildPdfDocument(content), spec)
        requests.send(request)
        try {
            return request.result.await()
        } finally {
            request.result.cancel()
        }
    }

    /** Renders queued requests one at a time, showing each render step through [show]. */
    internal suspend fun serve(show: (PdfFrame?) -> Unit) {
        hosts.update { it + 1 }
        try {
            for (request in requests) {
                coroutineScope {
                    val render = launch { render(request, show) }
                    request.result.invokeOnCompletion { render.cancel() }
                }
            }
        } finally {
            hosts.update { it - 1 }
        }
    }

    /** Completes the request's result, unless the caller already cancelled it. */
    @Suppress("TooGenericExceptionCaught") // A failing writer fails the caller, not the host.
    private suspend fun render(request: PdfRenderRequest, show: (PdfFrame?) -> Unit) {
        val document = request.document
        var session: PdfDocumentSession? = null
        try {
            val open = withContext(writerDispatcher) { writer.open(request.spec) }.also { session = it }

            val measure = PdfFrame.Measure(document, request.spec).also(show)
            val sizes = measure.result.await()
            val pages =
                PdfPaginator.paginate(
                    contentHeight = sizes.contentHeight,
                    headerHeight = sizes.headerHeight,
                    footerHeight = sizes.footerHeight,
                    blocks = document.measuredBlocks(sizes.blockHeights),
                    sectionHeaderHeights = sizes.sectionHeaderHeights,
                )
            pages.forEachIndexed { index, placements ->
                if (placements.any { it is PdfPlacement.Block && it.overflows }) {
                    Logger.withTag("PdfGenerator").w { "Page ${index + 1} clips a block taller than the page" }
                }

                val page = PdfFrame.Page(document, request.spec, placements, PdfPageInfo(index + 1, pages.size))
                show(page)
                val captured = page.captured.await()
                withContext(writerDispatcher) { open.addPage(captured) }
            }

            request.result.complete(withContext(writerDispatcher) { open.finish() })
            session = null
        } catch (cancellation: CancellationException) {
            // A no-op when the caller cancelled; otherwise the host left the composition.
            val hostLeft = IllegalStateException("PdfGeneratorHost left the composition", cancellation)
            request.result.completeExceptionally(hostLeft)
            throw cancellation
        } catch (failure: Exception) {
            currentCoroutineContext().ensureActive()
            request.result.completeExceptionally(failure)
        } finally {
            show(null)
            session?.cancel()
        }
    }
}

/** A [PdfGenerator] writing through [writer]; platform default writers arrive with their actuals. */
@Composable
fun rememberPdfGenerator(writer: PdfDocumentWriter): PdfGenerator = remember(writer) { PdfGenerator(writer) }

private class PdfRenderRequest(val document: PdfDocument, val spec: PdfPageSpec) {

    val result = CompletableDeferred<ByteArray>()
}

/** What the [PdfGeneratorHost] composes for one step of a render. */
internal sealed interface PdfFrame {

    val document: PdfDocument
    val spec: PdfPageSpec

    class Measure(override val document: PdfDocument, override val spec: PdfPageSpec) : PdfFrame {

        val result = CompletableDeferred<PdfMeasurements>()
    }

    class Page(
        override val document: PdfDocument,
        override val spec: PdfPageSpec,
        val placements: List<PdfPlacement>,
        val info: PdfPageInfo,
    ) : PdfFrame {

        val captured = CompletableDeferred<CapturedPdfPage>()
    }
}

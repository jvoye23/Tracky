package com.jvcs.tracky.core.presentation.pdf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpSize

/**
 * Measures every block of [document] at content width without placing or drawing any, and reports
 * the heights to [onMeasure] for the [PdfPaginator]. Takes no space.
 */
@Composable
internal fun PdfMeasurer(
    document: PdfDocument,
    spec: PdfPageSpec,
    onMeasure: (PdfMeasurements) -> Unit,
) {
    val sections = document.sectionsWithHeaders()
    SubcomposeLayout { _ ->
        // Rounded per edge like Modifier.padding, so heights match the page's content area exactly.
        val margins = spec.margins
        val contentWidth =
            spec.pageSize.width.roundToPx() -
                margins.calculateLeftPadding(layoutDirection).roundToPx() -
                margins.calculateRightPadding(layoutDirection).roundToPx()
        val contentHeight =
            spec.pageSize.height.roundToPx() -
                margins.calculateTopPadding().roundToPx() -
                margins.calculateBottomPadding().roundToPx()
        val constraints = Constraints(maxWidth = contentWidth)

        fun height(slot: Any, content: @Composable () -> Unit) =
            subcompose(slot, content).sumOf { it.measure(constraints).height }

        // The real page numbers are unknown until pagination; they must not change the height.
        val placeholder = PdfPageInfo(pageNumber = 1, pageCount = 1)
        val header = document.header
        val footer = document.footer
        onMeasure(
            PdfMeasurements(
                contentHeight = contentHeight,
                headerHeight = header?.let { height("header") { it(placeholder) } } ?: 0,
                footerHeight = footer?.let { height("footer") { it(placeholder) } } ?: 0,
                blockHeights =
                    document.blocks.mapIndexed { index, block ->
                        if (block is PdfBlock.Item) height(index, block.content) else 0
                    },
                sectionHeaderHeights =
                    sections.mapValues { (id, section) ->
                        height("section" to id, checkNotNull(section.repeatingHeader))
                    },
            ),
        )
        layout(0, 0) {}
    }
}

/**
 * One page of [document]: header, [placements] and footer inside the margins, recorded into a
 * [CapturedPdfPage] for [onCapture] rather than drawn on screen. Page composables own their
 * background; the page itself is transparent.
 */
@Composable
internal fun PdfPage(
    document: PdfDocument,
    spec: PdfPageSpec,
    placements: List<PdfPlacement>,
    info: PdfPageInfo,
    onCapture: (CapturedPdfPage) -> Unit,
) {
    val sections = document.sectionsWithHeaders()
    Column(
        Modifier
            .detachedPage(spec.pageSize)
            .capturePdfPage(onCapture)
            .padding(spec.margins),
    ) {
        document.header?.invoke(info)
        Column(Modifier.weight(1f).clipToBounds()) {
            placements.forEach { placement ->
                when (placement) {
                    is PdfPlacement.SectionHeader -> {
                        sections.getValue(placement.sectionId).repeatingHeader?.invoke()
                    }

                    is PdfPlacement.Block -> {
                        val block = document.blocks[placement.index] as PdfBlock.Item
                        // An oversized block keeps its height and is clipped at the content edge.
                        if (placement.overflows) {
                            Box(Modifier.wrapContentHeight(Alignment.Top, unbounded = true)) { block.content() }
                        } else {
                            block.content()
                        }
                    }
                }
            }
        }
        document.footer?.invoke(info)
    }
}

/**
 * Lays the page out at [size] but reports 0x0 to the parent, so the host takes no space in the
 * screen; the page is still laid out and drawn, which [capturePdfPage] needs to record it.
 */
private fun Modifier.detachedPage(size: DpSize) =
    layout { measurable, _ ->
        val page = measurable.measure(Constraints.fixed(size.width.roundToPx(), size.height.roundToPx()))
        layout(0, 0) { page.place(0, 0) }
    }

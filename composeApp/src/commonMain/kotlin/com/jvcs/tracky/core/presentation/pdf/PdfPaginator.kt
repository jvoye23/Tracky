package com.jvcs.tracky.core.presentation.pdf

/** A [PdfBlock] reduced to its height in points; list indices match [PdfDocument.blocks]. */
internal sealed interface PdfMeasuredBlock {

    data class Item(val height: Int, val sectionId: Int?) : PdfMeasuredBlock

    data object PageBreak : PdfMeasuredBlock
}

internal sealed interface PdfPlacement {

    /** Block [index] of the document; [overflows] = taller than a page, placed alone, to be clipped. */
    data class Block(val index: Int, val overflows: Boolean = false) : PdfPlacement

    /** The repeating header of section [sectionId], at the section start or a continuation. */
    data class SectionHeader(val sectionId: Int) : PdfPlacement
}

/**
 * Packs blocks onto pages greedily, never splitting one; returns each page's placements between
 * page header and footer. Section headers are explicit placements, move with their first block
 * and repeat on continuation pages. An empty document gets one empty page for header and footer.
 */
internal object PdfPaginator {

    fun paginate(
        contentHeight: Int,
        headerHeight: Int,
        footerHeight: Int,
        blocks: List<PdfMeasuredBlock>,
        sectionHeaderHeights: Map<Int, Int>,
    ): List<List<PdfPlacement>> {
        val available = contentHeight - headerHeight - footerHeight
        val pages = mutableListOf(mutableListOf<PdfPlacement>())
        var used = 0
        var previousSectionId: Int? = null

        fun startNewPage() {
            if (pages.last().isEmpty()) return
            pages += mutableListOf<PdfPlacement>()
            used = 0
        }

        blocks.forEachIndexed { index, block ->
            if (block !is PdfMeasuredBlock.Item) return@forEachIndexed startNewPage()
            val sectionHeader =
                block.sectionId?.let { id ->
                    sectionHeaderHeights[id]?.let { height -> PdfPlacement.SectionHeader(id) to height }
                }

            // Drawn where the section opens and atop every page it continues onto.
            fun headerHere() = sectionHeader?.takeIf { block.sectionId != previousSectionId || pages.last().isEmpty() }

            fun fits() = used + block.height + (headerHere()?.second ?: 0) <= available

            if (!fits()) startNewPage()
            val overflows = !fits()

            headerHere()?.let { (placement, height) -> pages.last() += placement.also { used += height } }
            pages.last() += PdfPlacement.Block(index, overflows)
            used += block.height
            previousSectionId = block.sectionId
        }
        // Only the last page can be empty: after a trailing break, or for an empty document.
        return pages.filterIndexed { page, placements -> page == 0 || placements.isNotEmpty() }
    }
}

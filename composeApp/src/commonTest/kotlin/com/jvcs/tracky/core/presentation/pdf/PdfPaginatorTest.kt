package com.jvcs.tracky.core.presentation.pdf

import assertk.assertThat
import assertk.assertions.containsExactly
import com.jvcs.tracky.core.presentation.pdf.PdfMeasuredBlock.PageBreak
import com.jvcs.tracky.core.presentation.pdf.PdfPlacement.Block
import com.jvcs.tracky.core.presentation.pdf.PdfPlacement.SectionHeader
import kotlin.test.Test

/** Each rule guards against a lost row, a blank page or a table without its column headers. */
class PdfPaginatorTest {

    private fun item(height: Int, sectionId: Int? = null) = PdfMeasuredBlock.Item(height, sectionId)

    private fun paginate(
        vararg blocks: PdfMeasuredBlock,
        sectionHeaderHeights: Map<Int, Int> = emptyMap(),
        headerAndFooterHeight: Int = 0,
    ) = PdfPaginator.paginate(
        contentHeight = 100,
        headerHeight = headerAndFooterHeight,
        footerHeight = headerAndFooterHeight,
        blocks = blocks.toList(),
        sectionHeaderHeights = sectionHeaderHeights,
    )

    @Test
    fun emptyDocument_isOneEmptyPage_soHeaderAndFooterStillRender() {
        assertThat(paginate()).containsExactly(emptyList<PdfPlacement>())
    }

    @Test
    fun blocks_fillAPageExactly_andTheNextOneMovesWholeToANewPage() {
        assertThat(paginate(item(40), item(60), item(1)))
            .containsExactly(listOf(Block(0), Block(1)), listOf(Block(2)))
    }

    @Test
    fun pageHeaderAndFooter_shrinkTheSpaceForBlocks() {
        assertThat(paginate(item(40), item(40), headerAndFooterHeight = 11))
            .containsExactly(listOf(Block(0)), listOf(Block(1)))
    }

    @Test
    fun pageBreak_startsANewPage_withoutBlankPages() {
        // Leading, doubled and trailing breaks all land on an already empty page.
        assertThat(paginate(PageBreak, item(10), PageBreak, PageBreak, item(10), PageBreak))
            .containsExactly(listOf(Block(1)), listOf(Block(4)))
    }

    @Test
    fun sectionHeader_opensTheSection_andRepeatsOnEveryContinuationPage() {
        val blocks = arrayOf(item(10), item(40, sectionId = 1), item(40, sectionId = 1), item(40, sectionId = 1))

        assertThat(paginate(*blocks, sectionHeaderHeights = mapOf(1 to 10))).containsExactly(
            listOf(Block(0), SectionHeader(1), Block(1), Block(2)),
            listOf(SectionHeader(1), Block(3)),
        )
    }

    @Test
    fun sectionHeader_repeatsAfterAPageBreakInsideTheSection() {
        assertThat(paginate(item(10, 1), PageBreak, item(10, 1), sectionHeaderHeights = mapOf(1 to 5)))
            .containsExactly(listOf(SectionHeader(1), Block(0)), listOf(SectionHeader(1), Block(2)))
    }

    @Test
    fun sectionHeader_isNeverOrphanedAtThePageBottom() {
        // The header (10) would fit under the first block, but not together with its first row (30).
        assertThat(paginate(item(70), item(30, sectionId = 1), sectionHeaderHeights = mapOf(1 to 10)))
            .containsExactly(listOf(Block(0)), listOf(SectionHeader(1), Block(1)))
    }

    @Test
    fun consecutiveSections_eachOpenWithTheirOwnHeader_ifTheyHaveOne() {
        assertThat(paginate(item(10, 1), item(10, 2), item(10, 3), sectionHeaderHeights = mapOf(1 to 5, 2 to 5)))
            .containsExactly(listOf(SectionHeader(1), Block(0), SectionHeader(2), Block(1), Block(2)))
    }

    @Test
    fun oversizedBlock_goesAloneOnItsOwnPage_andIsFlagged() {
        assertThat(paginate(item(10), item(150), item(10)))
            .containsExactly(listOf(Block(0)), listOf(Block(1, overflows = true)), listOf(Block(2)))
    }
}

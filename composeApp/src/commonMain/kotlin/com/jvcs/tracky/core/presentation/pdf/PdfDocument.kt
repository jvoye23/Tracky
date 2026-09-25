package com.jvcs.tracky.core.presentation.pdf

import androidx.compose.runtime.Composable

/** What a [PdfDocumentScope] DSL describes, flattened into the order blocks are printed in. */
internal data class PdfDocument(
    val header: (@Composable (PdfPageInfo) -> Unit)?,
    val footer: (@Composable (PdfPageInfo) -> Unit)?,
    val blocks: List<PdfBlock>,
)

/** A [PdfDocumentScope.section]; [id] is unique within its document. */
internal class PdfSection(val id: Int, val repeatingHeader: (@Composable () -> Unit)?)

internal sealed interface PdfBlock {

    class Item(
        val key: Any?,
        val section: PdfSection?,
        val content: @Composable () -> Unit,
    ) : PdfBlock

    data object PageBreak : PdfBlock
}

internal fun buildPdfDocument(content: PdfDocumentScope.() -> Unit): PdfDocument =
    PdfDocumentBuilder().apply(content).build()

private class PdfDocumentBuilder : PdfDocumentScope {

    private var header: (@Composable (PdfPageInfo) -> Unit)? = null
    private var footer: (@Composable (PdfPageInfo) -> Unit)? = null
    private val blocks = mutableListOf<PdfBlock>()
    private val keys = mutableSetOf<Any>()
    private var currentSection: PdfSection? = null
    private var sectionCount = 0

    override fun header(content: @Composable (PdfPageInfo) -> Unit) {
        header = content
    }

    override fun footer(content: @Composable (PdfPageInfo) -> Unit) {
        footer = content
    }

    override fun item(key: Any?, content: @Composable () -> Unit) {
        // The host composes blocks under their key, and Compose cannot tell two equal keys apart.
        require(key == null || keys.add(key)) { "Duplicate PDF item key: $key" }
        blocks += PdfBlock.Item(key = key, section = currentSection, content = content)
    }

    override fun <T> items(
        items: List<T>,
        key: ((T) -> Any)?,
        content: @Composable (T) -> Unit,
    ) {
        items.forEach { element -> item(key = key?.invoke(element)) { content(element) } }
    }

    override fun section(repeatingHeader: (@Composable () -> Unit)?, content: PdfDocumentScope.() -> Unit) {
        // Nested sections would need a stack of repeating headers; no report needs that yet.
        require(currentSection == null) { "PDF sections cannot be nested" }
        currentSection = PdfSection(id = sectionCount++, repeatingHeader = repeatingHeader)
        content()
        currentSection = null
    }

    override fun pageBreak() {
        blocks += PdfBlock.PageBreak
    }

    fun build() = PdfDocument(header = header, footer = footer, blocks = blocks.toList())
}

/** Pairs [blocks][PdfDocument.blocks] with their measured [heights] for the [PdfPaginator]. */
internal fun PdfDocument.measuredBlocks(heights: List<Int>): List<PdfMeasuredBlock> =
    blocks.mapIndexed { index, block ->
        when (block) {
            is PdfBlock.Item -> PdfMeasuredBlock.Item(heights[index], block.section?.id)
            PdfBlock.PageBreak -> PdfMeasuredBlock.PageBreak
        }
    }

/** The document's sections that have a repeating header, by id. */
internal fun PdfDocument.sectionsWithHeaders(): Map<Int, PdfSection> =
    blocks
        .mapNotNull { (it as? PdfBlock.Item)?.section }
        .filter { it.repeatingHeader != null }
        .associateBy { it.id }

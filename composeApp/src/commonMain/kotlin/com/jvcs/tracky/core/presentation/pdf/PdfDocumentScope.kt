package com.jvcs.tracky.core.presentation.pdf

import androidx.compose.runtime.Composable

/**
 * Describes a PDF like a `LazyColumn` describes a list: blocks packed onto pages top to bottom.
 * A block is never split across pages, so keep each one small — a table row, not the table.
 */
interface PdfDocumentScope {

    /** Drawn at the top of every page; a second call replaces the first. */
    fun header(content: @Composable (PdfPageInfo) -> Unit)

    /** Drawn at the bottom of every page; a second call replaces the first. */
    fun footer(content: @Composable (PdfPageInfo) -> Unit)

    /** Adds one block. A non-null [key] must be unique within the document. */
    fun item(key: Any? = null, content: @Composable () -> Unit)

    /** Adds one block per element of [items], keyed by [key] when given. */
    fun <T> items(
        items: List<T>,
        key: ((T) -> Any)? = null,
        content: @Composable (T) -> Unit,
    )

    /**
     * Groups blocks, e.g. the rows of a table. [repeatingHeader] is drawn above the first block
     * and again at the top of every page the section continues onto, and it is never left alone
     * at the bottom of a page. Sections cannot be nested.
     */
    fun section(repeatingHeader: (@Composable () -> Unit)? = null, content: PdfDocumentScope.() -> Unit)

    /** Starts a new page, unless the current one is still empty. */
    fun pageBreak()
}

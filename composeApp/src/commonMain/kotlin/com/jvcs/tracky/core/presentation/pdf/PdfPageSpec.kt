package com.jvcs.tracky.core.presentation.pdf

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

enum class PageOrientation { Portrait, Landscape }

/** Passed to the page header and footer so they can print "Page 2 of 5". */
data class PdfPageInfo(val pageNumber: Int, val pageCount: Int)

/**
 * The paper every page of a generated PDF is laid out on: DIN A4 in the given [orientation].
 *
 * Page composables are laid out at a density where 1.dp is one PDF point (1/72 inch), so
 * sizes and [margins] here are points on paper. [background] fills the whole sheet, margins
 * included, beneath everything drawn on it.
 */
data class PdfPageSpec(
    val orientation: PageOrientation = PageOrientation.Portrait,
    val margins: PaddingValues = PaddingValues(40.dp),
    val background: Color = Color.White,
) {

    /** The whole sheet, with width and height swapped for [PageOrientation.Landscape]. */
    val pageSize: DpSize
        get() = if (orientation == PageOrientation.Portrait) DpSize(A4Short, A4Long) else DpSize(A4Long, A4Short)

    /** The sheet minus [margins]. Start + end is the same in either layout direction. */
    val contentSize: DpSize
        get() {
            val horizontal =
                margins.calculateLeftPadding(LayoutDirection.Ltr) +
                    margins.calculateRightPadding(LayoutDirection.Ltr)
            val vertical = margins.calculateTopPadding() + margins.calculateBottomPadding()
            return DpSize(pageSize.width - horizontal, pageSize.height - vertical)
        }
}

private val A4Short = 595.dp
private val A4Long = 842.dp

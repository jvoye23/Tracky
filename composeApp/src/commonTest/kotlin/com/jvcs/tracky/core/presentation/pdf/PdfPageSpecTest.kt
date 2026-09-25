package com.jvcs.tracky.core.presentation.pdf

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class PdfPageSpecTest {

    @Test
    fun portrait_isDinA4InPoints_minusTheDefaultMargins() {
        assertThat(PdfPageSpec().pageSize).isEqualTo(DpSize(595.dp, 842.dp))
        assertThat(PdfPageSpec().contentSize).isEqualTo(DpSize(515.dp, 762.dp))
    }

    @Test
    fun landscape_swapsWidthAndHeight_beforeSubtractingEachMargin() {
        val margins = PaddingValues(start = 10.dp, top = 20.dp, end = 30.dp, bottom = 40.dp)
        val spec = PdfPageSpec(orientation = PageOrientation.Landscape, margins = margins)

        assertThat(spec.pageSize).isEqualTo(DpSize(842.dp, 595.dp))
        assertThat(spec.contentSize).isEqualTo(DpSize(802.dp, 535.dp))
    }
}

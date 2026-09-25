package com.jvcs.tracky.core.presentation.pdf

import androidx.compose.runtime.Composable
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import kotlin.test.Test

class PdfDocumentBuilderTest {

    @Test
    fun headerAndFooter_areKept() {
        val header: @Composable (PdfPageInfo) -> Unit = {}
        val footer: @Composable (PdfPageInfo) -> Unit = {}

        val document =
            buildPdfDocument {
                header(header)
                footer(footer)
            }

        assertThat(document.header).isSameInstanceAs(header)
        assertThat(document.footer).isSameInstanceAs(footer)
        assertThat(buildPdfDocument {}.header).isNull()
    }

    @Test
    fun blocks_keepTheirDeclarationOrder_andItemsTheirKeys() {
        val document =
            buildPdfDocument {
                item(key = "title") {}
                pageBreak()
                items(listOf(1, 2), key = { "row-$it" }) {}
                items(listOf(3)) {}
            }

        assertThat(document.blocks.map { if (it is PdfBlock.Item) it.key else "break" })
            .containsExactly("title", "break", "row-1", "row-2", null)
    }

    @Test
    fun sectionItems_shareTheirSection_andEachSectionGetsItsOwnId() {
        val tableHeader: @Composable () -> Unit = {}

        val document =
            buildPdfDocument {
                item {}
                section(repeatingHeader = tableHeader) { items(listOf(1, 2)) {} }
                section { item {} }
            }

        val (before, first, second, other) = document.blocks.filterIsInstance<PdfBlock.Item>()
        assertThat(before.section).isNull()
        assertThat(first.section?.repeatingHeader).isSameInstanceAs(tableHeader)
        assertThat(second.section).isSameInstanceAs(first.section)
        assertThat(listOf(first.section?.id, other.section?.id)).containsExactly(0, 1)
    }

    @Test
    fun nestedSectionsAndDuplicateKeys_areRejected() {
        fun rejects(dsl: PdfDocumentScope.() -> Unit) =
            assertFailure { buildPdfDocument(dsl) }.isInstanceOf<IllegalArgumentException>()

        rejects { section { section {} } }
        rejects { items(listOf(1, 1), key = { it }) {} }
    }
}

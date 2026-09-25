package com.jvcs.tracky.features.project.domain.export

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import kotlin.test.Test

class ExportFileTest {

    private fun file(vararg bytes: Byte) = ExportFile("a.json", "application/json", bytes)

    @Test
    fun comparesBytesByContent() {
        assertThat(file(1, 2)).isEqualTo(file(1, 2))
        assertThat(file(1, 2).hashCode()).isEqualTo(file(1, 2).hashCode())
        assertThat(file(1, 2)).isNotEqualTo(file(2, 1))
    }
}

package com.jvcs.tracky.features.project.data.export

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.export.ExportError
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class WriteExportTest {

    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("tracky-export-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `writes the bytes to the chosen file`() =
        runTest {
            val target = File(dir, "export.json")

            val result = writeExport(target, "{}".encodeToByteArray(), StandardTestDispatcher(testScheduler))

            assertThat(result).isEqualTo(Result.Success(Unit))
            assertThat(target.readText()).isEqualTo("{}")
        }

    @Test
    fun `an unwritable target is a write failure`() =
        runTest {
            val result = writeExport(dir, byteArrayOf(1), StandardTestDispatcher(testScheduler))

            assertThat(result).isEqualTo(Result.Error(ExportError.WRITE_FAILED))
        }
}

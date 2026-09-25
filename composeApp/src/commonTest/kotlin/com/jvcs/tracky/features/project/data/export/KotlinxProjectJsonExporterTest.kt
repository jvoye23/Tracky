package com.jvcs.tracky.features.project.data.export

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.data.export.dto.ProjectExportDto
import com.jvcs.tracky.features.project.domain.export.ExportFile
import com.jvcs.tracky.features.project.domain.models.Project
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.time.Instant

class KotlinxProjectJsonExporterTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val berlin = TimeZone.of("Europe/Berlin")

    // 23:30 UTC is already the next day in Berlin, which the file name has to follow.
    private val exportedAt = Instant.parse("2026-09-24T23:30:00Z")

    private fun project(title: String = "Client work") =
        Project(
            projectId = "p1",
            title = title,
            description = null,
            colorArgb = null,
            totalDurationMillis = null,
            startDateTimeUtc = Instant.parse("2026-09-01T08:00:00Z"),
            isFinished = false,
            endDateTimeUtc = null,
            projectTasks = emptyList(),
        )

    private suspend fun TestScope.exportOf(project: Project): ExportFile {
        val result =
            KotlinxProjectJsonExporter(json, StandardTestDispatcher(testScheduler))
                .export(project, exportedAt, berlin)
        assertThat(result).isInstanceOf<Result.Success<ExportFile>>()
        return (result as Result.Success).data
    }

    @Test
    fun producesPrettyJsonThatRoundTripsIntoTheDto() =
        runTest {
            val file = exportOf(project())
            val text = file.bytes.decodeToString()

            assertThat(file.mimeType).isEqualTo("application/json")
            assertThat(text).contains("\n    \"project\": {")
            assertThat(json.decodeFromString<ProjectExportDto>(text))
                .isEqualTo(project().toProjectExportDto(exportedAt, berlin))
        }

    @Test
    fun fileNameIsTheSanitizedTitleAndTheLocalExportDate() =
        runTest {
            assertThat(exportOf(project()).fileName).isEqualTo("Client_work_2026-09-25.json")
            assertThat(exportOf(project(" Q3: a/b \\ \"c\"? ")).fileName).isEqualTo("Q3_a_b_c_2026-09-25.json")
            assertThat(exportOf(project("Über-Größe_1")).fileName).isEqualTo("Über-Größe_1_2026-09-25.json")
            assertThat(exportOf(project("/// ")).fileName).isEqualTo("project_2026-09-25.json")
            assertThat(exportOf(project("x".repeat(200))).fileName).isEqualTo("x".repeat(60) + "_2026-09-25.json")
        }
}

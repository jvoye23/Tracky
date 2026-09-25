package com.jvcs.tracky.features.project.domain.export

import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.models.Project
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * Records every project it is asked to export and answers with [result] - a small JSON file named
 * after the project unless a test sets an [ExportError].
 */
class FakeProjectJsonExporter(var result: Result<ExportFile, ExportError>? = null) : ProjectJsonExporter {

    val exportedProjects = mutableListOf<Project>()

    override suspend fun export(
        project: Project,
        exportedAt: Instant,
        timeZone: TimeZone,
    ): Result<ExportFile, ExportError> {
        exportedProjects += project
        return result
            ?: Result.Success(ExportFile("${project.title}.json", "application/json", "{}".encodeToByteArray()))
    }
}

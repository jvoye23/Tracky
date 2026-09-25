package com.jvcs.tracky.features.project.data.export

import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.data.export.dto.ProjectExportDto
import com.jvcs.tracky.features.project.domain.export.ExportError
import com.jvcs.tracky.features.project.domain.export.ExportFile
import com.jvcs.tracky.features.project.domain.export.ProjectJsonExporter
import com.jvcs.tracky.features.project.domain.export.exportFileName
import com.jvcs.tracky.features.project.domain.models.Project
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Instant

private const val JSON_MIME_TYPE = "application/json"

/** Encodes a project tree with kotlinx.serialization, pretty-printed so the file reads well by hand. */
class KotlinxProjectJsonExporter(json: Json, private val dispatcher: CoroutineDispatcher) : ProjectJsonExporter {

    private val prettyJson = Json(from = json) { prettyPrint = true }

    override suspend fun export(
        project: Project,
        exportedAt: Instant,
        timeZone: TimeZone,
    ): Result<ExportFile, ExportError> =
        withContext(dispatcher) {
            val text =
                try {
                    prettyJson.encodeToString(
                        ProjectExportDto.serializer(),
                        project.toProjectExportDto(exportedAt, timeZone),
                    )
                } catch (_: SerializationException) {
                    return@withContext Result.Error(ExportError.SERIALIZATION)
                }
            Result.Success(
                ExportFile(
                    fileName = exportFileName(project.title, exportedAt, timeZone, extension = "json"),
                    mimeType = JSON_MIME_TYPE,
                    bytes = text.encodeToByteArray(),
                ),
            )
        }
}

package com.jvcs.tracky.features.project.data.export

import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.data.export.dto.ProjectExportDto
import com.jvcs.tracky.features.project.domain.export.ExportError
import com.jvcs.tracky.features.project.domain.export.ExportFile
import com.jvcs.tracky.features.project.domain.export.ProjectJsonExporter
import com.jvcs.tracky.features.project.domain.models.Project
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Instant

private const val JSON_MIME_TYPE = "application/json"
private const val MAX_TITLE_LENGTH = 60
private const val FALLBACK_TITLE = "project"

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
            val date = exportedAt.toLocalDateTime(timeZone).date
            Result.Success(
                ExportFile(
                    fileName = "${project.title.toFileNameStem()}_$date.json",
                    mimeType = JSON_MIME_TYPE,
                    bytes = text.encodeToByteArray(),
                ),
            )
        }
}

/**
 * Anything but letters, digits, '-' and '_' — which covers every character a file system or a share
 * target might reject — collapses into a single '_'. A loop rather than a regex: Kotlin/Native's
 * engine handles '-' inside a character class differently from the JVM's.
 */
private fun String.toFileNameStem(): String =
    buildString {
        for (char in this@toFileNameStem) {
            when {
                char.isLetterOrDigit() || char == '-' || char == '_' -> append(char)
                !endsWith('_') -> append('_')
            }
        }
    }.trim('_')
        .take(MAX_TITLE_LENGTH)
        .ifEmpty { FALLBACK_TITLE }

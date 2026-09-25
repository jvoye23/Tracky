package com.jvcs.tracky.features.project.domain.export

import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.models.Project
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * Turns one project tree into a user-facing JSON document — what the user tracked, not how the app
 * stores or syncs it.
 *
 * Every instant in the document is ISO-8601 UTC; [timeZone] is recorded alongside so a reader can
 * convert back to the wall-clock times the user saw, and it dates the file name.
 */
interface ProjectJsonExporter {

    suspend fun export(
        project: Project,
        exportedAt: Instant,
        timeZone: TimeZone,
    ): Result<ExportFile, ExportError>
}

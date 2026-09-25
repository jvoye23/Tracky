package com.jvcs.tracky.features.project.presentation.projectdetail

import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.core.domain.util.onFailure
import com.jvcs.tracky.core.domain.util.onSuccess
import com.jvcs.tracky.features.project.domain.export.ExportError
import com.jvcs.tracky.features.project.domain.export.ExportFile
import com.jvcs.tracky.features.project.domain.export.ExportFileSharer
import com.jvcs.tracky.features.project.domain.export.ProjectJsonExporter
import com.jvcs.tracky.features.project.domain.project.ProjectRepository
import com.jvcs.tracky.features.project.presentation.util.toUiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/**
 * The export menu of the project detail screen: loads the full task tree, turns it into a JSON file
 * and hands the file to the platform share sheet.
 *
 * Split out of [ProjectDetailViewModel], which owns the [state] this patches, the [scope] it runs in
 * and the [events] it reports to.
 */
internal class ProjectDetailExport(
    private val projectId: String?,
    private val state: MutableStateFlow<ProjectDetailState>,
    private val scope: CoroutineScope,
    private val events: SendChannel<ProjectDetailEvent>,
    private val projectRepository: ProjectRepository,
    private val projectJsonExporter: ProjectJsonExporter,
    private val exportFileSharer: ExportFileSharer,
    private val timeProvider: TimeProvider,
) {

    fun onExportMenuClick() = state.update { it.copy(isExportMenuExpanded = !it.isExportMenuExpanded) }

    fun onExportMenuDismiss() = state.update { it.copy(isExportMenuExpanded = false) }

    fun onExportFormatClick(format: ExportFormat) {
        val alreadyExporting = state.value.isExporting
        state.update { it.copy(isExportMenuExpanded = false, isExporting = true) }
        if (alreadyExporting) return

        scope.launch {
            val project = projectId?.let { projectRepository.observeProjectWithTaskTreeById(it).first() }
            if (project == null) {
                fail(ExportError.PROJECT_NOT_FOUND)
                return@launch
            }
            val now = timeProvider.nowInstant
            val timeZone = TimeZone.currentSystemDefault()
            when (format) {
                ExportFormat.Json -> {
                    projectJsonExporter
                        .export(project, exportedAt = now, timeZone = timeZone)
                        .onSuccess { share(it) }
                        .onFailure { fail(it) }
                }
            }
        }
    }

    private suspend fun share(file: ExportFile) {
        exportFileSharer
            .share(file)
            .onSuccess { state.update { current -> current.copy(isExporting = false) } }
            .onFailure { fail(it) }
    }

    private suspend fun fail(error: ExportError) {
        state.update { it.copy(isExporting = false) }
        events.send(ProjectDetailEvent.Error(error.toUiText()))
    }
}

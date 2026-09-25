package com.jvcs.tracky.features.project.presentation.projectdetail

import com.jvcs.tracky.designsystem.util.UiText
import com.jvcs.tracky.features.project.presentation.export.ProjectReportUi

sealed interface ProjectDetailEvent {

    data class NewProjectSessionSaved(val projectSessionTitle: String) : ProjectDetailEvent

    data class Error(val error: UiText) : ProjectDetailEvent

    /** A reorder that could not be saved; the list has already been rolled back. */
    data class ReorderError(val error: UiText) : ProjectDetailEvent

    /**
     * Draw [report] as a PDF and answer with [ProjectDetailAction.OnPdfRendered] or
     * [ProjectDetailAction.OnPdfRenderFailed]. Rendering needs Compose, so the Root does it.
     */
    data class RenderPdf(val report: ProjectReportUi) : ProjectDetailEvent
}

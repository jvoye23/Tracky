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

    /** A Pro user asked to add a subtask to [taskId]: open the editor for it. */
    data class NavigateToNewSubTask(val taskId: String) : ProjectDetailEvent

    /** A free user reached for a Pro feature: open the paywall. */
    data object NavigateToPaywall : ProjectDetailEvent
}

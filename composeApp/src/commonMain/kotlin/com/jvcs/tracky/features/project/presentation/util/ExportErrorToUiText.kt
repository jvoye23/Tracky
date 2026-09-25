package com.jvcs.tracky.features.project.presentation.util

import com.jvcs.tracky.designsystem.util.UiText
import com.jvcs.tracky.features.project.domain.export.ExportError
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.error_export_project_not_found
import tracky.composeapp.generated.resources.error_export_render_failed
import tracky.composeapp.generated.resources.error_export_serialization
import tracky.composeapp.generated.resources.error_export_share_failed
import tracky.composeapp.generated.resources.error_export_write_failed

fun ExportError.toUiText(): UiText =
    UiText.Resource(
        when (this) {
            ExportError.PROJECT_NOT_FOUND -> Res.string.error_export_project_not_found
            ExportError.SERIALIZATION -> Res.string.error_export_serialization
            ExportError.WRITE_FAILED -> Res.string.error_export_write_failed
            ExportError.RENDER_FAILED -> Res.string.error_export_render_failed
            ExportError.SHARE_FAILED -> Res.string.error_export_share_failed
        },
    )

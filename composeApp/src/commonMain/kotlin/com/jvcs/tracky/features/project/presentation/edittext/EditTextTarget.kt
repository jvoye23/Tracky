package com.jvcs.tracky.features.project.presentation.edittext

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the edit-text screen is editing. Part of the saved back stack via EditTextNavKey, so the
 * entry names are persisted: never rename one.
 */
@Serializable
@SerialName("edit_text_target")
enum class EditTextTarget {
    PROJECT,
    TASK,
    SUBTASK,

    /** A subtask that does not exist yet; the first successful save creates it. */
    NEW_SUBTASK,
}

package com.jvcs.tracky.features.project_tracker.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Carried as an argument of the ProjectDetail and EditTextNavKey routes, so the entry names
// end up in the saved back stack. @SerialName pins them against a future rename; it is only
// honoured because the enum itself is @Serializable.

@Serializable
enum class EditTextType(val key: String) {
    @SerialName("key_title")
    TITLE("key_title"),

    @SerialName("key_description")
    DESCRIPTION("key_description")
}

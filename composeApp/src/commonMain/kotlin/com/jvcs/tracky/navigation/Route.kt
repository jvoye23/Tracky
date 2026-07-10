package com.jvcs.tracky.navigation

import androidx.navigation3.runtime.NavKey
import com.jvcs.tracky.features.project_tracker.domain.EditTextType
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey {

    @Serializable
    sealed interface AuthRoute : Route {

        @Serializable
        data object Login : AuthRoute, NavKey

        @Serializable
        data object Register : AuthRoute, NavKey

        @Serializable
        data class RegisterSuccess(val email: String) : AuthRoute, NavKey

        @Serializable
        data class EmailVerification(val token: String) : AuthRoute, NavKey

        @Serializable
        data object ForgotPassword : AuthRoute, NavKey

        @Serializable
        data class ResetPassword(val token: String) : AuthRoute, NavKey
    }

    @Serializable
    data object ProjectRoute : Route, NavKey {

        @Serializable
        data object ProjectOverview : Route, NavKey

        @Serializable
        data object ProjectArchive : Route, NavKey

        @Serializable
        data class ProjectArchiveDetail(val projectId: String) : Route, NavKey

        @Serializable
        data object ProjectTrash : Route, NavKey

        @Serializable
        data class ProjectDetail(
            val isEditMode: Boolean,
            val projectId: String? = null,
            val editedText: String? = null,
            val editedTextType: EditTextType? = null
        ) : Route, NavKey

        @Serializable
        data class EditTextNavKey(
            val editText: String?,
            val editTextType: EditTextType
        ) : Route, NavKey

        @Serializable
        data class TaskDetail(
            val taskId: String
        ) : Route, NavKey
    }
}

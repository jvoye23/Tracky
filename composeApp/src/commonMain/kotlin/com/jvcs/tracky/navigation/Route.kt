package com.jvcs.tracky.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Every route carries an explicit @SerialName. That string is the polymorphic discriminator
// written into the saved back stack, and it defaults to the fully qualified class name -
// so without it, renaming a route or moving this file to another package would make a
// backgrounded app fail to restore its destination. Never change an existing name.

@Serializable
@SerialName("route")
sealed interface Route : NavKey {

    @Serializable
    @SerialName("auth_route")
    sealed interface AuthRoute : Route {

        @Serializable
        @SerialName("login")
        data object Login : AuthRoute, NavKey

        @Serializable
        @SerialName("register")
        data object Register : AuthRoute, NavKey

        @Serializable
        @SerialName("register_success")
        data class RegisterSuccess(val email: String) : AuthRoute, NavKey

        @Serializable
        @SerialName("email_verification")
        data class EmailVerification(val token: String) : AuthRoute, NavKey

        @Serializable
        @SerialName("forgot_password")
        data object ForgotPassword : AuthRoute, NavKey

        @Serializable
        @SerialName("reset_password")
        data class ResetPassword(val token: String) : AuthRoute, NavKey
    }

    @Serializable
    @SerialName("project_route")
    data object ProjectRoute : Route, NavKey {

        @Serializable
        @SerialName("project_overview")
        data object ProjectOverview : Route, NavKey

        @Serializable
        @SerialName("project_archive")
        data object ProjectArchive : Route, NavKey

        @Serializable
        @SerialName("project_archive_detail")
        data class ProjectArchiveDetail(val projectId: String) : Route, NavKey

        @Serializable
        @SerialName("project_trash")
        data object ProjectTrash : Route, NavKey

        @Serializable
        @SerialName("project_detail")
        data class ProjectDetail(
            val isEditMode: Boolean,
            val projectId: String? = null
        ) : Route, NavKey

        @Serializable
        @SerialName("edit_text")
        data class EditTextNavKey(
            val isEditMode: Boolean,
            val projectId: String? = null
        ) : Route, NavKey

        @Serializable
        @SerialName("task_detail")
        data class TaskDetail(
            val taskId: String
        ) : Route, NavKey
    }
}

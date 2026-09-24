package com.jvcs.tracky.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import com.jvcs.tracky.features.project.presentation.edittext.EditTextTarget
import kotlinx.serialization.PolymorphicSerializer
import kotlin.test.Test

/**
 * These names are the polymorphic discriminators written into the saved back stack. Changing one
 * means a backgrounded app can no longer restore that destination, so the expected strings are
 * spelled out here literally: this test is meant to fail when a route is renamed or moved.
 */
class RouteSerialNameTest {

    private val expectedSerialNames =
        mapOf(
            "login" to
                Route.AuthRoute.Login
                    .serializer()
                    .descriptor.serialName,
            "register" to
                Route.AuthRoute.Register
                    .serializer()
                    .descriptor.serialName,
            "register_success" to
                Route.AuthRoute.RegisterSuccess
                    .serializer()
                    .descriptor.serialName,
            "email_verification" to
                Route.AuthRoute.EmailVerification
                    .serializer()
                    .descriptor.serialName,
            "forgot_password" to
                Route.AuthRoute.ForgotPassword
                    .serializer()
                    .descriptor.serialName,
            "reset_password" to
                Route.AuthRoute.ResetPassword
                    .serializer()
                    .descriptor.serialName,
            "project_overview" to
                Route.ProjectRoute.ProjectOverview
                    .serializer()
                    .descriptor.serialName,
            "project_archive" to
                Route.ProjectRoute.ProjectArchive
                    .serializer()
                    .descriptor.serialName,
            "project_archive_detail" to
                Route.ProjectRoute.ProjectArchiveDetail
                    .serializer()
                    .descriptor.serialName,
            "project_trash" to
                Route.ProjectRoute.ProjectTrash
                    .serializer()
                    .descriptor.serialName,
            "project_detail" to
                Route.ProjectRoute.ProjectDetail
                    .serializer()
                    .descriptor.serialName,
            "edit_text" to
                Route.ProjectRoute.EditTextNavKey
                    .serializer()
                    .descriptor.serialName,
            "task_detail" to
                Route.ProjectRoute.TaskDetail
                    .serializer()
                    .descriptor.serialName,
            "daily_overview" to
                Route.ProjectRoute.DailyOverview
                    .serializer()
                    .descriptor.serialName,
        )

    @Test
    fun `route serial names are stable`() {
        expectedSerialNames.forEach { (expected, actual) ->
            assertThat(actual).isEqualTo(expected)
        }
    }

    @Test
    fun `every route is covered by this test`() {
        assertThat(expectedSerialNames.size).isEqualTo(14)
    }

    @Test
    fun `route serial names are unique`() {
        val actualNames = expectedSerialNames.values
        assertThat(actualNames.toSet().size).isEqualTo(actualNames.size)
    }

    @Test
    fun `no route serial name falls back to the fully qualified class name`() {
        expectedSerialNames.values.forEach { name ->
            assertThat(
                name.contains('.'),
                name = "Serial name '$name' looks package-derived - it is missing an explicit @SerialName",
            ).isFalse()
        }
    }

    @Test
    fun `routes survive a saved state round trip through the polymorphic module`() {
        val routes: List<NavKey> =
            listOf(
                Route.AuthRoute.Login,
                Route.AuthRoute.Register,
                Route.AuthRoute.RegisterSuccess(email = "a@b.com"),
                Route.AuthRoute.EmailVerification(token = "token"),
                Route.AuthRoute.ForgotPassword,
                Route.AuthRoute.ResetPassword(token = "token"),
                Route.ProjectRoute.ProjectOverview,
                Route.ProjectRoute.ProjectArchive,
                Route.ProjectRoute.ProjectArchiveDetail(projectId = "id"),
                Route.ProjectRoute.ProjectTrash,
                Route.ProjectRoute.ProjectDetail(
                    isEditMode = true,
                    projectId = "id",
                ),
                Route.ProjectRoute.EditTextNavKey(
                    isEditMode = true,
                    projectId = "id",
                ),
                Route.ProjectRoute.EditTextNavKey(
                    isEditMode = true,
                    projectId = "id",
                    target = EditTextTarget.SUBTASK,
                    taskId = "task",
                    subTaskId = "sub",
                ),
                Route.ProjectRoute.TaskDetail(taskId = "id"),
                Route.ProjectRoute.DailyOverview(projectId = "id", preselectedDateEpochDay = 20_700L),
            )

        // One extra entry: the edit-text route is round-tripped once per shape it is opened with.
        assertThat(routes.size).isEqualTo(expectedSerialNames.size + 1)

        routes.forEach { route ->
            val encoded =
                encodeToSavedState(
                    serializer = PolymorphicSerializer(NavKey::class),
                    value = route,
                    configuration = routeSavedStateConfiguration,
                )
            val decoded =
                decodeFromSavedState(
                    deserializer = PolymorphicSerializer(NavKey::class),
                    savedState = encoded,
                    configuration = routeSavedStateConfiguration,
                )

            assertThat(decoded).isEqualTo(route)
        }
    }
}

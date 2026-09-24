package com.jvcs.tracky.features.project.presentation.projectoverview

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsAtLeast
import assertk.assertions.containsExactly
import com.jvcs.tracky.core.domain.auth.User
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.features.project.presentation.models.ProjectUi
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.archive_selected
import tracky.composeapp.generated.resources.cancel
import tracky.composeapp.generated.resources.close
import tracky.composeapp.generated.resources.confirm
import tracky.composeapp.generated.resources.delete_project_title
import tracky.composeapp.generated.resources.delete_projects_title
import tracky.composeapp.generated.resources.delete_selected
import tracky.composeapp.generated.resources.drawer_archive
import tracky.composeapp.generated.resources.drawer_projects
import tracky.composeapp.generated.resources.drawer_reminders
import tracky.composeapp.generated.resources.drawer_trash
import tracky.composeapp.generated.resources.log_out
import tracky.composeapp.generated.resources.logout_not_possible
import tracky.composeapp.generated.resources.navigation_menu
import tracky.composeapp.generated.resources.new_project
import tracky.composeapp.generated.resources.no_current_projects
import tracky.composeapp.generated.resources.no_internet_connection
import tracky.composeapp.generated.resources.pin_selected
import tracky.composeapp.generated.resources.pinned
import tracky.composeapp.generated.resources.sort_by
import tracky.composeapp.generated.resources.sort_creation_date
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
internal class ProjectOverviewScreenTest {

    private val actions = mutableListOf<ProjectOverviewAction>()
    private val navigation = mutableListOf<String>()

    private fun text(resource: StringResource) = runBlocking { getString(resource) }

    private fun ComposeUiTest.show(state: ProjectOverviewState) =
        setContent {
            TrackyTheme {
                ProjectOverviewScreen(
                    state = state,
                    onAction = { actions += it },
                    snackbarHostState = SnackbarHostState(),
                    onNavigateToArchive = { navigation += "archive" },
                    onNavigateToTrash = { navigation += "trash" },
                    sortOption = state.sortOption,
                )
            }
        }

    @Test
    fun noProjectsShowsTheEmptySectionAndTheFabAddsOne() =
        runComposeUiTest {
            show(ProjectOverviewState(isLoading = false))

            onNodeWithText(text(Res.string.no_current_projects)).assertExists()
            onNodeWithContentDescription(text(Res.string.new_project)).performClick()

            assertThat(actions).containsExactly(ProjectOverviewAction.OnFabClick)
        }

    @Test
    fun cardsOpenOnClickAndSelectOnLongPress() =
        runComposeUiTest {
            show(listState)

            onNodeWithText(text(Res.string.pinned)).assertExists()
            onNodeWithText("Pinned project").performClick()
            onNodeWithText("Other project").performTouchInput { longClick() }
            onNodeWithContentDescription(text(Res.string.sort_by)).performClick()
            onNodeWithContentDescription(text(Res.string.navigation_menu)).performClick()

            assertThat(actions).containsAtLeast(
                ProjectOverviewAction.OnProjectCardClick("p-1"),
                ProjectOverviewAction.OnProjectCardLongPress("p-2"),
                ProjectOverviewAction.OnToggleSortBottomSheet,
            )
        }

    @Test
    fun editModeActsOnTheSelection() =
        runComposeUiTest {
            show(listState.copy(isEditModeActive = true, selectedProjectIds = setOf("p-2")))

            onNodeWithText("Other project").performClick()
            onNodeWithContentDescription(text(Res.string.pin_selected)).performClick()
            onNodeWithContentDescription(text(Res.string.archive_selected)).performClick()
            onNodeWithContentDescription(text(Res.string.delete_selected)).performClick()

            assertThat(actions).containsAtLeast(
                ProjectOverviewAction.OnProjectCardToggleSelection("p-2"),
                ProjectOverviewAction.OnPinSelectedClick,
                ProjectOverviewAction.OnArchiveSelectedClick,
                ProjectOverviewAction.OnDeleteSelectedClick,
            )
        }

    @Test
    fun deletingOneOrManyProjectsAsksFirst() =
        runComposeUiTest {
            show(listState.copy(isDeleteConfirmationDialogVisible = true, selectedProjectIds = setOf("p-1", "p-2")))

            onNodeWithText(text(Res.string.delete_projects_title)).assertExists()
            onNodeWithText(text(Res.string.confirm)).performClick()
            onNodeWithText(text(Res.string.cancel)).performClick()

            assertThat(actions).containsExactly(
                ProjectOverviewAction.OnConfirmDelete,
                ProjectOverviewAction.OnDismissDeleteDialog,
            )
        }

    @Test
    fun deletingASingleProjectSaysSo() =
        runComposeUiTest {
            show(listState.copy(isDeleteConfirmationDialogVisible = true, selectedProjectIds = setOf("p-1")))

            onNodeWithText(text(Res.string.delete_project_title)).assertExists()
        }

    @Test
    fun logoutAsksOnlineAndExplainsOffline() =
        runComposeUiTest {
            show(listState.copy(showLogoutConfirmation = true, isOnline = false))

            onNodeWithText(text(Res.string.no_internet_connection)).assertExists()
            onNodeWithText(text(Res.string.logout_not_possible)).assertExists()
            onNodeWithText(text(Res.string.close)).performClick()

            assertThat(actions).containsExactly(ProjectOverviewAction.OnDismissLogoutConfirmation)
        }

    @Test
    fun logoutOnlineConfirms() =
        runComposeUiTest {
            show(listState.copy(showLogoutConfirmation = true))

            onNodeWithText(text(Res.string.confirm)).performClick()

            assertThat(actions).containsExactly(ProjectOverviewAction.OnConfirmLogout)
        }

    @Test
    fun theProfileMenuLogsOut() =
        runComposeUiTest {
            show(listState)

            onNodeWithText("AL").performClick()
            onNodeWithText(text(Res.string.log_out)).performClick()

            assertThat(actions).containsExactly(ProjectOverviewAction.OnLogoutClick)
        }

    @Test
    fun theSortSheetPicksAnOption() =
        runComposeUiTest {
            show(listState.copy(isSortBottomSheetVisible = true))

            onNodeWithText(text(Res.string.sort_creation_date)).performClick()
            // The option is reported once the sheet has finished hiding.
            waitForIdle()

            assertThat(actions).contains(ProjectOverviewAction.OnSortOptionSelected(SortOption.CREATION_DATE))
        }

    @Test
    fun theAddSheetCreatesTheTypedProject() =
        runComposeUiTest {
            show(
                listState.copy(
                    isAddNewProjectBottomSheetVisible = true,
                    addProjectTextFieldState = TextFieldState("Garden"),
                ),
            )

            onNodeWithText(text(Res.string.confirm)).performClick()

            assertThat(actions).contains(ProjectOverviewAction.OnAddProjectClick("Garden"))
        }

    @Test
    fun theDrawerNavigatesToArchiveAndTrash() =
        runComposeUiTest {
            show(listState)

            // Each item closes the drawer, so it is reopened from the menu before every tap.
            listOf(
                Res.string.drawer_projects,
                Res.string.drawer_reminders,
                Res.string.drawer_archive,
                Res.string.drawer_trash,
            ).forEach { item ->
                onNodeWithContentDescription(text(Res.string.navigation_menu)).performClick()
                waitForIdle()
                onNodeWithText(text(item)).performClick()
                waitForIdle()
            }

            assertThat(navigation).containsExactly("archive", "trash")
        }

    private companion object {
        fun project(
            id: String,
            title: String,
            pinned: Boolean = false,
        ) = ProjectUi(
            projectId = id,
            title = title,
            description = "About $title",
            color = Color.Green,
            totalDurationMillis = 5_400_000L,
            startDateTimeUtc = "01.08.2026",
            isFinished = false,
            endDateTimeUtc = null,
            isPinned = pinned,
        )

        val listState =
            ProjectOverviewState(
                localUser =
                    User(
                        id = "u-1",
                        email = "ada@example.com",
                        username = "Ada Lovelace",
                        hasVerifiedEmail = true,
                    ),
                pinnedProjects = listOf(project("p-1", "Pinned project", pinned = true)),
                otherProjects = listOf(project("p-2", "Other project")),
                isLoading = false,
            )
    }
}

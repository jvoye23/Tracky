package com.jvcs.tracky.features.project.presentation.projectarchive

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import assertk.assertThat
import assertk.assertions.containsExactly
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.presentation.fakes.FakeProjectOrganizationRepository
import com.jvcs.tracky.features.project.presentation.fakes.FakeProjectRepository
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.cancel
import tracky.composeapp.generated.resources.confirm
import tracky.composeapp.generated.resources.delete_permanently
import tracky.composeapp.generated.resources.drawer_projects
import tracky.composeapp.generated.resources.drawer_trash
import tracky.composeapp.generated.resources.navigation_menu
import tracky.composeapp.generated.resources.restore_selected
import tracky.composeapp.generated.resources.search_in_archive
import kotlin.test.Test
import kotlin.time.Instant

/** Drives the real view model through the Root, over fake repositories. */
@OptIn(ExperimentalTestApi::class)
internal class ProjectArchiveScreenTest {

    private val projectRepository = FakeProjectRepository(project = null)
    private val organizationRepository =
        FakeProjectOrganizationRepository(
            archived = listOf(project("a-1", "Old garden"), project("a-2", "Old kitchen")),
        )
    private val navigation = mutableListOf<String>()

    private fun text(resource: StringResource) = runBlocking { getString(resource) }

    private fun ComposeUiTest.showRoot() =
        setContent {
            TrackyTheme {
                ProjectArchiveScreenRoot(
                    onNavigateToDetail = { navigation += "detail:$it" },
                    onNavigateToProjects = { navigation += "projects" },
                    onNavigateToTrash = { navigation += "trash" },
                    viewModel = ProjectArchiveViewModel(projectRepository, organizationRepository),
                )
            }
        }

    @Test
    fun aCardOpensItsProjectAndSearchNarrowsTheList() =
        runComposeUiTest {
            showRoot()

            onNodeWithText("Old garden").performClick()
            onNodeWithContentDescription(text(Res.string.search_in_archive)).performClick()
            onNode(hasSetTextAction()).performTextInput("kitchen")

            onNodeWithText("Old garden").assertDoesNotExist()
            onNodeWithText("Old kitchen").assertExists()
            assertThat(navigation).containsExactly("detail:a-1")
        }

    @Test
    fun reactivatingUnarchivesTheSelection() =
        runComposeUiTest {
            showRoot()

            onNodeWithText("Old garden").performTouchInput { longClick() }
            onNodeWithText("Old kitchen").performClick()
            onNodeWithContentDescription(text(Res.string.restore_selected)).performClick()
            waitForIdle()

            assertThat(organizationRepository.archivedCalls).containsExactly("a-1" to false, "a-2" to false)
        }

    @Test
    fun aFailedReactivationSaysSo() =
        runComposeUiTest {
            organizationRepository.writeResult = Result.Error(DataError.Local.UNKNOWN)
            showRoot()

            onNodeWithText("Old garden").performTouchInput { longClick() }
            onNodeWithContentDescription(text(Res.string.restore_selected)).performClick()

            onNodeWithText("Failed to reactivate all selected projects").assertExists()
        }

    @Test
    fun deletingAsksFirstAndCanBeCancelled() =
        runComposeUiTest {
            showRoot()

            onNodeWithText("Old garden").performTouchInput { longClick() }
            onNodeWithContentDescription(text(Res.string.delete_permanently)).performClick()
            onNodeWithText(text(Res.string.cancel)).performClick()
            onNodeWithContentDescription(text(Res.string.delete_permanently)).performClick()
            onNodeWithText(text(Res.string.confirm)).performClick()
            waitForIdle()

            assertThat(projectRepository.deletedIds).containsExactly("a-1")
        }

    @Test
    fun theDrawerLeadsToProjectsAndTrash() =
        runComposeUiTest {
            showRoot()

            listOf(Res.string.drawer_projects, Res.string.drawer_trash).forEach { item ->
                onNodeWithContentDescription(text(Res.string.navigation_menu)).performClick()
                waitForIdle()
                onNodeWithText(text(item)).performClick()
                waitForIdle()
            }

            assertThat(navigation).containsExactly("projects", "trash")
        }

    private companion object {
        fun project(id: String, title: String) =
            Project(
                projectId = id,
                title = title,
                description = null,
                colorArgb = null,
                totalDurationMillis = 60_000L,
                startDateTimeUtc = Instant.parse("2026-08-01T09:00:00Z"),
                isFinished = false,
                endDateTimeUtc = null,
                isArchived = true,
            )
    }
}

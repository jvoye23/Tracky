package com.jvcs.tracky.features.project.presentation.projecttrash

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.runDesktopComposeUiTest
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
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
import tracky.composeapp.generated.resources.drawer_archive
import tracky.composeapp.generated.resources.drawer_projects
import tracky.composeapp.generated.resources.failed_to_delete_all_selected_projects
import tracky.composeapp.generated.resources.failed_to_restore_all_selected_projects
import tracky.composeapp.generated.resources.navigation_menu
import tracky.composeapp.generated.resources.restore_selected
import tracky.composeapp.generated.resources.search_in_trash
import kotlin.test.Test
import kotlin.time.Instant

/** Drives the real view model through the Root, over fake repositories. */
@OptIn(ExperimentalTestApi::class)
internal class ProjectTrashScreenTest {

    private val projectRepository = FakeProjectRepository(project = null)
    private val organizationRepository =
        FakeProjectOrganizationRepository(trashed = listOf(project("t-1", "Old garden"), project("t-2", "Old kitchen")))
    private val navigation = mutableListOf<String>()

    private fun text(resource: StringResource) = runBlocking { getString(resource) }

    private fun ComposeUiTest.showRoot() =
        setContent {
            TrackyTheme {
                ProjectTrashScreenRoot(
                    onNavigateToProjects = { navigation += "projects" },
                    onNavigateToArchive = { navigation += "archive" },
                    viewModel = ProjectTrashViewModel(projectRepository, organizationRepository),
                )
            }
        }

    private fun ComposeUiTest.select(title: String) = onNodeWithText(title).performTouchInput { longClick() }

    @Test
    fun searchNarrowsTheList() =
        runComposeUiTest {
            showRoot()

            onNodeWithContentDescription(text(Res.string.search_in_trash)).performClick()
            onNode(hasSetTextAction()).performTextInput("kitchen")

            onNodeWithText("Old garden").assertDoesNotExist()
            onNodeWithText("Old kitchen").assertExists()
        }

    @Test
    fun restoringClearsTheTrashStampOfTheSelection() =
        runComposeUiTest {
            showRoot()

            select("Old garden")
            onNodeWithText("Old kitchen").performClick()
            onNodeWithText("Old kitchen").performClick()
            onNodeWithContentDescription(text(Res.string.restore_selected)).performClick()
            waitForIdle()

            assertThat(organizationRepository.trashedCalls).containsExactly("t-1" to null)
        }

    @Test
    fun aFailedRestoreSaysSo() =
        runComposeUiTest {
            organizationRepository.writeResult = Result.Error(DataError.Local.UNKNOWN)
            showRoot()

            select("Old garden")
            onNodeWithContentDescription(text(Res.string.restore_selected)).performClick()

            onNodeWithText(text(Res.string.failed_to_restore_all_selected_projects)).assertExists()
        }

    @Test
    fun deletingPermanentlyAsksFirstAndCanBeCancelled() =
        runComposeUiTest {
            showRoot()

            select("Old garden")
            onNodeWithContentDescription(text(Res.string.delete_permanently)).performClick()
            onNodeWithText(text(Res.string.cancel)).performClick()
            onNodeWithContentDescription(text(Res.string.delete_permanently)).performClick()
            onNodeWithText(text(Res.string.confirm)).performClick()
            waitForIdle()

            assertThat(projectRepository.deletedIds).containsExactly("t-1")
        }

    @Test
    fun aFailedDeleteSaysSo() =
        runComposeUiTest {
            projectRepository.deleteResult = Result.Error(DataError.Local.UNKNOWN)
            showRoot()

            select("Old garden")
            onNodeWithContentDescription(text(Res.string.delete_permanently)).performClick()
            onNodeWithText(text(Res.string.confirm)).performClick()

            onNodeWithText(text(Res.string.failed_to_delete_all_selected_projects)).assertExists()
        }

    @Test
    fun theDrawerLeadsToProjectsAndArchive() =
        runPhonePortraitTest {
            showRoot()

            listOf(Res.string.drawer_projects, Res.string.drawer_archive).forEach { item ->
                onNodeWithContentDescription(text(Res.string.navigation_menu)).performClick()
                waitForIdle()
                onNodeWithText(text(item)).performClick()
                waitForIdle()
            }

            assertThat(navigation).containsExactly("projects", "archive")
        }

    @Test
    fun wideWindowsShowTheRailInsteadOfTheDrawer() =
        runTabletLandscapeTest {
            showRoot()

            onNodeWithContentDescription(text(Res.string.navigation_menu)).assertDoesNotExist()
            listOf(Res.string.drawer_projects, Res.string.drawer_archive).forEach { item ->
                onNodeWithText(text(item)).performClick()
                waitForIdle()
            }

            assertThat(navigation).containsExactly("projects", "archive")
        }

    @Test
    fun landscapeWindowsLayCardsSideBySide() =
        runTabletLandscapeTest {
            showRoot()
            waitForIdle()

            val first = onNodeWithText("Old garden").getBoundsInRoot()
            val second = onNodeWithText("Old kitchen").getBoundsInRoot()

            assertThat(second.top).isEqualTo(first.top)
            assertThat(second.left).isGreaterThan(first.left)
        }

    // The window size picks the navigation (drawer vs rail) and the number of card columns.
    private fun runPhonePortraitTest(block: ComposeUiTest.() -> Unit) =
        runDesktopComposeUiTest(width = 412, height = 915) { block() }

    private fun runTabletLandscapeTest(block: ComposeUiTest.() -> Unit) =
        runDesktopComposeUiTest(width = 1280, height = 800) { block() }

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
                trashedAt = Instant.parse("2026-09-01T09:00:00Z"),
            )
    }
}

package com.jvcs.tracky.features.project.presentation.edittext

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.SavedStateHandle
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.back
import tracky.composeapp.generated.resources.edit_uppercase
import tracky.composeapp.generated.resources.project_details_uppercase
import tracky.composeapp.generated.resources.project_info_saved
import tracky.composeapp.generated.resources.save
import tracky.composeapp.generated.resources.task_details_uppercase
import kotlin.test.Test
import kotlin.time.Instant

/** Drives the real view model through the Root, over the fakes the view-model test uses. */
@OptIn(ExperimentalTestApi::class)
internal class EditTextScreenTest {

    private val projectRepository = FakeEditTextProjectRepository(storedProject)
    private var backClicks = 0

    private fun text(resource: StringResource) = runBlocking { getString(resource) }

    private fun ComposeUiTest.showRoot(target: EditTextTarget, taskId: String? = null) =
        setContent {
            TrackyTheme {
                EditTextScreenRoot(
                    onNavigateBack = { backClicks++ },
                    viewModel =
                        EditTextViewModel(
                            isEditMode = false,
                            projectId = "p1",
                            target = target,
                            taskId = taskId,
                            subTaskId = null,
                            projectRepository = projectRepository,
                            projectTaskRepository = FakeEditTextTaskRepository(storedTask),
                            subTaskRepository = FakeEditTextSubTaskRepository(emptyList()),
                            timeProvider = FixedTimeProvider,
                            savedStateHandle = SavedStateHandle(),
                        ),
                )
            }
        }

    @Test
    fun editingAndSavingAProjectConfirmsIt() =
        runComposeUiTest {
            showRoot(EditTextTarget.PROJECT)

            onNodeWithText(text(Res.string.project_details_uppercase)).assertExists()
            onNodeWithText("Garden").assertExists()
            onNodeWithContentDescription(text(Res.string.edit_uppercase)).performClick()
            onAllNodes(hasSetTextAction()).onFirst().performTextInput(" plan")
            onNodeWithContentDescription(text(Res.string.save)).performClick()

            onNodeWithText(text(Res.string.project_info_saved)).assertExists()
            assertThat(projectRepository.upserted.last().title).isEqualTo("Garden plan")
        }

    @Test
    fun aTaskShowsItsOwnTextAndBackLeaves() =
        runComposeUiTest {
            showRoot(EditTextTarget.TASK, taskId = "t1")

            onNodeWithText(text(Res.string.task_details_uppercase)).assertExists()
            onNodeWithText("Dig beds").assertExists()
            onNodeWithContentDescription(text(Res.string.back)).performClick()

            assertThat(backClicks).isEqualTo(1)
        }

    private companion object {
        val storedProject =
            Project(
                projectId = "p1",
                title = "Garden",
                description = "Spring work",
                colorArgb = null,
                totalDurationMillis = null,
                startDateTimeUtc = Instant.fromEpochMilliseconds(0),
                isFinished = false,
                endDateTimeUtc = null,
            )

        val storedTask =
            ProjectTask(
                projectTaskId = "t1",
                title = "Dig beds",
                description = "Before April",
                durationMillis = 0L,
                startDateTimeUtc = Instant.fromEpochMilliseconds(0),
                parentProjectId = "p1",
                isTimerRunning = false,
            )
    }
}

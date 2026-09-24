package com.jvcs.tracky.features.project.presentation.projectdetail

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsAtLeast
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.features.project.presentation.models.PerDayStripUi
import com.jvcs.tracky.features.project.presentation.models.PerDayUi
import com.jvcs.tracky.features.project.presentation.models.ProjectSubTaskUi
import com.jvcs.tracky.features.project.presentation.models.ProjectTaskUi
import com.jvcs.tracky.features.project.presentation.models.ProjectUi
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.add_subtask
import tracky.composeapp.generated.resources.add_task
import tracky.composeapp.generated.resources.create_new_task
import tracky.composeapp.generated.resources.daily_overview_title
import tracky.composeapp.generated.resources.delete
import tracky.composeapp.generated.resources.edit
import tracky.composeapp.generated.resources.hide_subtasks
import tracky.composeapp.generated.resources.light_text_color
import tracky.composeapp.generated.resources.ok
import tracky.composeapp.generated.resources.save
import tracky.composeapp.generated.resources.select
import tracky.composeapp.generated.resources.select_project_color
import tracky.composeapp.generated.resources.start_timer
import tracky.composeapp.generated.resources.stop_timer
import tracky.composeapp.generated.resources.timer_running_on_another_device
import tracky.composeapp.generated.resources.timer_stale_on_another_device
import tracky.composeapp.generated.resources.uncheck_task_blocked_title
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
internal class ProjectDetailScreenTest {

    private val actions = mutableListOf<ProjectDetailAction>()

    private fun text(resource: StringResource) = runBlocking { getString(resource) }

    private fun ComposeUiTest.show(state: ProjectDetailState) =
        setContent {
            TrackyTheme {
                ProjectDetailScreen(
                    state = state,
                    onAction = { actions += it },
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

    /** Brings a lazily composed row on screen before it is looked up; the per-day strip scrolls sideways. */
    private fun ComposeUiTest.scrollTo(matcher: SemanticsMatcher) =
        onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(VerticalScrollAxisRange))
            .performScrollToNode(matcher)

    private fun ComposeUiTest.scrollTo(text: String) = scrollTo(hasText(text))

    @Test
    fun loadingShowsNoProject() =
        runComposeUiTest {
            show(ProjectDetailState())

            onNodeWithText("PROJECT DETAILS").assertExists()
        }

    @Test
    fun viewModeWiresEveryTaskAffordance() =
        runComposeUiTest {
            show(viewState.copy(isRunningTimerForeign = true))

            onNodeWithText(text(Res.string.timer_running_on_another_device)).assertExists()
            onNodeWithContentDescription("Back").performClick()
            onNodeWithContentDescription(text(Res.string.daily_overview_title)).performClick()
            onNodeWithContentDescription(text(Res.string.edit)).performClick()
            onNodeWithText("Tue").performClick()
            scrollTo(hasContentDescription(text(Res.string.add_task)))
            onNodeWithContentDescription(text(Res.string.add_task)).performClick()
            scrollTo("Write report")
            onNodeWithText("Write report").performClick()
            onAllNodesWithContentDescription(text(Res.string.stop_timer)).onFirst().performClick()
            onNodeWithContentDescription(text(Res.string.hide_subtasks)).performClick()
            scrollTo("Proofread")
            onAllNodesWithContentDescription(text(Res.string.start_timer)).onFirst().performClick()

            assertThat(actions).containsAtLeast(
                ProjectDetailAction.OnBackClick,
                ProjectDetailAction.OnDailyOverviewClick(-1),
                ProjectDetailAction.OnEditModeClick,
                ProjectDetailAction.OnDailyOverviewClick(LocalDate(2026, 8, 25).toEpochDays()),
                ProjectDetailAction.OnToggleAddNewProjectSessionBottomSheet,
                ProjectDetailAction.OnProjectSessionCardClick(TASK_ID),
                ProjectDetailAction.OnToggleTaskExpanded(TASK_ID),
            )
        }

    @Test
    fun editModeWiresTheEditingAffordances() =
        runComposeUiTest {
            show(viewState.copy(isEditMode = true, isRunningTimerStale = true))

            onNodeWithText(text(Res.string.timer_stale_on_another_device)).assertExists()
            onNodeWithText(text(Res.string.select_project_color)).performClick()
            onNodeWithText(text(Res.string.light_text_color)).assertExists()
            onNodeWithText("Project One").performClick()
            scrollTo("Write report")
            onNodeWithText("Write report").performClick()
            scrollTo("Draft")
            onNodeWithText("Draft").performClick()
            scrollTo(text(Res.string.add_subtask))
            onNodeWithText(text(Res.string.add_subtask)).performClick()
            onAllNodesWithContentDescription(text(Res.string.delete)).onFirst().performClick()
            onNodeWithContentDescription(text(Res.string.save)).performClick()
            onNodeWithContentDescription("Cancel").performClick()

            assertThat(actions).containsAtLeast(
                ProjectDetailAction.OnToggleColorPicker,
                ProjectDetailAction.OnProjectEditTextClick(isEditMode = true, projectId = PROJECT_ID),
                ProjectDetailAction.OnTaskTitleClick(TASK_ID),
                ProjectDetailAction.OnSubTaskClick(TASK_ID, "sub-1"),
                ProjectDetailAction.OnAddSubTaskClick(TASK_ID),
                ProjectDetailAction.OnSaveClick,
                ProjectDetailAction.OnCloseAndCancelClick,
            )
        }

    @Test
    fun theUncheckBlockedDialogDismissesWithOk() =
        runComposeUiTest {
            show(viewState.copy(isUncheckTaskBlockedDialogVisible = true))

            onNodeWithText(text(Res.string.uncheck_task_blocked_title)).assertExists()
            onNodeWithText(text(Res.string.ok)).performClick()

            assertThat(actions).contains(ProjectDetailAction.OnDismissUncheckTaskDialog)
        }

    @Test
    fun theAddTaskSheetOpens() =
        runComposeUiTest {
            show(viewState.copy(isAddNewProjectTaskBottomSheetVisible = true))
            onNodeWithText(text(Res.string.create_new_task)).assertExists()
        }

    @Test
    fun theColorPickerSavesItsColor() =
        runComposeUiTest {
            show(viewState.copy(isEditMode = true, isColorPickerVisible = true, projectColor = Color.Red))

            onNodeWithText(text(Res.string.select)).performClick()

            assertThat(actions).contains(ProjectDetailAction.OnColorChanged(Color.Red))
        }

    private companion object {
        const val PROJECT_ID = "project-1"
        const val TASK_ID = "task-1"

        fun subTask(
            id: String,
            title: String,
            running: Boolean = false,
            finished: Boolean = false,
        ) = ProjectSubTaskUi(
            projectSubTaskId = id,
            title = title,
            description = null,
            durationMillis = 90_000L,
            formattedStartDateTime = "25.08.2026, 10:15",
            formattedEndDateTimeUtc = null,
            isTimerRunning = running,
            isFinished = finished,
        )

        fun task(
            id: String,
            title: String,
            subTasks: List<ProjectSubTaskUi> = emptyList(),
            running: Boolean = false,
        ) = ProjectTaskUi(
            projectTaskId = id,
            title = title,
            description = "About $title",
            durationMillis = 3_600_000L,
            formattedStateDateTime = "25.08.2026, 09:00",
            formattedEndDateTimeUtc = "",
            isTimerRunning = running,
            subTasks = subTasks,
            isFinished = false,
        )

        val viewState =
            ProjectDetailState(
                project =
                    ProjectUi(
                        projectId = PROJECT_ID,
                        title = "Project One",
                        description = "A project",
                        color = Color.Blue,
                        totalDurationMillis = 7_200_000L,
                        startDateTimeUtc = "01.08.2026",
                        isFinished = false,
                        endDateTimeUtc = null,
                        projectTasks =
                            listOf(
                                task(
                                    TASK_ID,
                                    "Write report",
                                    subTasks =
                                        listOf(
                                            subTask("sub-1", "Draft", running = true),
                                            subTask("sub-2", "Proofread", finished = true),
                                        ),
                                ),
                                task("task-2", "Send invoice", running = true),
                                task("task-3", "Archive files").copy(isFinished = true),
                            ),
                    ),
                titleText = "Project One",
                descriptionText = "A project",
                projectColor = Color.Blue,
                perDayStrip =
                    PerDayStripUi(
                        days =
                            listOf(
                                PerDayUi("Mon", "24.8", null, 0L, LocalDate(2026, 8, 24)),
                                PerDayUi("Tue", "25.8", "01:30:00", 5_400_000L, LocalDate(2026, 8, 25)),
                            ),
                        busiestDayLabel = "Tue 25.8",
                    ),
            )
    }
}

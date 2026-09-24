package com.jvcs.tracky.features.project.presentation.taskdetail

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import assertk.assertThat
import assertk.assertions.containsExactly
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.features.project.presentation.models.ProjectTaskUi
import com.jvcs.tracky.features.project.presentation.taskdetail.model.DailyStatistic
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.edit_task_uppercase
import tracky.composeapp.generated.resources.start_timer
import tracky.composeapp.generated.resources.task_details
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
internal class TaskDetailScreenTest {

    private val actions = mutableListOf<TaskDetailAction>()

    private fun text(resource: StringResource) = runBlocking { getString(resource) }

    private fun ComposeUiTest.show(state: TaskDetailState) =
        setContent { TrackyTheme { TaskDetailScreen(state = state, onAction = { actions += it }) } }

    @Test
    fun viewModeListsTheSessionsAndWiresItsButtons() =
        runComposeUiTest {
            show(taskState)

            onNodeWithText(text(Res.string.task_details)).assertExists()
            onNodeWithText("Mon, Aug 24").assertExists()
            onNodeWithText("Dig beds").performClick()
            onNodeWithText(text(Res.string.start_timer)).performClick()
            onNodeWithContentDescription("Edit").performClick()
            onNodeWithContentDescription("Back").performClick()

            assertThat(actions).containsExactly(
                TaskDetailAction.OnHeaderClick,
                TaskDetailAction.OnToggleTimer,
                TaskDetailAction.OnEditModeClick,
                TaskDetailAction.OnBackClick,
            )
        }

    @Test
    fun editModeClosesFromEitherButton() =
        runComposeUiTest {
            show(taskState.copy(isEditMode = true, isTimerRunning = true))

            onNodeWithText(text(Res.string.edit_task_uppercase)).assertExists()
            onNodeWithContentDescription("Done").performClick()
            onNodeWithContentDescription("Close").performClick()

            assertThat(actions).containsExactly(
                TaskDetailAction.OnCloseEditModeClick,
                TaskDetailAction.OnCloseEditModeClick,
            )
        }

    private companion object {
        val taskState =
            TaskDetailState(
                task =
                    ProjectTaskUi(
                        projectTaskId = "t1",
                        title = "Dig beds",
                        description = "Before April",
                        durationMillis = 5_400_000L,
                        formattedStateDateTime = "24.08.2026, 09:00",
                        formattedEndDateTimeUtc = "",
                        isTimerRunning = false,
                        subTasks = emptyList(),
                        isFinished = false,
                    ),
                projectId = "p1",
                projectColor = Color.Magenta,
                dailyStatistics =
                    listOf(
                        DailyStatistic("i1", "Mon, Aug 24", "09:00", "10:00", "01:00:00"),
                        DailyStatistic("i2", "Tue, Aug 25", "09:00", "09:30", "00:30:00"),
                    ),
            )
    }
}

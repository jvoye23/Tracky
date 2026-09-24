package com.jvcs.tracky.features.project.presentation.strandedtimer

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import assertk.assertThat
import assertk.assertions.containsExactly
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.features.project.domain.timer.StrandedTimer
import com.jvcs.tracky.features.project.domain.timer.StrandedTimerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.cancel
import tracky.composeapp.generated.resources.save
import tracky.composeapp.generated.resources.stranded_timer_discard
import tracky.composeapp.generated.resources.stranded_timer_edit
import tracky.composeapp.generated.resources.stranded_timer_not_counted
import tracky.composeapp.generated.resources.stranded_timer_title
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Drives the real view model through the app-level host, one parked timer at a time. */
@OptIn(ExperimentalTestApi::class)
internal class StrandedTimerDialogHostTest {

    private val repository = QueueRepository(listOf(timer("a"), timer("b", subTask = "Per-day strip")))

    private fun text(resource: StringResource) = runBlocking { getString(resource) }

    private fun ComposeUiTest.showHost() =
        setContent { TrackyTheme { StrandedTimerDialogHost(viewModel = StrandedTimerViewModel(repository)) } }

    @Test
    fun eachParkedTimerIsResolvedInTurn() =
        runComposeUiTest {
            showHost()

            onNodeWithText(text(Res.string.stranded_timer_title)).assertExists()
            onNodeWithText("1 more to review").assertExists()
            onNodeWithText("Keep", substring = true).performClick()
            waitForIdle()
            onNodeWithText("Subtask: Per-day strip").assertExists()
            onNodeWithText(text(Res.string.stranded_timer_not_counted)).assertExists()
            onNodeWithText(text(Res.string.stranded_timer_discard)).performClick()
            waitForIdle()

            onNodeWithText(text(Res.string.stranded_timer_title)).assertDoesNotExist()
            assertThat(repository.resolved).containsExactly("kept a", "discarded b")
        }

    @Test
    fun anEditedDurationIsKeptInstead() =
        runComposeUiTest {
            showHost()

            onNodeWithText(text(Res.string.stranded_timer_edit)).performClick()
            onNodeWithText(text(Res.string.cancel)).performClick()
            onNodeWithText(text(Res.string.stranded_timer_edit)).performClick()
            onNode(hasSetTextAction()).performTextClearance()
            onNode(hasSetTextAction()).performTextInput("1:30")
            onNodeWithText(text(Res.string.save)).performClick()
            waitForIdle()

            assertThat(repository.keptDurations).containsExactly(1.hours + 30.minutes)
        }

    private class QueueRepository(initial: List<StrandedTimer>) : StrandedTimerRepository {
        private val parked = MutableStateFlow(initial)
        val resolved = mutableListOf<String>()
        val keptDurations = mutableListOf<Duration>()

        override fun observeStrandedTimers(): Flow<List<StrandedTimer>> = parked

        private fun resolve(timer: StrandedTimer, how: String): EmptyResult<DataError> {
            resolved += "$how ${timer.id}"
            parked.value = parked.value.filterNot { it.id == timer.id }
            return Result.Success(Unit)
        }

        override suspend fun keep(timer: StrandedTimer): EmptyResult<DataError> = resolve(timer, "kept")

        override suspend fun keepWithDuration(timer: StrandedTimer, duration: Duration): EmptyResult<DataError> {
            keptDurations += duration
            return resolve(timer, "kept")
        }

        override suspend fun discard(timer: StrandedTimer): EmptyResult<DataError> = resolve(timer, "discarded")
    }

    private companion object {
        fun timer(id: String, subTask: String? = null) =
            StrandedTimer(
                taskIntervalId = id,
                subTaskIntervalId = null,
                taskId = "t-$id",
                taskTitle = "Project Detail Screen",
                projectTitle = "Tracky App",
                subTaskTitle = subTask,
                startedAt = Instant.fromEpochMilliseconds(0),
                proposedEndAt = Instant.fromEpochMilliseconds(2 * 60 * 60 * 1000L),
                keepingWouldNotBeCounted = subTask != null,
            )
    }
}

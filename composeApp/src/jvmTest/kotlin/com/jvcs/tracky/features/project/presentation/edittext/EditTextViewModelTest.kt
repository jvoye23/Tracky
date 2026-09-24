@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.jvcs.tracky.features.project.presentation.edittext

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.project.ProjectRepository
import com.jvcs.tracky.features.project.domain.subtask.SubTaskRepository
import com.jvcs.tracky.features.project.domain.task.ProjectTaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private const val PROJECT_ID = "p1"
private const val STORED_TITLE = "Stored title"
private const val STORED_DESCRIPTION = "Stored description"
private const val TASK_ID = "t1"
private const val SUB_TASK_ID = "s1"

/**
 * Covers the process-death contract - an unsaved draft in the [SavedStateHandle] outranks the stored
 * project, and every keystroke is mirrored back into the handle - and the task and subtask targets.
 */
class EditTextViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `restored draft wins over the stored project`() =
        runTest {
            val handle =
                SavedStateHandle(
                    mapOf(
                        EditTextViewModel.KEY_TITLE to "Draft title",
                        EditTextViewModel.KEY_DESCRIPTION to "Draft description",
                    ),
                )
            val vm = viewModel(savedStateHandle = handle)

            assertThat(
                vm.state.value.titleState.text
                    .toString(),
            ).isEqualTo("Draft title")
            assertThat(
                vm.state.value.descriptionState.text
                    .toString(),
            ).isEqualTo("Draft description")
        }

    @Test
    fun `an empty handle falls back to the stored project`() =
        runTest {
            val vm = viewModel()

            assertThat(
                vm.state.value.titleState.text
                    .toString(),
            ).isEqualTo(STORED_TITLE)
            assertThat(
                vm.state.value.descriptionState.text
                    .toString(),
            ).isEqualTo(STORED_DESCRIPTION)
        }

    @Test
    fun `typing is mirrored into the handle`() =
        runTest {
            val handle = SavedStateHandle()
            val vm = viewModel(savedStateHandle = handle)

            vm.state.value.titleState
                .setTextAndPlaceCursorAtEnd("Edited title")
            vm.state.value.descriptionState
                .setTextAndPlaceCursorAtEnd("Edited description")
            // Off-composition there is no recomposer to apply the global snapshot, so snapshotFlow only
            // re-reads once the notification is sent by hand.
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            assertThat(handle.get<String>(EditTextViewModel.KEY_TITLE)).isEqualTo("Edited title")
            assertThat(handle.get<String>(EditTextViewModel.KEY_DESCRIPTION)).isEqualTo("Edited description")
        }

    @Test
    fun `edit mode is persisted and cleared once the save lands`() =
        runTest {
            val handle = SavedStateHandle()
            val vm = viewModel(savedStateHandle = handle)

            vm.onAction(EditTextAction.OnEditClick)
            advanceUntilIdle()
            assertThat(handle.get<Boolean>(EditTextViewModel.KEY_IS_EDIT_MODE)).isEqualTo(true)
            assertThat(vm.state.value.isEditMode).isTrue()

            vm.onAction(EditTextAction.OnSaveClick)
            advanceUntilIdle()
            assertThat(handle.get<Boolean>(EditTextViewModel.KEY_IS_EDIT_MODE)).isEqualTo(false)
        }

    @Test
    fun `saving a rename keeps the project's place in the manual order`() =
        runTest {
            // Rebuilding the project from its UI model dropped sortIndex, and a null index sorts first
            // under Custom: a rename moved the project to the top of the overview.
            val stored = project().copy(sortIndex = 3, isPinned = true)
            val repository = FakeEditTextProjectRepository(stored)
            val vm = viewModel(projectRepository = repository)

            vm.onAction(EditTextAction.OnEditClick)
            vm.state.value.titleState
                .setTextAndPlaceCursorAtEnd("Renamed")
            vm.onAction(EditTextAction.OnSaveClick)
            advanceUntilIdle()

            val saved = repository.upserted.single()
            assertThat(saved.title).isEqualTo("Renamed")
            assertThat(saved.sortIndex).isEqualTo(3L)
            assertThat(saved.isPinned).isTrue()
        }

    @Test
    fun `a task loads its own text and saves both fields`() =
        runTest {
            val tasks = FakeEditTextTaskRepository(task())
            val vm =
                viewModel(
                    target = EditTextTarget.TASK,
                    taskId = TASK_ID,
                    isEditMode = true,
                    taskRepository = tasks,
                )
            assertThat(
                vm.state.value.titleState.text
                    .toString(),
            ).isEqualTo("Stored task")
            assertThat(
                vm.state.value.descriptionState.text
                    .toString(),
            ).isEqualTo("Stored task description")

            vm.state.value.titleState
                .setTextAndPlaceCursorAtEnd("Renamed task")
            vm.state.value.descriptionState
                .setTextAndPlaceCursorAtEnd("New description")
            vm.onAction(EditTextAction.OnSaveClick)
            advanceUntilIdle()

            assertThat(
                tasks.textUpdates,
            ).isEqualTo(listOf(Triple<String, String, String?>(TASK_ID, "Renamed task", "New description")))
            assertThat(vm.state.value.isEditMode).isFalse()
        }

    @Test
    fun `a subtask loads its own text and saves from the stored row`() =
        runTest {
            val subTasks = FakeEditTextSubTaskRepository(listOf(subTask()))
            val vm =
                viewModel(
                    target = EditTextTarget.SUBTASK,
                    taskId = TASK_ID,
                    subTaskId = SUB_TASK_ID,
                    isEditMode = true,
                    subTaskRepository = subTasks,
                )
            assertThat(
                vm.state.value.titleState.text
                    .toString(),
            ).isEqualTo("Stored subtask")
            assertThat(
                vm.state.value.descriptionState.text
                    .toString(),
            ).isEqualTo("Stored subtask description")

            vm.state.value.titleState
                .setTextAndPlaceCursorAtEnd("Renamed subtask")
            vm.state.value.descriptionState
                .setTextAndPlaceCursorAtEnd("")
            vm.onAction(EditTextAction.OnSaveClick)
            advanceUntilIdle()

            val saved = subTasks.upserted.single()
            assertThat(saved.title).isEqualTo("Renamed subtask")
            assertThat(saved.description, name = "a cleared description is stored as none").isNull()
            // Everything else comes from the stored row.
            assertThat(saved.durationMillis).isEqualTo(1_000L)
            assertThat(saved.sortIndex).isEqualTo(2L)
        }

    @Test
    fun `a new subtask starts empty and writes nothing for a blank title`() =
        runTest {
            val subTasks = FakeEditTextSubTaskRepository(emptyList())
            val events = mutableListOf<EditTextEvent>()
            val vm =
                viewModel(
                    target = EditTextTarget.NEW_SUBTASK,
                    taskId = TASK_ID,
                    isEditMode = true,
                    subTaskRepository = subTasks,
                    events = events,
                )
            assertThat(
                vm.state.value.titleState.text
                    .toString(),
            ).isEqualTo("")

            vm.onAction(EditTextAction.OnSaveClick)
            advanceUntilIdle()

            assertThat(subTasks.upserted.isEmpty()).isTrue()
            assertThat(events.single() is EditTextEvent.Error).isTrue()
            assertThat(vm.state.value.isEditMode, name = "a refused save keeps the user in the field").isTrue()
        }

    @Test
    fun `saving a new subtask creates it under its task and navigates back`() =
        runTest {
            val subTasks = FakeEditTextSubTaskRepository(emptyList())
            val events = mutableListOf<EditTextEvent>()
            val vm =
                viewModel(
                    target = EditTextTarget.NEW_SUBTASK,
                    taskId = TASK_ID,
                    isEditMode = true,
                    subTaskRepository = subTasks,
                    events = events,
                )

            vm.state.value.titleState
                .setTextAndPlaceCursorAtEnd("Fresh subtask")
            vm.state.value.descriptionState
                .setTextAndPlaceCursorAtEnd("With a description")
            vm.onAction(EditTextAction.OnSaveClick)
            advanceUntilIdle()

            val created = subTasks.upserted.single()
            assertThat(created.title).isEqualTo("Fresh subtask")
            assertThat(created.description).isEqualTo("With a description")
            assertThat(created.parentProjectTaskId).isEqualTo(TASK_ID)
            assertThat(created.parentProjectId).isEqualTo(PROJECT_ID)
            assertThat(events).isEqualTo(listOf<EditTextEvent>(EditTextEvent.NavigateBack))
        }

    @Test
    fun `a blank title is refused for a task`() =
        runTest {
            val tasks = FakeEditTextTaskRepository(task())
            val events = mutableListOf<EditTextEvent>()
            val vm =
                viewModel(
                    target = EditTextTarget.TASK,
                    taskId = TASK_ID,
                    isEditMode = true,
                    taskRepository = tasks,
                    events = events,
                )

            vm.state.value.titleState
                .setTextAndPlaceCursorAtEnd("   ")
            vm.onAction(EditTextAction.OnSaveClick)
            advanceUntilIdle()

            assertThat(tasks.textUpdates.isEmpty()).isTrue()
            assertThat(events.single() is EditTextEvent.Error).isTrue()
        }

    // --- helpers -------------------------------------------------------------------------------

    /**
     * Builds the ViewModel and drains its initial load. The state flow is collected on the
     * background scope because [EditTextViewModel.getProject] runs from `onStart`, so
     * nothing loads until something subscribes.
     */
    private fun TestScope.viewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        project: Project = project(),
        isEditMode: Boolean = false,
        target: EditTextTarget = EditTextTarget.PROJECT,
        taskId: String? = null,
        subTaskId: String? = null,
        projectRepository: FakeEditTextProjectRepository = FakeEditTextProjectRepository(project),
        taskRepository: FakeEditTextTaskRepository = FakeEditTextTaskRepository(task()),
        subTaskRepository: FakeEditTextSubTaskRepository = FakeEditTextSubTaskRepository(listOf(subTask())),
        events: MutableList<EditTextEvent> = mutableListOf(),
    ): EditTextViewModel {
        val vm =
            EditTextViewModel(
                isEditMode = isEditMode,
                projectId = PROJECT_ID,
                target = target,
                taskId = taskId,
                subTaskId = subTaskId,
                projectRepository = projectRepository,
                projectTaskRepository = taskRepository,
                subTaskRepository = subTaskRepository,
                timeProvider = FixedTimeProvider,
                savedStateHandle = savedStateHandle,
            )
        backgroundScope.launch { vm.state.collect { } }
        // Unconfined: advanceUntilIdle does not wait for background work, so a collector on the
        // standard dispatcher would still be holding an event when the test asserts.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        advanceUntilIdle()
        return vm
    }

    private fun task() =
        ProjectTask(
            projectTaskId = TASK_ID,
            title = "Stored task",
            description = "Stored task description",
            durationMillis = 0L,
            startDateTimeUtc = Instant.fromEpochMilliseconds(0),
            parentProjectId = PROJECT_ID,
            isTimerRunning = false,
        )

    private fun subTask() =
        ProjectSubTask(
            projectSubTaskId = SUB_TASK_ID,
            parentProjectTaskId = TASK_ID,
            parentProjectId = PROJECT_ID,
            title = "Stored subtask",
            description = "Stored subtask description",
            durationMillis = 1_000L,
            isTimerRunning = false,
            startDateTimeUtc = Instant.fromEpochMilliseconds(0),
            sortIndex = 2,
        )

    private fun project() =
        Project(
            projectId = PROJECT_ID,
            title = STORED_TITLE,
            description = STORED_DESCRIPTION,
            colorArgb = null,
            totalDurationMillis = null,
            startDateTimeUtc = Instant.fromEpochMilliseconds(0),
            isFinished = false,
            endDateTimeUtc = null,
        )
}

// --- fakes -------------------------------------------------------------------------------------

private class FakeEditTextProjectRepository(project: Project) : ProjectRepository {
    private val projectFlow = MutableStateFlow(project)

    val upserted = mutableListOf<Project>()

    override suspend fun getProjectById(projectId: String): Project? = projectFlow.value

    override suspend fun getProjectWithTasksByProjectId(projectId: String): Project? = projectFlow.value

    override fun observeProjectById(projectId: String): Flow<Project?> = projectFlow

    override fun observeProjectWithTaskTreeById(projectId: String): Flow<Project?> = projectFlow

    override suspend fun upsertProject(project: Project): EmptyResult<DataError> {
        upserted += project
        projectFlow.value = project
        return Result.Success(Unit)
    }

    override fun getProjects(): Flow<List<Project>> = projectFlow.map { listOf(it) }

    override fun getActiveProjects(): Flow<List<Project>> = projectFlow.map { listOf(it) }

    override suspend fun fetchProjects(): EmptyResult<DataError> = Result.Success(Unit)

    override suspend fun deleteProject(projectId: String): EmptyResult<DataError> = Result.Success(Unit)

    override suspend fun deleteAllProjects() = Unit

    override suspend fun syncPendingProjects(): EmptyResult<DataError> = Result.Success(Unit)
}

private object FixedTimeProvider : TimeProvider {
    override val nowZoneTimeInUtc: LocalDateTime = LocalDateTime(2026, 1, 1, 0, 0)
    override val nowInstant: Instant = Instant.fromEpochMilliseconds(1_000_000)
}

private class FakeEditTextTaskRepository(private val task: ProjectTask) : ProjectTaskRepository {
    val textUpdates = mutableListOf<Triple<String, String, String?>>()

    override suspend fun updateProjectTaskText(
        taskId: String,
        title: String,
        description: String?,
    ): EmptyResult<DataError> {
        textUpdates += Triple(taskId, title, description)
        return Result.Success(Unit)
    }

    override fun getProjectTaskWithIntervalsById(taskId: String): Flow<ProjectTask?> =
        flowOf(task.takeIf { it.projectTaskId == taskId })

    override suspend fun upsertProjectTask(projectTask: ProjectTask): EmptyResult<DataError> = Result.Success(Unit)

    override suspend fun deleteProjectTask(projectId: String, taskId: String): EmptyResult<DataError> =
        Result.Success(Unit)

    override suspend fun updateProjectTaskDuration(taskId: String, newDurationMillis: Long): EmptyResult<DataError> =
        Result.Success(Unit)

    override suspend fun updateProjectTaskTitle(taskId: String, title: String): EmptyResult<DataError> =
        Result.Success(Unit)

    override suspend fun startProjectTask(taskId: String): EmptyResult<DataError> = Result.Success(Unit)

    override suspend fun stopProjectTask(taskId: String): EmptyResult<DataError> = Result.Success(Unit)

    override suspend fun reorderTasks(projectId: String, orderedTaskIds: List<String>): EmptyResult<DataError> =
        Result.Success(Unit)

    override suspend fun syncPendingTasks(): EmptyResult<DataError> = Result.Success(Unit)
}

private class FakeEditTextSubTaskRepository(private val subTasks: List<ProjectSubTask>) : SubTaskRepository {
    val upserted = mutableListOf<ProjectSubTask>()

    override fun getSubTasksForTask(taskId: String): Flow<List<ProjectSubTask>> =
        flowOf(subTasks.filter { it.parentProjectTaskId == taskId })

    override suspend fun upsertSubTask(subTask: ProjectSubTask): EmptyResult<DataError> {
        upserted += subTask
        return Result.Success(Unit)
    }

    override suspend fun deleteSubTask(subTaskId: String): EmptyResult<DataError> = Result.Success(Unit)

    override suspend fun startSubTask(subTaskId: String): EmptyResult<DataError> = Result.Success(Unit)

    override suspend fun stopSubTask(subTaskId: String): EmptyResult<DataError> = Result.Success(Unit)

    override suspend fun lastStartedSubTaskId(taskId: String): String? = null

    override suspend fun reorderSubTasks(taskId: String, orderedSubTaskIds: List<String>): EmptyResult<DataError> =
        Result.Success(Unit)

    override suspend fun syncPendingSubTasks(): EmptyResult<DataError> = Result.Success(Unit)
}

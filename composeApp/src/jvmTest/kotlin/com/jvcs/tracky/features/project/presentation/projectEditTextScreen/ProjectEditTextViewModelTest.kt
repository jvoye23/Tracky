@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.jvcs.tracky.features.project.presentation.projectEditTextScreen

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.project.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private const val PROJECT_ID = "p1"
private const val STORED_TITLE = "Stored title"
private const val STORED_DESCRIPTION = "Stored description"

/**
 * Covers the process-death contract: an unsaved draft in the [SavedStateHandle] outranks the stored
 * project, and every keystroke is mirrored back into the handle.
 */
class ProjectEditTextViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `restored draft wins over the stored project`() = runTest {
        val handle = SavedStateHandle(
            mapOf(
                ProjectEditTextViewModel.KEY_TITLE to "Draft title",
                ProjectEditTextViewModel.KEY_DESCRIPTION to "Draft description"
            )
        )
        val vm = viewModel(savedStateHandle = handle)

        assertEquals("Draft title", vm.state.value.titleState.text.toString())
        assertEquals("Draft description", vm.state.value.descriptionState.text.toString())
    }

    @Test
    fun `an empty handle falls back to the stored project`() = runTest {
        val vm = viewModel()

        assertEquals(STORED_TITLE, vm.state.value.titleState.text.toString())
        assertEquals(STORED_DESCRIPTION, vm.state.value.descriptionState.text.toString())
    }

    @Test
    fun `typing is mirrored into the handle`() = runTest {
        val handle = SavedStateHandle()
        val vm = viewModel(savedStateHandle = handle)

        vm.state.value.titleState.setTextAndPlaceCursorAtEnd("Edited title")
        vm.state.value.descriptionState.setTextAndPlaceCursorAtEnd("Edited description")
        // Off-composition there is no recomposer to apply the global snapshot, so snapshotFlow only
        // re-reads once the notification is sent by hand.
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        assertEquals("Edited title", handle.get<String>(ProjectEditTextViewModel.KEY_TITLE))
        assertEquals("Edited description", handle.get<String>(ProjectEditTextViewModel.KEY_DESCRIPTION))
    }

    @Test
    fun `edit mode is persisted and cleared once the save lands`() = runTest {
        val handle = SavedStateHandle()
        val vm = viewModel(savedStateHandle = handle)

        vm.onAction(ProjectEditTextAction.OnEditClick)
        advanceUntilIdle()
        assertEquals(true, handle.get<Boolean>(ProjectEditTextViewModel.KEY_IS_EDIT_MODE))
        assertTrue(vm.state.value.isEditMode)

        vm.onAction(ProjectEditTextAction.OnSaveClick)
        advanceUntilIdle()
        assertEquals(false, handle.get<Boolean>(ProjectEditTextViewModel.KEY_IS_EDIT_MODE))
    }

    // --- helpers -------------------------------------------------------------------------------

    /**
     * Builds the ViewModel and drains its initial load. The state flow is collected on the
     * background scope because [ProjectEditTextViewModel.getProject] runs from `onStart`, so
     * nothing loads until something subscribes.
     */
    private fun TestScope.viewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        project: Project = project(),
        isEditMode: Boolean = false
    ): ProjectEditTextViewModel {
        val vm = ProjectEditTextViewModel(
            isEditMode = isEditMode,
            projectId = PROJECT_ID,
            projectRepository = FakeEditTextProjectRepository(project),
            savedStateHandle = savedStateHandle
        )
        backgroundScope.launch { vm.state.collect { } }
        advanceUntilIdle()
        return vm
    }

    private fun project() = Project(
        projectId = PROJECT_ID,
        title = STORED_TITLE,
        description = STORED_DESCRIPTION,
        colorArgb = null,
        totalDurationMillis = null,
        startDateTimeUtc = Instant.fromEpochMilliseconds(0),
        isFinished = false,
        endDateTimeUtc = null
    )
}

// --- fakes -------------------------------------------------------------------------------------

private class FakeEditTextProjectRepository(project: Project) : ProjectRepository {
    private val projectFlow = MutableStateFlow(project)

    val upserted = mutableListOf<Project>()

    override suspend fun getProjectById(projectId: String): Project? = projectFlow.value
    override suspend fun getProjectWithTasksByProjectId(projectId: String): Project? = projectFlow.value
    override fun observeProjectById(projectId: String): Flow<Project?> = projectFlow

    override suspend fun upsertProject(project: Project): EmptyResult<DataError> {
        upserted += project
        projectFlow.value = project
        return Result.Success(Unit)
    }

    override fun getProjects(): Flow<List<Project>> = projectFlow.map { listOf(it) }
    override fun getActiveProjects(): Flow<List<Project>> = projectFlow.map { listOf(it) }
    override fun getArchivedProjects(): Flow<List<Project>> = flowOf(emptyList())
    override fun getTrashedProjects(): Flow<List<Project>> = flowOf(emptyList())
    override suspend fun fetchProjects(): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun reorderProjects(orderedProjectIds: List<String>): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun setProjectsPinned(projectIds: List<String>, isPinned: Boolean): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun setProjectArchived(projectId: String, isArchived: Boolean): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun setProjectTrashed(projectId: String, trashedAt: Instant?): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun purgeExpiredTrashedProjects(cutoff: Instant): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun deleteProject(projectId: String): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun deleteAllProjects() = Unit
    override suspend fun syncPendingProjects() = Unit
}

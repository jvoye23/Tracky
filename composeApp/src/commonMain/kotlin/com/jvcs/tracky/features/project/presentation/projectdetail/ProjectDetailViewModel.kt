@file:OptIn(ExperimentalUuidApi::class)

package com.jvcs.tracky.features.project.presentation.projectdetail

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.TimeManager
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.core.domain.util.TimerState
import com.jvcs.tracky.core.domain.util.getOrDefault
import com.jvcs.tracky.core.domain.util.onFailure
import com.jvcs.tracky.core.domain.util.platformIoDispatcher
import com.jvcs.tracky.designsystem.util.UiText
import com.jvcs.tracky.features.project.domain.export.ExportFileSharer
import com.jvcs.tracky.features.project.domain.export.ProjectJsonExporter
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.project.ProjectRepository
import com.jvcs.tracky.features.project.domain.subtask.SubTaskRepository
import com.jvcs.tracky.features.project.domain.task.ProjectTaskRepository
import com.jvcs.tracky.features.project.presentation.mappers.toPerDayStripUi
import com.jvcs.tracky.features.project.presentation.mappers.toProjectSubTaskUi
import com.jvcs.tracky.features.project.presentation.mappers.toProjectTaskUi
import com.jvcs.tracky.features.project.presentation.mappers.toProjectUi
import com.jvcs.tracky.features.project.presentation.models.PerDayStripUi
import com.jvcs.tracky.features.project.presentation.models.ProjectSubTaskUi
import com.jvcs.tracky.features.project.presentation.models.ProjectTaskUi
import com.jvcs.tracky.features.project.presentation.util.toUiText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.title
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ProjectDetailViewModel(
    private val projectId: String?,
    private val projectRepository: ProjectRepository,
    private val projectTaskRepository: ProjectTaskRepository,
    private val subTaskRepository: SubTaskRepository,
    private val timeManager: TimeManager,
    private val timeProvider: TimeProvider,
    projectJsonExporter: ProjectJsonExporter,
    exportFileSharer: ExportFileSharer,
    // Injectable so tests can drive the initial load on their own scheduler; production keeps IO.
    private val ioDispatcher: CoroutineDispatcher = platformIoDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(ProjectDetailState())

    private val eventChannel = Channel<ProjectDetailEvent>()
    val events = eventChannel.receiveAsFlow()

    private val taskCompletion =
        TaskCompletion(
            state = _state,
            scope = viewModelScope,
            projectTaskRepository = projectTaskRepository,
            subTaskRepository = subTaskRepository,
            timeProvider = timeProvider,
            onTimeBanked = ::refreshPerDayStrip,
        )

    private val export =
        ProjectDetailExport(
            projectId = projectId,
            state = _state,
            scope = viewModelScope,
            events = eventChannel,
            projectRepository = projectRepository,
            projectJsonExporter = projectJsonExporter,
            exportFileSharer = exportFileSharer,
            timeProvider = timeProvider,
        )

    private var hasLoadedInitialData = false

    /**
     * True from the first move of a drag until the reorder it produced is written (or abandoned).
     * Only while this holds may the displayed order outrank the database's.
     *
     * This became necessary when the task tree started being streamed: while it was read once and
     * refreshed by hand, nothing could arrive mid-drag and replay the move. Now something can.
     */
    private var reorderInFlight = false

    /**
     * The locally held state folded over the live project row.
     *
     * The row is streamed rather than read once because the title and description are edited on
     * their own screen: that ViewModel writes straight to the database, and this entry stays on the
     * back stack with its state intact, so a snapshot taken on entry would still be on screen after
     * the user navigates back.
     *
     * The task tree is streamed too, by [observeTaskTree], for the same reason a second device
     * makes urgent: a sync pull writes another device's rows straight into Room, and a screen that
     * only re-read its tree when it was returned to would sit there showing figures from whenever
     * the user last opened it. It used to be a one-shot read guarded by [updateUiWithTimerValues]
     * clobbering; the overlay is now re-applied after every adoption instead, which gets the same
     * protection without the staleness.
     */
    val state =
        combine(
            _state,
            // A missing id has no row to observe; flowOf(null) keeps the combine emitting, so the
            // error state below still reaches the screen.
            if (projectId != null) projectRepository.observeProjectById(projectId) else flowOf(null),
        ) { state, projectRow -> state.withProjectRow(projectRow) }
            .onStart {
                if (!hasLoadedInitialData) {
                    if (projectId != null) {
                        getProject(projectId)
                    } else {
                        _state.update {
                            it.copy(
                                errorMessage = "Project cannot be found",
                            )
                        }
                    }
                    hasLoadedInitialData = true
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = _state.value,
            )

    init {
        if (projectId != null) observeTaskTree(projectId)
        viewModelScope.launch {
            // A stop leaves the card showing the last second the ticker drew, up to a second below
            // the exact figure banked into the row. Re-reading the project here to close that gap
            // would race the optimistic writes that follow a stop - finishing a task stops its
            // timer first - and overwrite them with the pre-write row. The second is cheaper.
            timeManager.taskStates.collect { activeTimersMap ->
                updateUiWithTimerValues(activeTimersMap)
            }
        }
    }

    fun onAction(action: ProjectDetailAction) {
        when (action) {
            is ProjectDetailAction.OnSaveClick -> {
                saveProjectDetails()
            }

            ProjectDetailAction.OnEditModeClick -> {
                toggleEditMode()
            }

            ProjectDetailAction.OnCloseAndCancelClick -> {
                discardChangesAndExitEditMode()
            }

            ProjectDetailAction.OnBackClick -> {}

            // Handled in UI
            ProjectDetailAction.OnStartTrackerClick -> {}

            ProjectDetailAction.OnStopTrackerClick -> {}

            is ProjectDetailAction.OnProjectSessionCardClick -> {}

            ProjectDetailAction.OnToggleAddNewProjectSessionBottomSheet -> {
                toggleAddNewProjectSessionBottomSheet()
            }

            is ProjectDetailAction.OnCreateProjectSession -> {
                createProjectTask(action.projectSessionTitle)
            }

            is ProjectDetailAction.OnToggleSessionTimer -> {
                onToggleTimer(action.projectSessionId)
            }

            is ProjectDetailAction.OnDeleteSessionClick -> {
                deleteProjectTask(action.sessionId)
            }

            is ProjectDetailAction.OnTaskCheckedChange -> {
                taskCompletion.onTaskCheckedChange(action.taskId)
            }

            ProjectDetailAction.OnDismissUncheckTaskDialog -> {
                _state.update { it.copy(isUncheckTaskBlockedDialogVisible = false) }
            }

            is ProjectDetailAction.OnToggleSubTaskTimer -> {
                onToggleSubTaskTimer(action.subTaskId)
            }

            is ProjectDetailAction.OnDeleteSubTaskClick -> {
                deleteSubTask(action.subTaskId)
            }

            is ProjectDetailAction.OnSubTaskCheckedChange -> {
                taskCompletion.onSubTaskCheckedChange(action.subTaskId)
            }

            is ProjectDetailAction.OnToggleTaskExpanded -> {
                toggleTaskExpanded(action.taskId)
            }

            is ProjectDetailAction.OnTaskReorderMove -> {
                reorderInFlight = true
                _state.update { it.withTaskMoved(action.fromTaskId, action.toTaskId) }
            }

            ProjectDetailAction.OnTaskReorderCommit -> {
                commitTaskReorder()
            }

            ProjectDetailAction.OnTaskReorderCancel -> {
                reorderInFlight = false
                reloadTasksFromDatabase()
            }

            is ProjectDetailAction.OnSubTaskReorderMove -> {
                reorderInFlight = true
                _state.update {
                    it.withSubTaskMoved(action.taskId, action.fromSubTaskId, action.toSubTaskId)
                }
            }

            is ProjectDetailAction.OnSubTaskReorderCommit -> {
                commitSubTaskReorder(action.taskId)
            }

            ProjectDetailAction.OnSubTaskReorderCancel -> {
                reorderInFlight = false
                reloadTasksFromDatabase()
            }

            ProjectDetailAction.OnToggleColorPicker -> {
                toggleColorPicker()
            }

            is ProjectDetailAction.OnColorChanged -> {
                onColorChanged(action.color)
            }

            is ProjectDetailAction.OnUseLightTextColorToggled -> {
                onUseLightTextColorToggled(action.useLightTextColor)
            }

            ProjectDetailAction.OnExportMenuClick -> {
                export.onExportMenuClick()
            }

            ProjectDetailAction.OnExportMenuDismiss -> {
                export.onExportMenuDismiss()
            }

            is ProjectDetailAction.OnExportFormatClick -> {
                export.onExportFormatClick(action.format)
            }

            is ProjectDetailAction.OnPdfRendered -> {
                export.onPdfRendered(action.bytes)
            }

            ProjectDetailAction.OnPdfRenderFailed -> {
                export.onPdfRenderFailed()
            }

            else -> {
                Unit
            }
        }
    }

    // Timer Implementation

    private fun updateUiWithTimerValues(activeTimersMap: Map<String, TimerState>) {
        _state.update { currentState ->
            val currentProject = currentState.project ?: return@update currentState

            // Efficiently update only the sessions that are running
            val updatedTasks =
                currentProject.projectTasks?.map { task ->
                    val timerState = activeTimersMap[task.projectTaskId]

                    // Subtasks are keyed into the same map by their own id, so they get the same
                    // live-value / keep-static-value treatment as their parent.
                    val updatedSubTasks =
                        task.subTasks.map { subTask ->
                            val subTaskTimerState = activeTimersMap[subTask.projectSubTaskId]
                            if (subTaskTimerState != null && subTaskTimerState.isRunning) {
                                subTask.copy(
                                    durationMillis = subTaskTimerState.totalDuration.inWholeMilliseconds,
                                    isTimerRunning = true,
                                    isForeign = subTaskTimerState.isForeign,
                                )
                            } else {
                                subTask.copy(isTimerRunning = false, isForeign = false)
                            }
                        }

                    // A task with subtasks is timed only through them: it runs while any of them runs,
                    // and its displayed duration is their sum (ProjectTaskUi.displayDuration), so it
                    // deliberately gets no TimeManager entry of its own — one would double-count.
                    if (task.subTasks.isNotEmpty()) {
                        task.copy(
                            isTimerRunning = updatedSubTasks.any { it.isTimerRunning },
                            // A task timed through its subtasks inherits their provenance.
                            isForeign = updatedSubTasks.any { it.isTimerRunning && it.isForeign },
                            subTasks = updatedSubTasks,
                        )
                    } else if (timerState != null && timerState.isRunning) {
                        // CASE: Running - Use the live value
                        task.copy(
                            durationMillis = timerState.totalDuration.inWholeMilliseconds,
                            isTimerRunning = timerState.isRunning,
                            isForeign = timerState.isForeign,
                            subTasks = updatedSubTasks,
                        )
                    } else {
                        // CASE: Not Running - Keep static DB value
                        // This prevents "flickering" back to 0s if the timer stops
                        task.copy(
                            isTimerRunning = false,
                            isForeign = false,
                            subTasks = updatedSubTasks,
                        )
                    }
                }

            // At most one timer runs per user, so the project's provenance is whichever entry in
            // the map is running -- there is never more than one to disagree with.
            val live = activeTimersMap.values.firstOrNull { it.isRunning }
            currentState.copy(
                project = currentProject.copy(projectTasks = updatedTasks),
                isRunningTimerForeign = live?.isForeign == true,
                isRunningTimerStale = live?.isStale == true,
            )
        }
    }

    private fun onToggleTimer(taskId: String) {
        val session =
            _state.value.project
                ?.projectTasks
                ?.find { it.projectTaskId == taskId }
        if (session == null) return

        // A task with subtasks is never timed directly — its button is a shortcut onto whichever
        // subtask is active, so no parent-only interval is ever opened and the summed duration the
        // card shows stays exact.
        if (session.subTasks.isNotEmpty()) {
            toggleActiveSubTask(session)
            return
        }

        // The rendered flag, which is the database's answer: TimeManager derives it from the open
        // interval, so the card's play/pause icon and this branch cannot disagree. Starting and
        // stopping is only a repository write now - the clock follows the row.
        if (session.isTimerRunning) {
            viewModelScope.launch {
                projectTaskRepository.stopProjectTask(taskId)
                refreshPerDayStrip()
            }
        } else {
            viewModelScope.launch {
                projectTaskRepository.startProjectTask(taskId)
            }
        }
    }

    /**
     * Resolves which subtask the parent's button acts on: the running one if there is one, else the
     * one used most recently, else the first still unfinished. A task whose subtasks are all
     * finished has nothing to resume.
     */
    private fun toggleActiveSubTask(task: ProjectTaskUi) {
        task.subTasks.find { it.isTimerRunning }?.let {
            onToggleSubTaskTimer(it.projectSubTaskId)
            return
        }

        viewModelScope.launch {
            // Unreadable history is treated as no history: the first unfinished subtask starts.
            val lastStartedId = subTaskRepository.lastStartedSubTaskId(task.projectTaskId).getOrDefault(null)
            val target =
                task.subTasks.find { it.projectSubTaskId == lastStartedId && !it.isFinished }
                    ?: task.subTasks.firstOrNull { !it.isFinished }
                    ?: return@launch
            onToggleSubTaskTimer(target.projectSubTaskId)
        }
    }
    // End New Timer Implementation

    private fun createProjectTask(projectTaskTitle: String) {
        viewModelScope.launch {
            val currentProject = state.value.project ?: return@launch

            val newProjectTask =
                ProjectTask(
                    projectTaskId = Uuid.random().toString(),
                    title = projectTaskTitle,
                    description = null,
                    durationMillis = 0L,
                    startDateTimeUtc = timeProvider.nowInstant,
                    endDateTimeUtc = null,
                    parentProjectId = currentProject.projectId,
                    isTimerRunning = false,
                )

            when (val result = projectTaskRepository.upsertProjectTask(newProjectTask)) {
                is Result.Error -> {
                    // Close the sheet here rather than letting the screen react to the error event:
                    // this ViewModel owns the flag, and a generic "on any error" toggle out there
                    // would fire for errors that have nothing to do with adding a task.
                    _state.update { it.copy(isAddNewProjectTaskBottomSheetVisible = false) }
                    eventChannel.send(ProjectDetailEvent.Error(result.error.toUiText()))
                }

                is Result.Success -> {
                    _state.update {
                        it.copy(
                            isAddNewProjectTaskBottomSheetVisible = false,
                            addProjectTaskTextFieldState = TextFieldState(),
                            project =
                                currentProject.copy(
                                    projectTasks = currentProject.projectTasks?.plus(newProjectTask.toProjectTaskUi()),
                                ),
                        )
                    }
                    eventChannel.send(ProjectDetailEvent.NewProjectSessionSaved(projectTaskTitle))
                }
            }
        }
    }

    private fun toggleAddNewProjectSessionBottomSheet() {
        _state.update {
            it.copy(
                isAddNewProjectTaskBottomSheetVisible = !it.isAddNewProjectTaskBottomSheetVisible,
            )
        }
    }

    private fun getProject(projectId: String) {
        viewModelScope.launch(ioDispatcher) {
            val newProject =
                when (val result = projectRepository.getProjectWithTasksByProjectId(projectId)) {
                    is Result.Success -> {
                        result.data
                    }

                    is Result.Error -> {
                        eventChannel.send(ProjectDetailEvent.Error(result.error.toUiText()))
                        return@launch
                    }
                }
            val color = if (newProject?.colorArgb != null) Color(newProject.colorArgb) else null
            _state.update {
                it.copy(
                    project = newProject?.toProjectUi(),
                    titleText = newProject?.title.orEmpty(),
                    descriptionText = newProject?.description.orEmpty(),
                    projectColor = color,
                    selectedColorHex = color?.toHex() ?: "#00FFFF",
                    useLightTextColor = newProject?.useLightTextColor ?: true,
                    // Built from the domain project, before toProjectUi() drops the intervals it needs.
                    perDayStrip = newProject?.perDayStrip(),
                )
            }
        }
    }

    /**
     * Rebuilds only the per-day strip from a fresh read of the project.
     *
     * getProject runs once, from onStart, and the strip is derived from intervals that
     * ProjectTaskUi does not carry — so there is nothing in state to patch incrementally the way
     * the task tree is patched. Without this the strip would go stale the moment the user banked
     * any time without leaving the screen. Called after the timer stops, which is when an interval
     * gains its duration.
     */
    private suspend fun refreshPerDayStrip() {
        val projectId = projectId ?: return
        // A failed read keeps the strip already on screen rather than blanking it.
        val project = projectRepository.getProjectWithTasksByProjectId(projectId) as? Result.Success ?: return
        _state.update { it.copy(perDayStrip = project.data?.perDayStrip()) }
    }

    /** The zone the strip buckets its intervals by. */
    private fun Project.perDayStrip(): PerDayStripUi? = toPerDayStripUi(timeZone = TimeZone.currentSystemDefault())

    private fun saveProjectDetails() {
        // Only the colour and text contrast are edited here; title and description belong to the
        // edit-text screen and are left as the stored row has them.
        val current = state.value
        val newColorArgb = current.projectColor?.toArgb()
        val useLightTextColor = current.useLightTextColor
        val projectId = projectId ?: return

        viewModelScope.launch {
            // Start from the stored row, not the UI model. Rebuilding the project from ProjectUi used
            // to drop what the UI does not carry, above all sortIndex: a null index sorts first under
            // Custom, so leaving edit mode after a task reorder moved the project on the overview.
            val stored =
                when (val result = projectRepository.getProjectById(projectId)) {
                    is Result.Success -> {
                        result.data ?: return@launch
                    }

                    is Result.Error -> {
                        eventChannel.send(ProjectDetailEvent.Error(result.error.toUiText()))
                        return@launch
                    }
                }
            val edited =
                stored.copy(
                    colorArgb = newColorArgb,
                    useLightTextColor = useLightTextColor,
                )
            // A session that only reordered tasks changed nothing here; writing anyway would stamp
            // the project as modified and push it for no reason.
            if (edited != stored) projectRepository.upsertProject(edited)
        }
        _state.update {
            it.copy(
                isEditMode = false,
            )
        }
    }

    private fun toggleColorPicker() {
        _state.update {
            it.copy(
                isColorPickerVisible = !it.isColorPickerVisible,
            )
        }
    }

    private fun onColorChanged(color: Color) {
        _state.update {
            it.copy(
                projectColor = color,
                selectedColorHex = color.toHex(),
                isColorPickerVisible = false,
            )
        }
    }

    private fun onUseLightTextColorToggled(useLightTextColor: Boolean) {
        _state.update { it.copy(useLightTextColor = useLightTextColor) }
    }

    private fun deleteProjectTask(taskId: String) {
        viewModelScope.launch {
            // The task routes are nested under the project, so the delete needs both ids.
            val parentProjectId = _state.value.project?.projectId ?: return@launch
            projectTaskRepository.deleteProjectTask(parentProjectId, taskId)
            _state.update { currentState ->
                val currentProject = currentState.project ?: return@update currentState
                val updatedTasks = currentProject.projectTasks?.filter { it.projectTaskId != taskId }
                currentState.copy(
                    project = currentProject.copy(projectTasks = updatedTasks),
                )
            }
        }
    }

    /**
     * Mirrors [onToggleTimer], but the repository call also opens or closes the parent task's
     * interval, so nothing here touches the task's timer directly. [TimeManager] is keyed by an
     * opaque id, so subtask ids share the same map as task ids.
     */
    private fun onToggleSubTaskTimer(subTaskId: String) {
        val parentTask =
            _state.value.project
                ?.projectTasks
                ?.find { task -> task.subTasks.any { it.projectSubTaskId == subTaskId } } ?: return
        val subTask = parentTask.subTasks.first { it.projectSubTaskId == subTaskId }

        // Same flag the parent branches on, for the same reason.
        if (subTask.isTimerRunning) {
            viewModelScope.launch {
                subTaskRepository.stopSubTask(subTaskId)
                refreshPerDayStrip()
            }
        } else {
            // Only one subtask per task may run, and startSubTask closes a running sibling's
            // interval. Nothing else has to be told: closing the row is what stops its clock.
            viewModelScope.launch {
                subTaskRepository.startSubTask(subTaskId)
            }
        }
    }

    private fun deleteSubTask(subTaskId: String) {
        val parentTaskId =
            _state.value.project
                ?.projectTasks
                ?.find { task -> task.subTasks.any { it.projectSubTaskId == subTaskId } }
                ?.projectTaskId

        viewModelScope.launch {
            subTaskRepository.deleteSubTask(subTaskId)
            _state.update { currentState ->
                val currentProject = currentState.project ?: return@update currentState
                val updatedTasks =
                    currentProject.projectTasks?.map { task ->
                        task.copy(
                            subTasks = task.subTasks.filter { it.projectSubTaskId != subTaskId },
                        )
                    }
                currentState.copy(project = currentProject.copy(projectTasks = updatedTasks))
            }

            // Deleting the last open subtask leaves the rest finished, so the parent is too.
            if (parentTaskId != null) taskCompletion.syncTaskFinishedFromSubTasks(parentTaskId)
        }
    }

    private fun toggleTaskExpanded(taskId: String) {
        _state.update { currentState ->
            val collapsed = currentState.collapsedTaskIds
            currentState.copy(
                collapsedTaskIds = if (taskId in collapsed) collapsed - taskId else collapsed + taskId,
            )
        }
    }

    /**
     * Writes the order the drag settled on, and releases the in-flight guard once it is written —
     * after that the database is authoritative again and an arriving tree may be adopted.
     */
    private fun commitTaskReorder() {
        val projectId = projectId ?: return
        val ordered =
            _state.value.project
                ?.projectTasks
                ?.map { it.projectTaskId } ?: return
        viewModelScope.launch {
            projectTaskRepository
                .reorderTasks(projectId, ordered)
                .onFailure { error ->
                    // Don't leave the user looking at an order that says it saved while the
                    // snackbar says it didn't: fall back to what is actually persisted.
                    reloadTasksFromDatabase()
                    eventChannel.send(ProjectDetailEvent.ReorderError(error.toUiText()))
                }
            reorderInFlight = false
        }
    }

    /** The subtask twin of [commitTaskReorder], scoped to the card the drag happened in. */
    private fun commitSubTaskReorder(taskId: String) {
        val task =
            _state.value.project
                ?.projectTasks
                ?.find { it.projectTaskId == taskId } ?: return
        val ordered = task.subTasks.map { it.projectSubTaskId }
        viewModelScope.launch {
            subTaskRepository
                .reorderSubTasks(taskId, ordered)
                .onFailure { error ->
                    reloadTasksFromDatabase()
                    eventChannel.send(ProjectDetailEvent.ReorderError(error.toUiText()))
                }
            reorderInFlight = false
        }
    }

    /**
     * Keeps the task tree level with the database, whoever wrote to it — the edit-text screen, an
     * optimistic write of our own, or a sync pull carrying another device's rows.
     *
     * Replaces only the tasks and the per-day strip: the colour, contrast and edit mode are left
     * alone, so a colour pick the user has not saved yet survives an arrival.
     */
    private fun observeTaskTree(projectId: String) {
        viewModelScope.launch {
            projectRepository.observeProjectWithTaskTreeById(projectId).collect { fresh ->
                if (fresh == null) return@collect
                // A drag is the one time the order on screen outranks the order in the database.
                if (reorderInFlight) return@collect

                val freshTasks = fresh.toProjectUi().projectTasks
                _state.update { current ->
                    val project = current.project ?: return@update current
                    current.copy(
                        project = project.copy(projectTasks = freshTasks),
                        perDayStrip = fresh.perDayStrip(),
                    )
                }
                // The tree carries what the row banked; the ticker carries what is accruing now.
                // Re-applying the overlay here is what lets the tree be streamed at all: without
                // it every arrival would snap a running timer back to its stored value.
                updateUiWithTimerValues(timeManager.taskStates.value)

                // A subtask created elsewhere is open, so a finished parent re-opens - the escape
                // route out of the uncheck-blocked dialog that the inline draft row used to provide.
                freshTasks?.forEach { taskCompletion.syncTaskFinishedFromSubTasks(it.projectTaskId) }
            }
        }
    }

    /** Re-reads the task tree, discarding any preview order the screen is still showing. */
    private fun reloadTasksFromDatabase() {
        val projectId = projectId ?: return
        getProject(projectId)
    }

    private fun toggleEditMode() {
        _state.update {
            it.copy(
                isEditMode = !it.isEditMode,
            )
        }
    }

    private fun discardChangesAndExitEditMode() {
        _state.update {
            it.copy(
                isEditMode = false,
                titleText = it.project?.title,
                descriptionText = it.project?.description,
                projectColor = it.project?.color,
                useLightTextColor = it.project?.useLightTextColor ?: false,
            )
        }
    }
}

private fun Color.toHex(): String {
    val redHex = (red * 255).toInt().toString(16).padStart(2, '0')
    val greenHex = (green * 255).toInt().toString(16).padStart(2, '0')
    val blueHex = (blue * 255).toInt().toString(16).padStart(2, '0')
    return "#$redHex$greenHex$blueHex".uppercase()
}

/**
 * Folds the live project row over the locally held state. Only the fields the row owns are
 * rewritten: the loaded task tree and the running timer values live in the state itself and are
 * left alone, so a row change never disturbs them.
 */
private fun ProjectDetailState.withProjectRow(row: Project?): ProjectDetailState {
    if (row == null) return this
    val color = row.colorArgb?.let { Color(it) }
    return copy(
        titleText = row.title,
        descriptionText = row.description.orEmpty(),
        // copy, not replace: the row carries no tasks.
        project = project?.copy(title = row.title, description = row.description),
        // Edit mode owns the colour picker's uncommitted selection, so a row change arriving
        // mid-edit must not overwrite a pick the user has not saved yet.
        projectColor = if (isEditMode) projectColor else color,
        selectedColorHex = if (isEditMode) selectedColorHex else color?.toHex() ?: "#00FFFF",
        useLightTextColor = if (isEditMode) useLightTextColor else row.useLightTextColor,
    )
}

/** Rewrites one task in the loaded project, leaving the rest of the state untouched. */
internal fun ProjectDetailState.mapTask(
    taskId: String,
    transform: (ProjectTaskUi) -> ProjectTaskUi,
): ProjectDetailState {
    val project = this.project ?: return this
    return copy(
        project =
            project.copy(
                projectTasks =
                    project.projectTasks?.map { task ->
                        if (task.projectTaskId == taskId) transform(task) else task
                    },
            ),
    )
}

/**
 * Moves one task into another's slot, leaving every other field alone.
 *
 * A move that names an id the list does not hold is a no-op rather than an error: the drag state
 * hit-tests against what is on screen, which can lag a delete arriving from a sync pull.
 */
private fun ProjectDetailState.withTaskMoved(fromTaskId: String, toTaskId: String): ProjectDetailState {
    val project = this.project ?: return this
    val tasks = project.projectTasks ?: return this

    val from = tasks.indexOfFirst { it.projectTaskId == fromTaskId }
    val to = tasks.indexOfFirst { it.projectTaskId == toTaskId }
    if (from == -1 || to == -1 || from == to) return this

    val moved = tasks.toMutableList().apply { add(to, removeAt(from)) }
    return copy(project = project.copy(projectTasks = moved))
}

/**
 * Moves one subtask into a sibling's slot inside [taskId], leaving every other task alone.
 *
 * Reuses [mapTask], so a drag can only ever rewrite the card it started in — a subtask is never
 * re-parented, and an id belonging to another task simply finds no match here.
 */
private fun ProjectDetailState.withSubTaskMoved(
    taskId: String,
    fromSubTaskId: String,
    toSubTaskId: String,
): ProjectDetailState =
    mapTask(taskId) { task ->
        val from = task.subTasks.indexOfFirst { it.projectSubTaskId == fromSubTaskId }
        val to = task.subTasks.indexOfFirst { it.projectSubTaskId == toSubTaskId }
        if (from == -1 || to == -1 || from == to) return@mapTask task
        task.copy(subTasks = task.subTasks.toMutableList().apply { add(to, removeAt(from)) })
    }

/** Rewrites every subtask of one task. */
internal fun ProjectDetailState.mapSubTasksOf(
    taskId: String,
    transform: (ProjectSubTaskUi) -> ProjectSubTaskUi,
): ProjectDetailState =
    mapTask(taskId) { task ->
        task.copy(subTasks = task.subTasks.map(transform))
    }

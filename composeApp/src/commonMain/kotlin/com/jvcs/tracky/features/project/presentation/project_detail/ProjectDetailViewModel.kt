@file:OptIn(ExperimentalUuidApi::class)

package com.jvcs.tracky.features.project.presentation.project_detail

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvcs.tracky.features.project.domain.subtask.SubTaskRepository
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.TimeManager
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.core.domain.util.TimerState
import com.jvcs.tracky.features.project.presentation.mappers.toProject
import com.jvcs.tracky.features.project.presentation.models.ProjectTaskUi
import com.jvcs.tracky.features.project.presentation.mappers.toProjectSubTaskUi
import com.jvcs.tracky.features.project.presentation.mappers.toProjectTaskUi
import com.jvcs.tracky.features.project.presentation.mappers.toProjectUi
import com.jvcs.tracky.features.project.presentation.util.toUiText
import com.jvcs.tracky.design_system.util.parseDuration
import com.jvcs.tracky.features.project.domain.project.EditTextType
import com.jvcs.tracky.features.project.domain.project.ProjectRepository
import com.jvcs.tracky.features.project.domain.task.ProjectTaskRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ProjectDetailViewModel(
    private val isEdit: Boolean,
    private val projectId: String?,
    private val projectRepository: ProjectRepository,
    private val projectTaskRepository: ProjectTaskRepository,
    private val subTaskRepository: SubTaskRepository,
    private val timeManager: TimeManager,
    private val timeProvider: TimeProvider,
    // Injectable so tests can drive the initial load on their own scheduler; production keeps IO.
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
): ViewModel() {

    private val _state = MutableStateFlow(ProjectDetailState())

    private val eventChannel = Channel<ProjectDetailEvent>()
    val events = eventChannel.receiveAsFlow()

    private var hasLoadedInitialData = false

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                if (projectId != null) {
                    getProject(projectId)
                } else {
                    _state.update { it.copy(
                        errorMessage = "Project cannot be found"
                    ) }
                }
                hasLoadedInitialData = true
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = _state.value
        )

    init {
        viewModelScope.launch {
            timeManager.taskStates.collect { activeTimersMap ->
                updateUiWithTimerValues(activeTimersMap)

            }
        }
    }

    fun onAction(action: ProjectDetailAction) {
        when(action){
            is ProjectDetailAction.OnSaveClick -> {saveProjectDetails()}
            ProjectDetailAction.OnEditModeClick -> {toggleEditMode()}
            ProjectDetailAction.OnCloseAndCancelClick -> {discardChangesAndExitEditMode()}
            ProjectDetailAction.OnBackClick -> {} // Handled in UI
            is ProjectDetailAction.OnEditTextClick -> {}
            ProjectDetailAction.OnStartTrackerClick -> {}
            ProjectDetailAction.OnStopTrackerClick -> {}
            is ProjectDetailAction.OnEditTextChanged -> {onEditTextChanged(action.value, action.editTextType)}
            is ProjectDetailAction.OnProjectSessionCardClick -> {}
            ProjectDetailAction.OnToggleAddNewProjectSessionBottomSheet -> {toggleAddNewProjectSessionBottomSheet()}
            is ProjectDetailAction.OnCreateProjectSession -> {createProjectTask(action.projectSessionTitle)}
            is ProjectDetailAction.OnToggleSessionTimer -> {onToggleTimer(action.projectSessionId)}
            is ProjectDetailAction.OnDeleteSessionClick -> {deleteProjectTask(action.sessionId)}
            is ProjectDetailAction.OnToggleSubTaskTimer -> {onToggleSubTaskTimer(action.subTaskId)}
            is ProjectDetailAction.OnDeleteSubTaskClick -> {deleteSubTask(action.subTaskId)}
            is ProjectDetailAction.OnSubTaskCheckedChange -> {onSubTaskCheckedChange(action.subTaskId)}
            is ProjectDetailAction.OnToggleTaskExpanded -> {toggleTaskExpanded(action.taskId)}
            is ProjectDetailAction.OnAddSubTaskClick -> {beginAddSubTask(action.taskId)}
            is ProjectDetailAction.OnSubTaskTitleClick -> {beginSubTaskRename(action.subTaskId, action.currentTitle)}
            ProjectDetailAction.OnCommitSubTaskTitle -> {commitSubTaskRename()}
            ProjectDetailAction.OnToggleColorPicker -> {toggleColorPicker()}
            is ProjectDetailAction.OnColorChanged -> {onColorChanged(action.color)}
            is ProjectDetailAction.OnUseLightTextColorToggled -> {onUseLightTextColorToggled(action.useLightTextColor)}
        }
    }

    //Timer Implementation

    private fun updateUiWithTimerValues(activeTimersMap: Map<String, TimerState>) {
        _state.update { currentState ->
            val currentProject = currentState.project ?: return@update currentState

            // Efficiently update only the sessions that are running
            val updatedTasks = currentProject.projectTasks?.map { task ->
                val timerState = activeTimersMap[task.projectTaskId]

                // Subtasks are keyed into the same map by their own id, so they get the same
                // live-value / keep-static-value treatment as their parent.
                val updatedSubTasks = task.subTasks.map { subTask ->
                    val subTaskTimerState = activeTimersMap[subTask.projectSubTaskId]
                    if (subTaskTimerState != null && subTaskTimerState.isRunning) {
                        subTask.copy(
                            formattedDuration = subTaskTimerState.formattedTime,
                            isTimerRunning = true
                        )
                    } else {
                        subTask.copy(isTimerRunning = false)
                    }
                }

                // A task with subtasks is timed only through them: it runs while any of them runs,
                // and its displayed duration is their sum (ProjectTaskUi.displayDuration), so it
                // deliberately gets no TimeManager entry of its own — one would double-count.
                if (task.subTasks.isNotEmpty()) {
                    task.copy(
                        isTimerRunning = updatedSubTasks.any { it.isTimerRunning },
                        subTasks = updatedSubTasks
                    )
                } else if (timerState != null && timerState.isRunning) {
                    // CASE: Running - Use the live value
                    task.copy(
                        formattedDuration = timerState.formattedTime,
                        isTimerRunning = timerState.isRunning,
                        subTasks = updatedSubTasks
                    )
                } else {
                    // CASE: Not Running - Keep static DB value
                    // This prevents "flickering" back to 0s if the timer stops
                    task.copy(
                        isTimerRunning = false,
                        subTasks = updatedSubTasks
                    )
                }
            }

            currentState.copy(
                project = currentProject.copy(projectTasks = updatedTasks)
            )
        }
    }

    private fun onToggleTimer(taskId: String) {

        val session = _state.value.project?.projectTasks?.find { it.projectTaskId == taskId }
        if(session == null) return

        // A task with subtasks is never timed directly — its button is a shortcut onto whichever
        // subtask is active, so no parent-only interval is ever opened and the summed duration the
        // card shows stays exact.
        if (session.subTasks.isNotEmpty()) {
            toggleActiveSubTask(session)
            return
        }

        val currentDuration = parseDuration( timeString = session.formattedDuration)

        val timerState = timeManager.taskStates.value[taskId]

        if (timerState != null && timerState.isRunning) {
            viewModelScope.launch {
                projectTaskRepository.stopProjectTask(taskId)
                timeManager.stopAndResetTimer(taskId)
            }
        } else {
            viewModelScope.launch {
                projectTaskRepository.startProjectTask(taskId)
                timeManager.toggleTimer(taskId, currentDuration)
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
            val lastStartedId = subTaskRepository.lastStartedSubTaskId(task.projectTaskId)
            val target = task.subTasks.find { it.projectSubTaskId == lastStartedId && !it.isFinished }
                ?: task.subTasks.firstOrNull { !it.isFinished }
                ?: return@launch
            onToggleSubTaskTimer(target.projectSubTaskId)
        }
    }
    //End New Timer Implementation

    private fun createProjectTask(projectTaskTitle: String) {
        viewModelScope.launch {
            val currentProject = state.value.project ?: return@launch

            val newProjectTask = ProjectTask(
                projectTaskId = Uuid.random().toString(),
                title = projectTaskTitle,
                description = null,
                durationMillis = 0L,
                startDateTimeUtc = timeProvider.nowInstant,
                endDateTimeUtc = null,
                parentProjectId = currentProject.projectId,
                isTimerRunning = false
            )

            when(val result = projectTaskRepository.upsertProjectTask(newProjectTask)) {
                is Result.Error -> {
                    // Close the sheet here rather than letting the screen react to the error event:
                    // this ViewModel owns the flag, and a generic "on any error" toggle out there
                    // would fire for errors that have nothing to do with adding a task.
                    _state.update { it.copy(isAddNewProjectTaskBottomSheetVisible = false) }
                    eventChannel.send(ProjectDetailEvent.Error(result.error.toUiText()))
                }
                is Result.Success-> {
                    _state.update { it.copy(
                        isAddNewProjectTaskBottomSheetVisible = false,
                        addProjectTaskTextFieldState = TextFieldState(),
                        project = currentProject.copy(
                            projectTasks = currentProject.projectTasks?.plus(newProjectTask.toProjectTaskUi())
                        )
                    ) }
                    eventChannel.send(ProjectDetailEvent.NewProjectSessionSaved(projectTaskTitle))
                }
            }
        }
    }


    private fun toggleAddNewProjectSessionBottomSheet() {
        _state.update { it.copy(
            isAddNewProjectTaskBottomSheetVisible = !it.isAddNewProjectTaskBottomSheetVisible
        ) }
    }

    private fun getProject(projectId: String) {
        viewModelScope.launch(ioDispatcher) {
            val newProject = projectRepository.getProjectWithTasksByProjectId(projectId)
            val color = if (newProject?.colorArgb != null) Color(newProject.colorArgb) else null
            _state.update { it.copy(
                project = newProject?.toProjectUi(),
                titleText = newProject?.title ?: "",
                descriptionText = newProject?.description ?:"",
                selectedColor = color,
                selectedColorHex = color?.toHex() ?: "#00FFFF",
                useLightTextColor = newProject?.useLightTextColor ?: true
            ) }
        }
    }

    private fun saveProjectDetails(){
        val newTitle = _state.value.titleText.toString()
        val newDescription = _state.value.descriptionText.toString()
        val newColor = _state.value.selectedColor
        val useLightTextColor = _state.value.useLightTextColor

        val newProject = _state.value.project?.copy(
            title = newTitle,
            description = newDescription,
            color = newColor,
            useLightTextColor = useLightTextColor
        )!!.toProject()

        viewModelScope.launch {
            projectRepository.upsertProject(newProject)
        }
        _state.update { it.copy(
            isEditMode = false
        ) }
    }

    private fun toggleColorPicker() {
        _state.update { it.copy(
            isColorPickerVisible = !it.isColorPickerVisible
        ) }
    }

    private fun onColorChanged(color: Color) {
        _state.update { it.copy(
            selectedColor = color,
            selectedColorHex = color.toHex(),
            isColorPickerVisible = false
        ) }
    }

    private fun Color.toHex(): String {
        val r = (red * 255).toInt().toString(16).padStart(2, '0')
        val g = (green * 255).toInt().toString(16).padStart(2, '0')
        val b = (blue * 255).toInt().toString(16).padStart(2, '0')
        return "#$r$g$b".uppercase()
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
                    project = currentProject.copy(projectTasks = updatedTasks)
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
        val parentTask = _state.value.project
            ?.projectTasks
            ?.find { task -> task.subTasks.any { it.projectSubTaskId == subTaskId } } ?: return
        val subTask = parentTask.subTasks.first { it.projectSubTaskId == subTaskId }

        val currentDuration = parseDuration(timeString = subTask.formattedDuration)
        val timerState = timeManager.taskStates.value[subTaskId]

        if (timerState != null && timerState.isRunning) {
            viewModelScope.launch {
                subTaskRepository.stopSubTask(subTaskId)
                timeManager.stopAndResetTimer(subTaskId)
            }
        } else {
            // Only one subtask per task may run: startSubTask closes a running sibling's interval
            // in the database, but TimeManager would keep ticking it — two rows would look live and
            // the parent's summed duration would climb twice as fast.
            parentTask.subTasks
                .filter { it.projectSubTaskId != subTaskId && it.isTimerRunning }
                .forEach { timeManager.stopAndResetTimer(it.projectSubTaskId) }

            viewModelScope.launch {
                subTaskRepository.startSubTask(subTaskId)
                timeManager.toggleTimer(subTaskId, currentDuration)
            }
        }
    }

    private fun deleteSubTask(subTaskId: String) {
        viewModelScope.launch {
            subTaskRepository.deleteSubTask(subTaskId)
            _state.update { currentState ->
                val currentProject = currentState.project ?: return@update currentState
                val updatedTasks = currentProject.projectTasks?.map { task ->
                    task.copy(
                        subTasks = task.subTasks.filter { it.projectSubTaskId != subTaskId }
                    )
                }
                currentState.copy(project = currentProject.copy(projectTasks = updatedTasks))
            }
        }
    }

    /**
     * Flips the subtask's finished flag. Finishing also stops a running timer — a done subtask
     * must not keep counting — which the repository turns into a closed interval.
     *
     * There is no task-level equivalent yet: `OnCheckedChange` for a task is still an unwired stub
     * at the call site, so finishing currently works on subtasks only.
     */
    private fun onSubTaskCheckedChange(subTaskId: String) {
        val parentTaskId = _state.value.project
            ?.projectTasks
            ?.find { task -> task.subTasks.any { it.projectSubTaskId == subTaskId } }
            ?.projectTaskId ?: return

        viewModelScope.launch {
            val subTask = subTaskRepository.getSubTasksForTask(parentTaskId).first()
                .find { it.projectSubTaskId == subTaskId } ?: return@launch

            val nowFinished = !subTask.isFinished
            if (nowFinished && subTask.isTimerRunning) {
                subTaskRepository.stopSubTask(subTaskId)
                timeManager.stopAndResetTimer(subTaskId)
            }

            subTaskRepository.upsertSubTask(
                subTask.copy(
                    isFinished = nowFinished,
                    endDateTimeUtc = if (nowFinished) timeProvider.nowInstant else null,
                    ownUpdatedAt = timeProvider.nowInstant
                )
            )
        }
    }

    private fun toggleTaskExpanded(taskId: String) {
        _state.update { currentState ->
            val collapsed = currentState.collapsedTaskIds
            currentState.copy(
                collapsedTaskIds = if (taskId in collapsed) collapsed - taskId else collapsed + taskId
            )
        }
    }

    /**
     * Opens an inline draft row instead of writing anything. The server rejects a blank title
     * (CreateProjectSubTaskRequest.title is @NotBlank), so a subtask must not reach the repository
     * until the user has actually typed one — and abandoning the draft then leaves nothing behind.
     */
    private fun beginAddSubTask(taskId: String) {
        _state.update { it.copy(
            // The draft row lives at the end of the subtask list, so the list has to be showing.
            collapsedTaskIds = it.collapsedTaskIds - taskId,
            pendingSubTaskParentTaskId = taskId,
            editingSubTaskId = null,
            editSubTaskTextFieldState = TextFieldState()
        ) }
    }

    private fun createSubTask(taskId: String, title: String) {
        viewModelScope.launch {
            val currentProject = state.value.project ?: return@launch

            val newSubTask = ProjectSubTask(
                projectSubTaskId = Uuid.random().toString(),
                parentProjectTaskId = taskId,
                parentProjectId = currentProject.projectId,
                title = title,
                description = null,
                durationMillis = 0L,
                isTimerRunning = false,
                startDateTimeUtc = timeProvider.nowInstant,
                ownUpdatedAt = timeProvider.nowInstant
            )

            when (val result = subTaskRepository.upsertSubTask(newSubTask)) {
                is Result.Error -> eventChannel.send(ProjectDetailEvent.Error(result.error.toUiText()))
                is Result.Success -> _state.update { currentState ->
                    val project = currentState.project ?: return@update currentState
                    currentState.copy(
                        project = project.copy(
                            projectTasks = project.projectTasks?.map { task ->
                                if (task.projectTaskId == taskId) {
                                    task.copy(subTasks = task.subTasks + newSubTask.toProjectSubTaskUi())
                                } else {
                                    task
                                }
                            }
                        )
                    )
                }
            }
        }
    }

    private fun beginSubTaskRename(subTaskId: String, currentTitle: String) {
        _state.update { it.copy(
            editingSubTaskId = subTaskId,
            editSubTaskTextFieldState = TextFieldState(initialText = currentTitle)
        ) }
    }

    private fun commitSubTaskRename() {
        val current = _state.value
        val newTitle = current.editSubTaskTextFieldState.text.toString().trim()
        val pendingParentTaskId = current.pendingSubTaskParentTaskId
        val subTaskId = current.editingSubTaskId

        // Close the field first so the UI settles even if the write fails.
        _state.update { it.copy(
            pendingSubTaskParentTaskId = null,
            editingSubTaskId = null,
            editSubTaskTextFieldState = TextFieldState()
        ) }

        // An abandoned draft is simply dropped, and a rename to blank is refused: either way the
        // server would reject it, so nothing is sent.
        if (newTitle.isEmpty()) return

        if (pendingParentTaskId != null) {
            createSubTask(pendingParentTaskId, newTitle)
            return
        }
        if (subTaskId == null) return

        val parentTaskId = current.project
            ?.projectTasks
            ?.find { task -> task.subTasks.any { it.projectSubTaskId == subTaskId } }
            ?.projectTaskId ?: return

        viewModelScope.launch {
            val subTask = subTaskRepository.getSubTasksForTask(parentTaskId).first()
                .find { it.projectSubTaskId == subTaskId } ?: return@launch
            if (subTask.title == newTitle) return@launch

            subTaskRepository.upsertSubTask(
                subTask.copy(title = newTitle, ownUpdatedAt = timeProvider.nowInstant)
            )
            _state.update { currentState ->
                val project = currentState.project ?: return@update currentState
                currentState.copy(
                    project = project.copy(
                        projectTasks = project.projectTasks?.map { task ->
                            task.copy(subTasks = task.subTasks.map { ui ->
                                if (ui.projectSubTaskId == subTaskId) ui.copy(title = newTitle) else ui
                            })
                        }
                    )
                )
            }
        }
    }

    private fun onEditTextChanged(value: String, editTextType: EditTextType) {
        when(editTextType) {
            EditTextType.TITLE ->
                _state.update { it.copy(titleText = value) }
            EditTextType.DESCRIPTION ->
                _state.update { it.copy(descriptionText = value) }
        }
    }

    private fun toggleEditMode() {
        _state.update { it.copy(
            isEditMode = !it.isEditMode
        ) }
    }

    private fun discardChangesAndExitEditMode() {
        _state.update { it.copy(
            isEditMode = false,
            titleText = it.project?.title,
            descriptionText = it.project?.description,
            selectedColor = it.project?.color,
            useLightTextColor = it.project?.useLightTextColor ?: false
        ) }
    }
}
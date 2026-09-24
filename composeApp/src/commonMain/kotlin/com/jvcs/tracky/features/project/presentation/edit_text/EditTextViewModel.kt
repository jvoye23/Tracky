@file:OptIn(ExperimentalUuidApi::class)

package com.jvcs.tracky.features.project.presentation.edit_text

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.designsystem.util.UiText
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.project.ProjectRepository
import com.jvcs.tracky.features.project.domain.subtask.SubTaskRepository
import com.jvcs.tracky.features.project.domain.task.ProjectTaskRepository
import com.jvcs.tracky.features.project.presentation.mappers.toProjectUi
import com.jvcs.tracky.features.project.presentation.util.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.project_cannot_be_found
import tracky.composeapp.generated.resources.project_title_cannot_be_blank
import tracky.composeapp.generated.resources.subtask_cannot_be_found
import tracky.composeapp.generated.resources.subtask_title_cannot_be_blank
import tracky.composeapp.generated.resources.task_cannot_be_found
import tracky.composeapp.generated.resources.task_title_cannot_be_blank
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Edits the title and description of whatever [target] names: the project itself, one of its
 * tasks, one of a task's subtasks, or a subtask that does not exist yet.
 *
 * [taskId] is the task being edited for [EditTextTarget.TASK], and the parent task for both
 * subtask targets. [subTaskId] is only set for [EditTextTarget.SUBTASK].
 */
class EditTextViewModel(
    private val isEditMode: Boolean,
    private val projectId: String,
    private val target: EditTextTarget,
    private val taskId: String?,
    private val subTaskId: String?,
    private val projectRepository: ProjectRepository,
    private val projectTaskRepository: ProjectTaskRepository,
    private val subTaskRepository: SubTaskRepository,
    private val timeProvider: TimeProvider,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val eventChannel = Channel<EditTextEvent>()
    val events = eventChannel.receiveAsFlow()

    private var hasLoadedInitialData = false

    // Drawn once, so repeated saves of a new subtask all land on the same row instead of creating
    // one subtask per tap.
    private val newSubTaskId = Uuid.random().toString()

    // Read once, at construction: the mirroring collectors below write the current (possibly empty)
    // text straight away, so a live handle read could no longer tell a restored draft from a
    // ViewModel that has simply not loaded its project yet.
    private val restoredTitle: String? = savedStateHandle[KEY_TITLE]
    private val restoredDescription: String? = savedStateHandle[KEY_DESCRIPTION]

    private val restoredEditMode: Boolean? = savedStateHandle[KEY_IS_EDIT_MODE]

    // Owned by the ViewModel and never replaced, so the text (and the focus and undo history that
    // hang off it) survives every state update.
    private val titleState =
        TextFieldState(
            initialText = restoredTitle ?: "",
            initialSelection = TextRange(restoredTitle?.length ?: 0),
        )
    private val descriptionState =
        TextFieldState(
            initialText = restoredDescription ?: "",
            initialSelection = TextRange(restoredDescription?.length ?: 0),
        )

    private val editModeState = restoredEditMode ?: isEditMode

    private val _state =
        MutableStateFlow(
            EditTextState(
                target = target,
                titleState = titleState,
                descriptionState = descriptionState,
                isEditMode = editModeState,
            ),
        )

    val state =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    loadInitialData()
                    observeTitleChanges()
                    observeDescriptionChanges()
                    observeEditModeChanges()
                    hasLoadedInitialData = true
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = _state.value,
            )

    fun onAction(action: EditTextAction) {
        when (action) {
            EditTextAction.OnEditClick -> {
                toggleEditMode()
            }

            EditTextAction.OnSaveClick -> {
                save(
                    title =
                        _state.value.titleState.text
                            .toString(),
                    description =
                        _state.value.descriptionState.text
                            .toString(),
                )
            }

            else -> {
                Unit
            }
        }
    }

    private fun observeTitleChanges() {
        viewModelScope.launch {
            snapshotFlow { titleState.text.toString() }.collect {
                savedStateHandle[KEY_TITLE] = it
            }
        }
    }

    private fun observeDescriptionChanges() {
        viewModelScope.launch {
            snapshotFlow { descriptionState.text.toString() }.collect {
                savedStateHandle[KEY_DESCRIPTION] = it
            }
        }
    }

    private fun observeEditModeChanges() {
        viewModelScope.launch {
            _state
                .map { it.isEditMode }
                .distinctUntilChanged()
                .collect { savedStateHandle[KEY_IS_EDIT_MODE] = it }
        }
    }

    fun toggleEditMode() {
        _state.update { it.copy(isEditMode = !it.isEditMode) }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            // The project is loaded for every target: the top bar is tinted with its colour.
            val project = projectRepository.getProjectById(projectId)
            if (project == null) {
                sendError(Res.string.project_cannot_be_found)
                return@launch
            }
            val projectUi = project.toProjectUi()
            _state.update {
                it.copy(
                    project = projectUi,
                    projectColor = projectUi.color,
                )
            }

            val stored: StoredText =
                when (target) {
                    EditTextTarget.PROJECT -> {
                        StoredText(project.title, project.description)
                    }

                    EditTextTarget.TASK -> {
                        val task =
                            taskId?.let { projectTaskRepository.getProjectTaskWithIntervalsById(it).first() }
                                ?: return@launch sendError(Res.string.task_cannot_be_found)
                        StoredText(task.title, task.description)
                    }

                    EditTextTarget.SUBTASK -> {
                        val subTask =
                            loadStoredSubTask()
                                ?: return@launch sendError(Res.string.subtask_cannot_be_found)
                        StoredText(subTask.title, subTask.description)
                    }

                    // Nothing is stored yet: the fields start empty.
                    EditTextTarget.NEW_SUBTASK -> {
                        return@launch
                    }
                }

            // A draft restored after process death outranks the stored text: it is the newer,
            // unsaved edit the user was still typing.
            if (restoredTitle == null) {
                titleState.setTextAndPlaceCursorAtEnd(stored.title)
            }
            if (restoredDescription == null) {
                descriptionState.setTextAndPlaceCursorAtEnd(stored.description ?: "")
            }
        }
    }

    private suspend fun loadStoredSubTask(): ProjectSubTask? {
        val parentTaskId = taskId ?: return null
        return subTaskRepository
            .getSubTasksForTask(parentTaskId)
            .first()
            .find { it.projectSubTaskId == subTaskId }
    }

    private fun save(title: String, description: String) {
        viewModelScope.launch {
            if (title.isBlank()) {
                sendError(blankTitleError())
                return@launch
            }

            val result =
                when (target) {
                    EditTextTarget.PROJECT -> saveProject(title, description)
                    EditTextTarget.TASK -> saveTask(title, description)
                    EditTextTarget.SUBTASK -> saveSubTask(title, description)
                    EditTextTarget.NEW_SUBTASK -> createSubTask(title, description)
                } ?: return@launch

            when (result) {
                is Result.Error -> {
                    eventChannel.send(EditTextEvent.Error(result.error.toUiText()))
                }

                is Result.Success -> {
                    if (target == EditTextTarget.NEW_SUBTASK) {
                        eventChannel.send(EditTextEvent.NavigateBack)
                        return@launch
                    }
                    // Only leave edit mode once the save actually landed, so a blank title or a
                    // failed upsert keeps the user in the field they still have to correct.
                    toggleEditMode()
                    eventChannel.send(EditTextEvent.OnSavedSuccess)
                }
            }
        }
    }

    // Each save starts from the stored row, not a UI model: the UI models carry no sortIndex, and
    // saving one back would move the row to the top of its manual order.

    private suspend fun saveProject(title: String, description: String): EmptyResult<DataError>? {
        val editedProject =
            projectRepository.getProjectById(projectId)?.copy(
                title = title,
                description = description,
            ) ?: return null
        return projectRepository.upsertProject(editedProject)
    }

    private suspend fun saveTask(title: String, description: String): EmptyResult<DataError>? {
        val taskId = taskId ?: return null
        return projectTaskRepository.updateProjectTaskText(
            taskId = taskId,
            title = title,
            description = description.ifBlank { null },
        )
    }

    private suspend fun saveSubTask(title: String, description: String): EmptyResult<DataError>? {
        val subTask = loadStoredSubTask() ?: return null
        return subTaskRepository.upsertSubTask(
            subTask.copy(
                title = title,
                description = description.ifBlank { null },
                ownUpdatedAt = timeProvider.nowInstant,
            ),
        )
    }

    private suspend fun createSubTask(title: String, description: String): EmptyResult<DataError>? {
        val parentTaskId = taskId ?: return null
        return subTaskRepository.upsertSubTask(
            ProjectSubTask(
                projectSubTaskId = newSubTaskId,
                parentProjectTaskId = parentTaskId,
                parentProjectId = projectId,
                title = title,
                description = description.ifBlank { null },
                durationMillis = 0L,
                isTimerRunning = false,
                startDateTimeUtc = timeProvider.nowInstant,
                ownUpdatedAt = timeProvider.nowInstant,
            ),
        )
    }

    private fun blankTitleError(): StringResource =
        when (target) {
            EditTextTarget.PROJECT -> Res.string.project_title_cannot_be_blank

            EditTextTarget.TASK -> Res.string.task_title_cannot_be_blank

            EditTextTarget.SUBTASK,
            EditTextTarget.NEW_SUBTASK,
            -> Res.string.subtask_title_cannot_be_blank
        }

    private suspend fun sendError(resource: StringResource) {
        eventChannel.send(EditTextEvent.Error(UiText.Resource(resource)))
    }

    private data class StoredText(val title: String, val description: String?)

    internal companion object {
        // The "projectEditText" prefix predates the rename; kept so a draft saved before it still
        // restores.
        const val KEY_TITLE = "projectEditText.title"
        const val KEY_DESCRIPTION = "projectEditText.description"
        const val KEY_IS_EDIT_MODE = "projectEditText.isEditMode"
    }
}

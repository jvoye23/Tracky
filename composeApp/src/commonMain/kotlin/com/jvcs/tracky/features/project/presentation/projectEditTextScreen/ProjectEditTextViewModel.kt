package com.jvcs.tracky.features.project.presentation.projectEditTextScreen

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.design_system.util.UiText
import com.jvcs.tracky.features.project.domain.project.ProjectRepository
import com.jvcs.tracky.features.project.presentation.mappers.toProject
import com.jvcs.tracky.features.project.presentation.mappers.toProjectUi
import com.jvcs.tracky.features.project.presentation.util.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.project_cannot_be_found
import tracky.composeapp.generated.resources.project_title_cannot_be_blank

class ProjectEditTextViewModel(
    private val isEditMode: Boolean,
    private val projectId: String,
    private val projectRepository: ProjectRepository,
    private val savedStateHandle: SavedStateHandle
): ViewModel() {
    private val eventChannel = Channel<ProjectEditTextEvent>()
    val events = eventChannel.receiveAsFlow()

    private var hasLoadedInitialData = false

    // Read once, at construction: the mirroring collectors below write the current (possibly empty)
    // text straight away, so a live handle read could no longer tell a restored draft from a
    // ViewModel that has simply not loaded its project yet.
    private val restoredTitle: String? = savedStateHandle[KEY_TITLE]
    private val restoredDescription: String? = savedStateHandle[KEY_DESCRIPTION]

    // Owned by the ViewModel and never replaced, so the text (and the focus and undo history that
    // hang off it) survives every state update.
    private val titleState = TextFieldState(
        initialText = restoredTitle ?: "",
        initialSelection = TextRange(restoredTitle?.length ?: 0),
    )
    private val descriptionState = TextFieldState(
        initialText = restoredDescription ?: "",
        initialSelection = TextRange(restoredDescription?.length ?: 0),
    )

    private val _state = MutableStateFlow(
        ProjectEditTextState(
            titleState = titleState,
            descriptionState = descriptionState,
            isEditMode = savedStateHandle[KEY_IS_EDIT_MODE] ?: isEditMode,
        )
    )

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                getProject(projectId)
                hasLoadedInitialData = true
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = _state.value
        )

    init {
        // Keep the unsaved draft in the handle so process death can't swallow it.
        viewModelScope.launch {
            snapshotFlow { titleState.text.toString() }.collect { savedStateHandle[KEY_TITLE] = it }
        }
        viewModelScope.launch {
            snapshotFlow { descriptionState.text.toString() }.collect {
                savedStateHandle[KEY_DESCRIPTION] = it
            }
        }
    }

    fun onAction(action: ProjectEditTextAction) {
        when (action) {
            ProjectEditTextAction.OnEditClick -> setEditMode(true)
            ProjectEditTextAction.OnSaveClick -> saveProject(
                title = _state.value.titleState.text.toString(),
                description = _state.value.descriptionState.text.toString(),
            )
            else -> Unit
        }
    }

    private fun setEditMode(isEditMode: Boolean) {
        savedStateHandle[KEY_IS_EDIT_MODE] = isEditMode
        _state.update { it.copy(isEditMode = isEditMode) }
    }

    private fun getProject(projectId: String) {
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId)
            if (project == null) {
                eventChannel.send(ProjectEditTextEvent.Error(UiText.Resource(Res.string.project_cannot_be_found)))
                return@launch
            }
            val projectUi = project.toProjectUi()
            _state.update { it.copy(
                project = projectUi,
                projectColor = projectUi.color,
            ) }
            // A draft restored after process death outranks the stored project: it is the newer,
            // unsaved edit the user was still typing.
            if (restoredTitle == null) {
                titleState.setTextAndPlaceCursorAtEnd(project.title)
            }
            if (restoredDescription == null) {
                descriptionState.setTextAndPlaceCursorAtEnd(project.description ?: "")
            }
        }
    }

    private fun saveProject(title: String, description: String) {
        viewModelScope.launch {
            if (title.isBlank()) {
                eventChannel.send(ProjectEditTextEvent.Error(UiText.Resource(Res.string.project_title_cannot_be_blank)))
                return@launch
            }
            val editedProject = _state.value.project?.copy(
                title = title,
                description = description,
            ) ?: return@launch

            when (val result = projectRepository.upsertProject(editedProject.toProject())) {
                is Result.Error -> eventChannel.send(ProjectEditTextEvent.Error(result.error.toUiText()))
                is Result.Success -> {
                    // Only leave edit mode once the save actually landed, so a blank title or a
                    // failed upsert keeps the user in the field they still have to correct.
                    setEditMode(false)
                    eventChannel.send(ProjectEditTextEvent.OnSavedSuccess)
                }
            }
        }
    }

    internal companion object {
        const val KEY_TITLE = "projectEditText.title"
        const val KEY_DESCRIPTION = "projectEditText.description"
        const val KEY_IS_EDIT_MODE = "projectEditText.isEditMode"
    }
}

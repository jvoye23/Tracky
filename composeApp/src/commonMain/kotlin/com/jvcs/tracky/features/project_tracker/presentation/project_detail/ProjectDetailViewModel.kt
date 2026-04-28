package com.jvcs.tracky.features.project_tracker.presentation.project_detail

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvcs.tracky.core.domain.model.Project
import com.jvcs.tracky.core.domain.model.ProjectSession
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.TimeManager
import com.jvcs.tracky.core.domain.util.TimerState
import com.jvcs.tracky.core.presentation.mapper.toProjectSessionUi
import com.jvcs.tracky.core.presentation.mapper.toProjectUi
import com.jvcs.tracky.design_system.util.asUiText
import com.jvcs.tracky.design_system.util.parseDuration
import com.jvcs.tracky.features.project_tracker.domain.EditTextType
import com.jvcs.tracky.features.project_tracker.domain.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

class ProjectDetailViewModel(
    private val isEdit: Boolean,
    private val projectId: String?,
    private val projectRepository: ProjectRepository,
    private val timeManager: TimeManager
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
            timeManager.sessionStates.collect { activeTimersMap ->
                updateUiWithTimerValues(activeTimersMap)

            }
        }
    }

    fun onAction(action: ProjectDetailAction) {
        when(action){
            is ProjectDetailAction.OnSaveClick -> {saveProject()}
            ProjectDetailAction.OnCloseAndCancelClick -> {}
            ProjectDetailAction.OnEditModeClick -> {toggleEditMode()}
            is ProjectDetailAction.OnEditTextClick -> {}
            ProjectDetailAction.OnStartTrackerClick -> {}
            ProjectDetailAction.OnStopTrackerClick -> {}
            is ProjectDetailAction.OnEditTextChanged -> {onEditTextChanged(action.value, action.editTextType)}
            is ProjectDetailAction.OnProjectSessionCardClick -> {}
            ProjectDetailAction.OnToggleAddNewProjectSessionBottomSheet -> {toggleAddNewProjectSessionBottomSheet()}
            is ProjectDetailAction.OnCreateProjectSession -> {createProjectSession(action.projectSessionTitle)}
            is ProjectDetailAction.OnToggleSessionTimer -> {onToggleTimer(action.projectSessionId)}
        }
    }

    //NewTimer Implementation

    private fun updateUiWithTimerValues(activeTimersMap: Map<String, TimerState>) {
        _state.update { currentState ->
            val currentProject = currentState.project ?: return@update currentState

            // Efficiently update only the sessions that are running
            val updatedSessions = currentProject.projectSessions?.map { session ->
                val timerState = activeTimersMap[session.id]

                if (timerState != null && timerState.isRunning) {
                    // CASE: Running - Use the live value
                    session.copy(
                        formattedDuration = timerState.formattedTime,
                        isTimerRunning = timerState.isRunning
                    )
                } else {
                    // CASE: Not Running - Keep static DB value
                    // This prevents "flickering" back to 0s if the timer stops
                    session.copy(
                        isTimerRunning = false
                    )
                }
            }

            currentState.copy(
                project = currentProject.copy(projectSessions = updatedSessions)
            )
        }
    }

    private fun onToggleTimer(sessionId: String) {

        val session = _state.value.project?.projectSessions?.find { it.id == sessionId }
        if(session == null) return

        val currentDuration = parseDuration( timeString = session.formattedDuration)

        // 1. Get the current state of this session synchronously
        // (We access .value directly because we need the snapshot RIGHT NOW)
        val timerState = timeManager.sessionStates.value[sessionId]

        if (timerState != null && timerState.isRunning) {
            // --- STOPPING & SAVING ---

            // A. Capture the final duration
            val finalDuration = timerState.totalDuration

            // B. Save to Database (Async)
            viewModelScope.launch {
                projectRepository.updateSessionDuration(
                    sessionId = sessionId,
                    newDurationMillis = finalDuration.inWholeMilliseconds
                )
            }

            // C. Clear the Timer from Memory
            // This is crucial! It tells the UI: "Don't look at the TimerManager anymore, look at the DB."
            timeManager.stopAndResetTimer(sessionId)

        } else {
            // --- STARTING ---
            // Just start the timer as usual
            timeManager.toggleTimer(sessionId, currentDuration)
        }
    }


    /*private val newestTimberJobs = mutableMapOf<String, Job>()

    private fun onToggleTimer(sessionId: String) {
        val existingJob = newestTimberJobs[sessionId]

        if (existingJob != null) {
            existingJob.cancel()
            newestTimberJobs.remove(sessionId)
            timeManager.toggleTimer(sessionId)
        } else {
            timeManager.toggleTimer(sessionId)
            val newJob = viewModelScope.launch {
                timeManager.getSessionState(sessionId)
                    .collectLatest { timerState ->
                        _state.update { currentState ->
                            // Safe call on project (handle nullability)
                            val currentProject = currentState.project ?: return@update currentState
                            val updatedSessions = currentProject.projectSessions?.map { session ->
                                if (session.id == sessionId) {
                                    session.copy(
                                        formattedDuration = timerState.formattedTime,
                                    )
                                } else {
                                    // Leave other sessions unchanged
                                    session
                                }
                            }
                            currentState.copy(
                                project = currentProject.copy(
                                    projectSessions = updatedSessions
                                )
                            )
                        }
                    }
            }
            newestTimberJobs[sessionId] = newJob
        }
    }

    private fun onDeleteTimer(sessionId: String) {
        timeManager.stopAndResetTimer(sessionId)
    }*/
    //End New Timer Implementation



    private fun createProjectSession(projectSessionTitle: String) {
        viewModelScope.launch {
            val currentProject = state.value.project

            val newProjectSession = ProjectSession(
                projectSessionId = Clock.System.now().toString().takeLast(6).dropLast(1),
                title = projectSessionTitle,
                durationMillis = 0L,
                startDateTimeUtc = Clock.System.now(),
                endDateTimeUtc = null,
                isFinished = false,
                parentProjectId = state.value.project?.projectId!!,
                isTimerRunning = false
            )

            when(val result = projectRepository.upsertProjectSession(newProjectSession)) {
                is Result.Error -> {
                eventChannel.send(ProjectDetailEvent.Error(result.error.asUiText()))
            }
                is Result.Success-> {
                    _state.update { it.copy(
                        isAddNewProjectSessionBottomSheetVisible = false,
                        addProjectSessionTextFieldState = TextFieldState(),
                        project = currentProject?.copy(
                            projectSessions = currentProject.projectSessions?.plus(newProjectSession.toProjectSessionUi())
                        )
                    ) }
                    eventChannel.send(ProjectDetailEvent.NewProjectSessionSaved(projectSessionTitle))
                }
            }
        }
    }


    private fun toggleAddNewProjectSessionBottomSheet() {
        _state.update { it.copy(
            isAddNewProjectSessionBottomSheetVisible = !it.isAddNewProjectSessionBottomSheetVisible
        ) }
    }

    private fun getProject(projectId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val newProject = projectRepository.getProjectWithSessionsByProjectId(projectId)
            _state.update { it.copy(
                project = newProject?.toProjectUi(),
                titleText = newProject?.title ?: "",
            ) }
        }
    }

    private fun saveProject(){

        //TODO Save Project

        /*val newTitle = _state.value.titleText.toString()
        val newDescription = _state.value.descriptionText.toString()
        val newDuration = _state.value.timerDuration?.inWholeMilliseconds


        val newProjectSession = ProjectSession(
            projectSessionId = Clock.System.now().toString().takeLast(6).dropLast(1),
            title = newDescription,
            durationMillis = newDuration ?: 0L,
            startDateTimeUtc = Clock.System.now(),
            endDateTimeUtc = Clock.System.now(),
            isFinished = false,
            parentProjectId = "123",
            isTimerRunning = false
        )

        val newProject = Project(
            projectId = "12345789-01",
            title = newTitle,
            description = newDescription,
            colorArgb = 20,
            totalDurationMillis = 1000L,
            startDateTimeUtc = Clock.System.now(),
            isFinished = false,
            projectSessions = listOf(newProjectSession),
            endDateTimeUtc = null
        )

        viewModelScope.launch {
            projectRepository.upsertProject(newProject)
        }
        */
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
    /*private fun toggleSessionTimer(projectSessionId: String) {
        if (_state.value.isTimerRunning) {
            pauseTracker()
        } else {
            startTracker()
        }
    }

    private var timerJob: Job? = null
    private var accumulatedDuration: Duration = Duration.ZERO
    private var startMark: TimeSource.Monotonic.ValueTimeMark? = null

    fun toggleTracker() {
        if (_state.value.isTimerRunning) {
            pauseTracker()
        } else {
            startTracker()
        }
    }

    private fun startTracker() {
        // Prevent starting if already running
        if (timerJob?.isActive == true) return

        // 1. Mark the start time
        startMark = TimeSource.Monotonic.markNow()

        _state.update { it.copy(isTimerRunning = true) }

        timerJob = viewModelScope.launch {
            while (true) {
                // 2. Calculate Total = (Previous Sessions) + (Current Session)
                val currentSessionDuration = startMark?.elapsedNow() ?: Duration.ZERO
                val totalDuration = accumulatedDuration + currentSessionDuration

                // 3. Format and update state
                val formattedString = formatDuration(totalDuration)
                _state.update { it.copy(
                    formattedTimerString = formattedString,
                    timerDuration = totalDuration
                ) }

                delay(10) // Update every 10ms
            }
        }
    }

    private fun pauseTracker() {
        timerJob?.cancel()

        // 4. "Bank" the time from the current session into the accumulator
        startMark?.let { mark ->
            accumulatedDuration += mark.elapsedNow()
        }

        startMark = null
        _state.update { it.copy(isTimerRunning = false) }
    }

    // Helper to format Duration in KMP
    private fun formatDuration(duration: Duration): String {
        return duration.toComponents { hours, minutes, seconds, nanoseconds ->
            val centiseconds = nanoseconds / 10_000_000
            "${hours.toString().padStart(2, '0')}:" +
                    "${minutes.toString().padStart(2, '0')}:" +
                    "${seconds.toString().padStart(2, '0')}:" +
                    "${centiseconds.toString().padStart(2, '0')}"
        }
    }

    fun stopTracker() {
        timerJob?.cancel()
        _state.update { it.copy(isTimerRunning = false) }
    }*/

}
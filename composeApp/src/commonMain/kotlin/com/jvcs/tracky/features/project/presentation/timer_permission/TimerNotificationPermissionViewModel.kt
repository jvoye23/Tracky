package com.jvcs.tracky.features.project.presentation.timer_permission

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvcs.tracky.core.domain.notification.TimerNotificationPermission
import com.jvcs.tracky.core.domain.notification.TimerNotificationPermissionRequester
import com.jvcs.tracky.features.project.domain.timer.RunningTimerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Asks for the notification permission the first time a timer runs, and explains what was lost if
 * the answer is no.
 *
 * Lives above the screens rather than inside one, like the stranded-timer host: a timer can be
 * started from project detail, task detail or the daily overview, and the ask belongs to the timer,
 * not to whichever screen happened to start it.
 */
class TimerNotificationPermissionViewModel(
    private val permissionRequester: TimerNotificationPermissionRequester,
    private val runningTimerRepository: RunningTimerRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private var hasLoadedInitialData = false

    /**
     * Held in the SavedStateHandle rather than a plain field so it survives process death, which is
     * what the `rememberSaveable` this replaces did. Android stops showing the system dialog after
     * two refusals; without this, ours would still pop on every cold start with a timer running.
     */
    private var hasAsked: Boolean
        get() = savedStateHandle[KEY_HAS_ASKED] ?: false
        set(value) {
            savedStateHandle[KEY_HAS_ASKED] = value
        }

    private val _state = MutableStateFlow(TimerNotificationPermissionState())
    val state =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    observeRunningTimer()
                    hasLoadedInitialData = true
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = TimerNotificationPermissionState(),
            )

    private fun observeRunningTimer() {
        runningTimerRepository
            .observeRunningTimer()
            .map { it != null }
            .distinctUntilChanged()
            .onEach { isRunning -> if (isRunning) askOnce() }
            // viewModelScope, not the state's collector, so a five-second gap with nothing
            // subscribed cannot drop the ask on the floor.
            .launchIn(viewModelScope)
    }

    private suspend fun askOnce() {
        if (hasAsked) return
        hasAsked = true

        when (permissionRequester.request()) {
            TimerNotificationPermission.Granted,
            TimerNotificationPermission.NotRequired,
            -> {
                Unit
            }

            // Both refusals get the same explanation: the timer runs either way, and app settings is
            // a valid route out of either one.
            TimerNotificationPermission.Denied,
            TimerNotificationPermission.DeniedAlways,
            -> {
                _state.update { it.copy(showDeniedDialog = true) }
            }
        }
    }

    fun onAction(action: TimerNotificationPermissionAction) {
        when (action) {
            TimerNotificationPermissionAction.OnConfirm -> {
                dismiss()
            }

            TimerNotificationPermissionAction.OnOpenAppSettings -> {
                permissionRequester.openAppSettings()
                dismiss()
            }
        }
    }

    private fun dismiss() = _state.update { it.copy(showDeniedDialog = false) }

    private companion object {
        private const val KEY_HAS_ASKED = "timer_notification_permission_asked"
    }
}

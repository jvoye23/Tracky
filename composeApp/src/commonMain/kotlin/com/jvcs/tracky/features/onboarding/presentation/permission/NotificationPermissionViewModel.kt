package com.jvcs.tracky.features.onboarding.presentation.permission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvcs.tracky.core.domain.notification.TimerNotificationPermissionRequester
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Explains the timer notification, then asks for it.
 *
 * Never a barrier: a refusal moves on exactly like a grant. The timer runs either way, and the
 * timer-permission dialog explains what was lost the first time one actually starts.
 */
class NotificationPermissionViewModel(private val permissionRequester: TimerNotificationPermissionRequester) :
    ViewModel() {

    private val eventChannel = Channel<NotificationPermissionEvent>()
    val events = eventChannel.receiveAsFlow()

    private val _state = MutableStateFlow(NotificationPermissionState())
    val state = _state.asStateFlow()

    fun onAction(action: NotificationPermissionAction) {
        when (action) {
            NotificationPermissionAction.OnAllowClick -> requestPermission()
            NotificationPermissionAction.OnSkipClick -> finish()
        }
    }

    private fun requestPermission() {
        // A second tap while the system dialog is up would queue a second request behind it.
        if (_state.value.isRequesting) return
        _state.update { it.copy(isRequesting = true) }
        viewModelScope.launch {
            permissionRequester.request()
            _state.update { it.copy(isRequesting = false) }
            eventChannel.send(NotificationPermissionEvent.Finished)
        }
    }

    private fun finish() {
        viewModelScope.launch { eventChannel.send(NotificationPermissionEvent.Finished) }
    }
}

package com.jvcs.tracky.core.domain.realtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether this device currently has the server's ear. */
enum class RealtimeConnectionState { Idle, Connecting, Connected, Disconnected }

/**
 * Whether the realtime socket is up.
 *
 * Its own small object rather than a field on the RealtimeTimerConnection (core.data.realtime),
 * for the same reason `SyncRecency` is one: the sync loop wants to read it — a device with a live socket can poll far
 * less often — without depending on the thing that drives the socket, and a test can pin it
 * without opening one.
 */
class RealtimeConnectivity {

    private val _state = MutableStateFlow(RealtimeConnectionState.Idle)
    val state: StateFlow<RealtimeConnectionState> = _state.asStateFlow()

    internal fun set(state: RealtimeConnectionState) {
        _state.value = state
    }
}

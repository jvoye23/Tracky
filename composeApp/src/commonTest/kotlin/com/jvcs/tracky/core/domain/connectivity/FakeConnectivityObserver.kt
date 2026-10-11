package com.jvcs.tracky.core.domain.connectivity

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/** Connectivity a test can switch, as a live flow. */
class FakeConnectivityObserver(isConnected: Boolean = true) : ConnectivityObserver {

    override val isConnected = MutableStateFlow(isConnected)
}

/**
 * Online by default, as a single value that then completes - the same shape the old JVM
 * implementation had. It matters: ProjectSyncManager debounces connectivity, and a completed flow
 * hands its last value straight through where a live one would hold it for the debounce window.
 */
class FixedConnectivityObserver(override val isConnected: Flow<Boolean> = flowOf(true)) : ConnectivityObserver

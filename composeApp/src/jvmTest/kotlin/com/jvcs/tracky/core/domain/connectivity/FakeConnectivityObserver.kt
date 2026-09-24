package com.jvcs.tracky.core.domain.connectivity

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Online by default, as a single value that then completes - the same shape as the JVM
 * implementation. It matters: ProjectSyncManager debounces connectivity, and a completed flow
 * hands its last value straight through where a live one would hold it for the debounce window.
 */
class FakeConnectivityObserver(override val isConnected: Flow<Boolean> = flowOf(true)) : ConnectivityObserver

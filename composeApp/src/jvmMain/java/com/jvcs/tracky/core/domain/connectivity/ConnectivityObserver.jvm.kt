package com.jvcs.tracky.core.domain.connectivity

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

actual class ConnectivityObserver {
    // Desktop is treated as always-connected for now.
    actual val isConnected: Flow<Boolean> = flowOf(true)
}

package com.jvcs.tracky.core.data.connectivity

import com.jvcs.tracky.core.domain.connectivity.ConnectivityObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class JvmConnectivityObserver : ConnectivityObserver {

    // Desktop is treated as always-connected for now.
    override val isConnected: Flow<Boolean> = flowOf(true)
}

package com.jvcs.tracky.core.domain.connectivity

import kotlinx.coroutines.flow.Flow

/**
 * Observes device network connectivity. Implemented per platform (Android ConnectivityManager,
 * iOS NWPathMonitor, JVM always-connected stub).
 */
expect class ConnectivityObserver {
    val isConnected: Flow<Boolean>
}

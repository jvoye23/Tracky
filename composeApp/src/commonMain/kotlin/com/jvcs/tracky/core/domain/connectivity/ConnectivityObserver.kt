package com.jvcs.tracky.core.domain.connectivity

import kotlinx.coroutines.flow.Flow

/**
 * Observes device network connectivity. Implemented per platform in core.data (Android ConnectivityManager,
 * iOS NWPathMonitor, JVM always-connected stub).
 */
interface ConnectivityObserver {

    val isConnected: Flow<Boolean>
}

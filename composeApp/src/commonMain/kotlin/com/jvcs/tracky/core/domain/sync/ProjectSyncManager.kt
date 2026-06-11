@file:OptIn(FlowPreview::class)

package com.jvcs.tracky.core.domain.sync

import com.jvcs.tracky.core.domain.connectivity.ConnectivityObserver
import com.jvcs.tracky.core.domain.lifecycle.AppLifecycleObserver
import com.jvcs.tracky.features.project_tracker.domain.ProjectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration.Companion.seconds

/**
 * Reactive, portable foreground sync. Whenever the device is online and the app is in the
 * foreground, it drains the pending-sync queue and pulls a fresh copy from the backend. This is
 * the dependable sync path; the [SyncScheduler] adds best-effort background sync on top.
 */
class ProjectSyncManager(
    private val connectivityObserver: ConnectivityObserver,
    private val appLifecycleObserver: AppLifecycleObserver,
    private val projectRepository: ProjectRepository,
    private val applicationScope: CoroutineScope
) {
    private var started = false

    fun start() {
        if (started) return
        started = true

        combine(
            connectivityObserver.isConnected.debounce(1.seconds),
            appLifecycleObserver.isInForeground
        ) { online, foreground -> online && foreground }
            .distinctUntilChanged()
            .filter { it }
            .onEach {
                projectRepository.syncPendingOperations()
                projectRepository.fetchProjects()
            }
            .launchIn(applicationScope)
    }
}

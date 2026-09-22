@file:OptIn(FlowPreview::class)

package com.jvcs.tracky.core.domain.sync

import com.jvcs.tracky.core.domain.connectivity.ConnectivityObserver
import com.jvcs.tracky.core.domain.lifecycle.AppLifecycleObserver
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.TimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Reactive, portable foreground sync. Whenever the device is online and the app is in the
 * foreground, it drains the pending-sync queue and pulls a fresh copy from the backend. This is
 * the dependable sync path; the [SyncScheduler] adds best-effort background sync on top.
 */
class ProjectSyncManager(
    private val connectivityObserver: ConnectivityObserver,
    private val appLifecycleObserver: AppLifecycleObserver,
    private val syncRepository: SyncRepository,
    private val deltaSyncApplier: DeltaSyncApplier,
    private val syncRecency: SyncRecency,
    private val applicationScope: CoroutineScope,
    private val timeProvider: TimeProvider
) {
    private var started = false
    private var lastPull: Instant = Instant.DISTANT_PAST

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
                syncRepository.syncPendingOperations()
                val now = timeProvider.nowInstant
                if (now - lastPull > PULL_INTERVAL) {
                    // A delta, falling back to the full tree when the feed cannot be used. The
                    // throttle stays: it bounds how often this device talks to the server at all,
                    // and a delta on a quiet account is cheap but not free.
                    // Only a pull that landed counts as hearing from the server. Stamping the
                    // attempt would keep a foreign timer ticking through an outage, which is the
                    // one thing SyncRecency exists to stop.
                    if (deltaSyncApplier.pullChanges() is Result.Success) {
                        syncRecency.markSynced(timeProvider.nowInstant)
                    }
                    lastPull = now
                }
            }
            .launchIn(applicationScope)
    }

    private companion object {
        val PULL_INTERVAL = 5.minutes
    }
}

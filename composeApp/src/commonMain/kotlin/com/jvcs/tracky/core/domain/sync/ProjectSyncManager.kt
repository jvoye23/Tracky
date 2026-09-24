@file:OptIn(FlowPreview::class)

package com.jvcs.tracky.core.domain.sync

import com.jvcs.tracky.core.domain.connectivity.ConnectivityObserver
import com.jvcs.tracky.core.domain.lifecycle.AppLifecycleObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration.Companion.seconds

/**
 * Reactive, portable foreground sync. Whenever the device is online and the app is in the
 * foreground, it drains the pending-sync queue and pulls a fresh copy from the backend. This is
 * the dependable sync path; the [SyncScheduler] adds best-effort background sync on top.
 *
 * **This has to be a clock, not an edge.** It used to be
 * `combine(online, foreground).distinctUntilChanged().filter { it }`, which over a Boolean emits
 * only on the false-to-true transition: a device that stayed online and foregrounded pulled once,
 * at launch, and never again. Nothing else in the app pulls — every background path
 * ([SyncScheduler], `SyncWorker`, the iOS BGTask) only drains the outbox upward — so a second
 * device could learn about the first's timer only by the user swiping to refresh. The five-minute
 * constant that used to live here read like a throttle but was a ceiling on an event that never
 * re-fired.
 */
class ProjectSyncManager(
    private val connectivityObserver: ConnectivityObserver,
    private val appLifecycleObserver: AppLifecycleObserver,
    private val syncRepository: SyncRepository,
    private val pullCoordinator: SyncPullCoordinator,
    private val applicationScope: CoroutineScope,
) {

    private var started = false

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        if (started) return
        started = true

        combine(
            connectivityObserver.isConnected.debounce(1.seconds),
            appLifecycleObserver.isInForeground,
        ) { online, foreground -> online && foreground }
            .distinctUntilChanged()
            .flatMapLatest { active ->
                // flatMapLatest, so going offline or backgrounding cancels the ticker outright
                // rather than leaving it running against a device that cannot reach the server.
                if (!active) {
                    emptyFlow()
                } else {
                    flow {
                        while (true) {
                            emit(Unit)
                            delay(FOREGROUND_PULL_INTERVAL)
                        }
                    }
                }
            }.onEach {
                syncRepository.syncPendingOperations()
                // Through the coordinator, not straight at the applier: this tick is no longer the
                // only thing that pulls, and two pulls overlapping can walk the cursor backwards.
                pullCoordinator.pullNow()
            }.launchIn(applicationScope)
    }

    private companion object {
        /**
         * Only while the app is foregrounded, so this costs nothing when the user is not looking.
         * A delta pull on a quiet account is an empty page, which is why polling this often is
         * affordable at all. It is the correctness floor, not the fast path: once the realtime
         * socket lands it can go back up, because the socket carries the transition and this only
         * has to catch what the socket missed.
         */
        val FOREGROUND_PULL_INTERVAL = 30.seconds
    }
}

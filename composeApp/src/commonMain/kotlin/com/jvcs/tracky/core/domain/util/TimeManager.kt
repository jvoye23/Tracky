package com.jvcs.tracky.core.domain.util

import com.jvcs.tracky.design_system.util.formatDuration
import com.jvcs.tracky.features.project.domain.timer.RunningTimer
import com.jvcs.tracky.features.project.domain.timer.RunningTimerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.jvcs.tracky.core.domain.sync.SyncRecency
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** What one screen needs to draw a timer: is it running, how long, and how that reads. */
data class TimerState(
    val isRunning: Boolean = false,
    val totalDuration: Duration = Duration.ZERO,
    val formattedTime: String = "00:00:00"
)

/** The running timer together with its live elapsed value. */
data class RunningTimerTick(
    val timer: RunningTimer,
    val elapsed: Duration,
    /**
     * True when this is a foreign timer this device has not been able to confirm for a while, so
     * [elapsed] is frozen at what it read when the server was last heard from.
     *
     * A surface should say so rather than showing the number as if it were live — it is the last
     * thing known to be true, not the current truth.
     */
    val isStale: Boolean = false
) {
    val formatted: String get() = formatDuration(elapsed)
}

/**
 * Renders the running timer. It does not own one.
 *
 * The open interval in the database is the only thing that says a timer is running, and
 * [RunningTimer.elapsedAt] is the only place elapsed time is computed - the same call the
 * notification makes. So the two surfaces cannot disagree, and a Pause pressed in the
 * notification reaches every screen without anyone having to tell them.
 *
 * This used to be the opposite: an in-memory map keyed by task id, ticking off a monotonic mark
 * taken when the user tapped and seeded by re-parsing the string already on screen. Nothing
 * outside the app could stop it, and it drifted from the database by whatever the seeding lost.
 *
 * At most one timer runs at a time, so [taskStates] holds at most one entry - kept as a map
 * because that is the shape the screens already read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimeManager(
    private val runningTimerRepository: RunningTimerRepository,
    private val serverClock: ServerClock,
    private val syncRecency: SyncRecency,
    scope: CoroutineScope
) {
    val tick: StateFlow<RunningTimerTick?> = runningTimerRepository
        .observeRunningTimer()
        .flatMapLatest { timer ->
            if (timer == null) {
                flowOf<RunningTimerTick?>(null)
            } else {
                flow {
                    while (true) {
                        // Corrected, not the raw device clock: startedAt may have come from the
                        // user's other phone, and the two clocks disagreeing shows up directly as
                        // a wrong duration.
                        val now = serverClock.now()
                        val lastSync = syncRecency.lastSuccessfulSync.value
                        // Only a foreign timer can go stale. One this device started is running
                        // because this device is running it — there is nothing to confirm.
                        val stale = timer.isForeign && lastSync != null && now - lastSync > STALE_AFTER
                        val elapsed = timer.elapsedAt(if (stale) lastSync else now)
                        emit(RunningTimerTick(timer, elapsed, isStale = stale))
                        if (stale) {
                            // Nothing to animate, but keep looking: the next pull un-freezes it.
                            delay(STALE_RECHECK_MILLIS)
                        } else {
                            // Sleep to the next whole second of elapsed rather than a flat second
                            // from an arbitrary moment, so the digits turn over at the same instant
                            // the notification's do.
                            delay(TICK_MILLIS - elapsed.inWholeMilliseconds % TICK_MILLIS)
                        }
                    }
                }
            }
        }
        // Eagerly, on the app scope: the clock has to be right the moment a screen composes, not a
        // second later, and it is one coroutine for the whole app.
        .stateIn(scope, SharingStarted.Eagerly, null)

    val taskStates: StateFlow<Map<String, TimerState>> = tick
        .map { tick ->
            if (tick == null) {
                emptyMap()
            } else {
                mapOf(
                    tick.timer.timedEntityId to TimerState(
                        isRunning = true,
                        totalDuration = tick.elapsed,
                        formattedTime = tick.formatted
                    )
                )
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private companion object {
        const val TICK_MILLIS = 1_000L
        const val STALE_RECHECK_MILLIS = 10_000L

        /**
         * Three missed pull cycles, so one failed sync does not freeze a healthy timer.
         *
         * `ProjectSyncManager` pulls at most every five minutes while online and foregrounded, so
         * anything past this means the device has genuinely stopped hearing from the server rather
         * than merely being between polls.
         */
        val STALE_AFTER = 15.minutes
    }
}

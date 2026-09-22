package com.jvcs.tracky.core.domain.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Instant

/**
 * When this device last heard from the server.
 *
 * Its own small object rather than a field on [ProjectSyncManager], so the timer can read it
 * without depending on the thing that drives syncing — and so a test can pin it without starting
 * a sync loop.
 *
 * It exists for one case. A timer another device started keeps ticking here from its `startedAt`,
 * correctly and indefinitely, even with no network: that is the whole point of deriving elapsed
 * rather than counting it. But *correct* and *current* are different things. If the other device
 * stopped the timer an hour ago and this one has not managed a pull since, the number on screen is
 * a confident lie, and it grows by a second every second.
 *
 * In memory only. A cold start has not synced yet by definition, and treating "we just launched"
 * as "we last synced whenever we last ran" would freeze a perfectly good timer on every launch.
 */
class SyncRecency {

    private val _lastSuccessfulSync = MutableStateFlow<Instant?>(null)

    /** Null until the first successful pull of this process. */
    val lastSuccessfulSync: StateFlow<Instant?> = _lastSuccessfulSync.asStateFlow()

    fun markSynced(at: Instant) {
        _lastSuccessfulSync.value = at
    }
}

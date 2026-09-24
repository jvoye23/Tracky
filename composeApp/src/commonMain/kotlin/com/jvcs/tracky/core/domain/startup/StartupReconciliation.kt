package com.jvcs.tracky.core.domain.startup

/**
 * A gate the timer-start paths wait on.
 *
 * The stranded-interval pass runs once at start-up and is fire-and-forget, but starting a timer
 * before it finishes is worse than not running it at all: `startTask` reuses whatever interval is
 * already open, so it would adopt the stranded row and the next stop would bank every hour since
 * that row opened. One await at each start path closes the window.
 */
interface StartupReconciliation {

    /** Suspends until the start-up pass has finished. Returns immediately once it has. */
    suspend fun awaitReconciled()
}

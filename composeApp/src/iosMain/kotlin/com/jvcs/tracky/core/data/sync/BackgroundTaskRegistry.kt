package com.jvcs.tracky.core.data.sync

import kotlin.concurrent.Volatile

/**
 * Tracks which BGTaskScheduler identifiers already have a launch handler installed.
 *
 * Submitting a request for an identifier that is listed in BGTaskSchedulerPermittedIdentifiers but
 * has no registered handler raises an Objective-C NSInternalInconsistencyException. Kotlin/Native
 * does not convert that into a catchable [Throwable] — it unwinds through Kotlin frames and
 * terminates the process — so the bad submit has to be prevented rather than caught.
 *
 * The Swift app calls [markRegistered] right after each
 * BGTaskScheduler.register(forTaskWithIdentifier:using:launchHandler:), before Koin starts.
 */
object BackgroundTaskRegistry {

    @Volatile
    private var registered: Set<String> = emptySet()

    /** Called from Swift immediately after BGTaskScheduler.register(forTaskWithIdentifier:). */
    fun markRegistered(identifier: String) {
        registered = registered + identifier
    }

    fun isRegistered(identifier: String): Boolean = identifier in registered
}

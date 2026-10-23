package com.jvcs.tracky.features.project.domain.project

import com.jvcs.tracky.analytics.api.AnalyticsEvent
import com.jvcs.tracky.analytics.api.AnalyticsLogger
import com.jvcs.tracky.core.domain.device.InstallTimeProvider
import com.jvcs.tracky.core.domain.util.TimeProvider
import kotlinx.coroutines.flow.first

/** Logs [AnalyticsEvent.FirstProjectCreated] once per installation, for the first project saved on it. */
class FirstProjectCreatedTracker(
    private val firstProjectStore: FirstProjectStore,
    private val projectRepository: ProjectRepository,
    private val installTimeProvider: InstallTimeProvider,
    private val timeProvider: TimeProvider,
    private val analyticsLogger: AnalyticsLogger,
) {

    /** Call after the project [projectId] was saved. */
    suspend fun onProjectCreated(projectId: String) {
        if (!firstProjectStore.claimFirst()) return

        // A device that already had projects before this event existed: its first one was created
        // long ago. The claim is still taken, so no later project counts as the first either.
        val hasEarlierProjects = projectRepository.getProjects().first().any { it.projectId != projectId }
        if (hasEarlierProjects) return

        val sinceInstall = timeProvider.nowInstant - installTimeProvider.installedAt()
        analyticsLogger.log(AnalyticsEvent.FirstProjectCreated(secondsSinceInstall = sinceInstall.inWholeSeconds))
    }
}

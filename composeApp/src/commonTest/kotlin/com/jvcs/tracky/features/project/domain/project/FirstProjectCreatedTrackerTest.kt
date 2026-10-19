package com.jvcs.tracky.features.project.domain.project

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import com.jvcs.tracky.analytics.api.AnalyticsEvent
import com.jvcs.tracky.analytics.api.FakeAnalyticsLogger
import com.jvcs.tracky.core.domain.device.FakeInstallTimeProvider
import com.jvcs.tracky.core.domain.util.FakeTimeProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal class FirstProjectCreatedTrackerTest {

    private val installedAt = Instant.fromEpochMilliseconds(1_000_000)
    private val analytics = FakeAnalyticsLogger()
    private val repository = FakeProjectListRepository()

    private fun tracker(
        store: FirstProjectStore = FakeFirstProjectStore(),
        now: Instant = installedAt + 3.minutes + 20.seconds,
    ) = FirstProjectCreatedTracker(
        firstProjectStore = store,
        projectRepository = repository,
        installTimeProvider = FakeInstallTimeProvider(installedAt),
        timeProvider = FakeTimeProvider(now),
        analyticsLogger = analytics,
    )

    @Test
    fun theFirstProjectIsLoggedWithTheSecondsSinceInstall() =
        runTest {
            repository.projects.value = listOf(storedProject("garden"))

            tracker().onProjectCreated("garden")

            assertThat(analytics.events).containsExactly(AnalyticsEvent.FirstProjectCreated(secondsSinceInstall = 200))
        }

    @Test
    fun laterProjectsAreNotLogged() =
        runTest {
            val tracker = tracker()
            repository.projects.value = listOf(storedProject("garden"))
            tracker.onProjectCreated("garden")

            repository.projects.value = listOf(storedProject("garden"), storedProject("kitchen"))
            tracker.onProjectCreated("kitchen")

            assertThat(analytics.events).containsExactly(AnalyticsEvent.FirstProjectCreated(secondsSinceInstall = 200))
        }

    @Test
    fun anInstallationThatAlreadyLoggedItLogsNothing() =
        runTest {
            // Logout deletes every project, but the first one was still created long ago.
            tracker(store = FakeFirstProjectStore(isClaimed = true)).onProjectCreated("garden")

            assertThat(analytics.events).isEmpty()
        }

    @Test
    fun aDeviceWithProjectsFromBeforeTheEventExistedTakesTheClaimWithoutLogging() =
        runTest {
            val store = FakeFirstProjectStore()
            repository.projects.value = listOf(storedProject("old"), storedProject("garden"))

            tracker(store = store).onProjectCreated("garden")

            assertThat(analytics.events).isEmpty()
            // Taken for good: deleting the old projects later does not make a new one the first.
            repository.projects.value = listOf(storedProject("kitchen"))
            tracker(store = store).onProjectCreated("kitchen")
            assertThat(analytics.events).isEmpty()
        }
}

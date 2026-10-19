package com.jvcs.tracky.features.project.domain.project

import assertk.assertThat
import assertk.assertions.containsExactly
import com.jvcs.tracky.analytics.api.FakeAnalyticsLogger
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

internal class ProjectsCountPropertySyncTest {

    private val analytics = FakeAnalyticsLogger()
    private val dataSource = FakeLocalProjectDataSource()

    // runCurrent, not advanceUntilIdle: the sync collects on backgroundScope, which
    // advanceUntilIdle does not wait for.
    private fun TestScope.startSync() {
        ProjectsCountPropertySync(
            localProjectDataSource = dataSource,
            analyticsLogger = analytics,
            applicationScope = backgroundScope,
        ).start()
        runCurrent()
    }

    private fun storeProjects(count: Int) {
        dataSource.trees.value = List(count) { storedProject("p$it") }
    }

    private fun setValues() = analytics.userProperties.map { it.name to it.value }

    @Test
    fun theCountIsSetAtLaunch() =
        runTest {
            startSync()

            assertThat(setValues()).containsExactly("projects_count" to "0")
        }

    @Test
    fun everyChangeOfBucketSetsItAgain() =
        runTest {
            startSync()

            storeProjects(1)
            runCurrent()
            storeProjects(6)
            runCurrent()

            assertThat(setValues().map { it.second }).containsExactly("0", "1-5", "6")
        }

    @Test
    fun aChangeInsideTheSameBucketSetsNothing() =
        runTest {
            storeProjects(2)
            startSync()

            storeProjects(3)
            runCurrent()

            assertThat(setValues().map { it.second }).containsExactly("1-5")
        }
}

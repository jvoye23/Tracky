package com.jvcs.tracky.core.domain.sync

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.jvcs.tracky.core.domain.auth.FakeSessionStorage
import com.jvcs.tracky.core.domain.auth.authInfo
import com.jvcs.tracky.core.domain.connectivity.FakeConnectivityObserver
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.FakeTimeProvider
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.onboarding.domain.FakeOnboardingStatusStore
import com.jvcs.tracky.features.onboarding.domain.OnboardingStatus
import com.jvcs.tracky.features.project.domain.models.testProject
import com.jvcs.tracky.features.project.domain.models.testTask
import com.jvcs.tracky.features.project.domain.project.FakeLocalProjectDataSource
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** A free device's data reaches the account it signs in to, and nothing written meanwhile is lost. */
class LocalDataImporterTest {

    private val sessionStorage = FakeSessionStorage()
    private val importStore = FakeLocalDataImportStore(initial = true)
    private val connectivity = FakeConnectivityObserver()
    private val local = FakeLocalProjectDataSource(listOf(testProject("p1"), testProject("p2")))
    private val remote = FakeRemoteSyncDataSource()
    private val queue = FakePendingSyncDataSource()
    private val statusStore = FakeOnboardingStatusStore(OnboardingStatus.AWAITING_ACCOUNT)
    private val now = Instant.fromEpochMilliseconds(1_760_000_000_000)

    // runCurrent, not advanceUntilIdle: the importer runs on backgroundScope, which advanceUntilIdle
    // does not wait for.
    private fun TestScope.startImporter() {
        LocalDataImporter(
            sessionStorage = sessionStorage,
            localDataImportStore = importStore,
            connectivityObserver = connectivity,
            localProjectDataSource = local,
            remoteSyncDataSource = remote,
            pendingSyncDataSource = queue,
            onboardingStatusStore = statusStore,
            timeProvider = FakeTimeProvider(now),
            applicationScope = backgroundScope,
        ).start()
        runCurrent()
    }

    @Test
    fun nothingIsUploadedWithoutASession() =
        runTest {
            startImporter()

            assertThat(remote.importedProjectIds).isEmpty()
            assertThat(importStore.isPending).isTrue()
        }

    @Test
    fun signingInUploadsEveryProjectAndHandsOverToSync() =
        runTest {
            startImporter()

            sessionStorage.set(authInfo())
            runCurrent()

            assertThat(remote.importedProjectIds).containsExactly("p1", "p2")
            assertThat(importStore.isPending).isFalse()
            assertThat(statusStore.current).isEqualTo(OnboardingStatus.NOT_STARTED)
            assertThat(queue.operations).isEmpty()
        }

    @Test
    fun aDeviceWithNothingToImportIsNotMarkedPending() =
        runTest {
            importStore.setPending(false)
            sessionStorage.set(authInfo())

            startImporter()

            assertThat(remote.importedProjectIds).isEmpty()
        }

    @Test
    fun offlineItWaitsForTheConnection() =
        runTest {
            connectivity.isConnected.value = false
            sessionStorage.set(authInfo())
            startImporter()
            assertThat(remote.importedProjectIds).isEmpty()

            connectivity.isConnected.value = true
            runCurrent()

            assertThat(remote.importedProjectIds).containsExactly("p1", "p2")
        }

    @Test
    fun aRefusedImportIsRetriedWithBackoffUntilItLands() =
        runTest {
            // The server refuses until RevenueCat's webhook has marked the account Pro.
            remote.importResults += Result.Error(DataError.Remote.PAYMENT_REQUIRED)
            sessionStorage.set(authInfo())
            startImporter()
            assertThat(importStore.isPending).isTrue()

            advanceTimeBy(5.seconds)
            runCurrent()

            assertThat(remote.importedProjectIds).containsExactly("p1", "p2")
            assertThat(importStore.isPending).isFalse()
        }

    @Test
    fun aProjectRefusedForGoodIsSkippedAndReportedWhileTheRestLands() =
        runTest {
            local.trees.value = listOf(testProject("p1", title = "Broken"), testProject("p2"))
            remote.importResults += Result.Error(DataError.Remote.BAD_REQUEST)
            sessionStorage.set(authInfo())

            startImporter()

            assertThat(remote.importedProjectIds).containsExactly("p2")
            assertThat(importStore.isPending).isFalse()
            assertThat(importStore.refused.value).containsExactly("Broken")
        }

    @Test
    fun aProjectRefusedForGoodIsNotRetried() =
        runTest {
            remote.importResult = Result.Error(DataError.Remote.PAYLOAD_TOO_LARGE)
            sessionStorage.set(authInfo())
            startImporter()
            remote.importResult = Result.Success(Unit)

            advanceTimeBy(10.minutes)
            runCurrent()

            assertThat(remote.importedProjectIds).isEmpty()
            assertThat(importStore.isPending).isFalse()
            assertThat(importStore.refused.value).containsExactly("Project p1", "Project p2")
        }

    @Test
    fun changesToARefusedProjectAreNotQueued() =
        runTest {
            remote.importResults += Result.Error(DataError.Remote.BAD_REQUEST)
            sessionStorage.set(authInfo())
            remote.onImport = {
                if (local.trees.value.none { it.title == "Edited" }) {
                    local.trees.value = listOf(testProject("p1", title = "Edited"), testProject("p2"))
                }
            }

            startImporter()

            assertThat(queue.operations).isEmpty()
        }

    @Test
    fun anUnexpectedFailureIsRetriedNotSkipped() =
        runTest {
            // A missing route and an undecodable 2xx are faults, not a verdict on the project.
            remote.importResults += Result.Error(DataError.Remote.NOT_FOUND)
            remote.importResults += Result.Error(DataError.Remote.SERIALIZATION)
            sessionStorage.set(authInfo())
            startImporter()
            assertThat(importStore.isPending).isTrue()

            advanceTimeBy(5.seconds)
            runCurrent()
            advanceTimeBy(10.seconds)
            runCurrent()

            assertThat(remote.importedProjectIds).containsExactly("p1", "p2")
            assertThat(importStore.refused.value).isEmpty()
        }

    @Test
    fun anExpiredTokenIsWaitedOut() =
        runTest {
            remote.importResults += Result.Error(DataError.Remote.UNAUTHORIZED)
            sessionStorage.set(authInfo())
            startImporter()
            assertThat(importStore.isPending).isTrue()

            advanceTimeBy(5.seconds)
            runCurrent()

            assertThat(remote.importedProjectIds).containsExactly("p1", "p2")
            assertThat(importStore.refused.value).isEmpty()
        }

    @Test
    fun writesThatLandDuringTheUploadAreQueued() =
        runTest {
            sessionStorage.set(authInfo())
            remote.onImport = {
                // While the requests are in flight: a new project, a renamed one, a new task.
                if (local.trees.value.none { it.projectId == "p3" }) {
                    local.trees.value =
                        listOf(
                            testProject("p1", title = "Renamed", tasks = listOf(testTask("t9", "p1"))),
                            testProject("p2"),
                            testProject("p3"),
                        )
                }
            }

            startImporter()

            assertThat(queue.operations.map { it.entityId to it.operationType }).containsExactly(
                "p1" to PendingSyncOperation.OP_UPDATE,
                "t9" to PendingSyncOperation.OP_CREATE,
                "p3" to PendingSyncOperation.OP_CREATE,
            )
            // The drain routes a task through its project, and orders the queue by when it was written.
            assertThat(queue.operations.single { it.entityId == "t9" }.parentEntityId).isEqualTo("p1")
            assertThat(queue.operations.map { it.createdAt }.distinct()).containsExactly(now)
        }

    @Test
    fun signingOutAbandonsTheAttempt() =
        runTest {
            remote.importResult = Result.Error(DataError.Remote.NO_INTERNET)
            sessionStorage.set(authInfo())
            startImporter()

            sessionStorage.set(null)
            remote.importResult = Result.Success(Unit)
            advanceTimeBy(60.seconds)
            runCurrent()

            assertThat(remote.importedProjectIds).isEmpty()
            assertThat(importStore.isPending).isTrue()
        }

    @Test
    fun everyRefusalForGoodSkipsOnlyItsOwnProject() =
        runTest {
            local.trees.value =
                listOf("bad", "big", "foreign", "taken", "fine").map { testProject(it, title = it) }
            remote.importResults += Result.Error(DataError.Remote.BAD_REQUEST)
            remote.importResults += Result.Error(DataError.Remote.PAYLOAD_TOO_LARGE)
            remote.importResults += Result.Error(DataError.Remote.FORBIDDEN)
            remote.importResults += Result.Error(DataError.Remote.CONFLICT)
            sessionStorage.set(authInfo())

            startImporter()

            assertThat(remote.importedProjectIds).containsExactly("fine")
            assertThat(importStore.isPending).isFalse()
            assertThat(importStore.refused.value).containsExactlyInAnyOrder("bad", "big", "foreign", "taken")
        }

    @Test
    fun aLocalTreeThatCannotBeReadIsRetried() =
        runTest {
            local.treeReadErrors += DataError.Local.UNKNOWN
            sessionStorage.set(authInfo())
            startImporter()

            assertThat(remote.importedProjectIds).isEmpty()
            assertThat(importStore.isPending).isTrue()

            advanceTimeBy(5.seconds)
            runCurrent()

            assertThat(remote.importedProjectIds).containsExactly("p1", "p2")
        }

    @Test
    fun retriesBackOffFromFiveSecondsAndLevelOffAtFiveMinutes() =
        runTest {
            remote.importResult = Result.Error(DataError.Remote.NO_INTERNET)
            sessionStorage.set(authInfo())
            startImporter()
            val start = currentTime

            // 5 s, doubling each time, until the 320 s step is capped at 300 s.
            listOf(5, 15, 35, 75, 155, 315, 615).forEachIndexed { retry, dueSeconds ->
                val due = start + dueSeconds * 1_000L
                advanceTimeBy((due - 1 - currentTime).milliseconds)
                runCurrent()
                assertThat(remote.importAttempts).isEqualTo(retry + 1)

                advanceTimeBy(1.milliseconds)
                runCurrent()
                assertThat(remote.importAttempts).isEqualTo(retry + 2)
            }
        }
}

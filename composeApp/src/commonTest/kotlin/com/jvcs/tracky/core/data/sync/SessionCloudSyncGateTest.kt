package com.jvcs.tracky.core.data.sync

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.jvcs.tracky.core.domain.auth.FakeSessionStorage
import com.jvcs.tracky.core.domain.auth.authInfo
import com.jvcs.tracky.core.domain.sync.FakeLocalDataImportStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** Sync follows the session - none means the free, local-only tier - and waits for a pending import. */
class SessionCloudSyncGateTest {

    @Test
    fun withoutASessionSyncIsOff() =
        runTest {
            assertThat(SessionCloudSyncGate(FakeSessionStorage(), FakeLocalDataImportStore()).isEnabled()).isFalse()
        }

    @Test
    fun withASessionSyncIsOn() =
        runTest {
            assertThat(
                SessionCloudSyncGate(FakeSessionStorage(authInfo()), FakeLocalDataImportStore()).isEnabled(),
            ).isTrue()
        }

    @Test
    fun aSessionWithABlankAccessTokenDoesNotCount() =
        runTest {
            assertThat(
                SessionCloudSyncGate(
                    FakeSessionStorage(authInfo(accessToken = " ")),
                    FakeLocalDataImportStore(),
                ).isEnabled(),
            ).isFalse()
        }

    @Test
    fun signingInAndOutSwitchesSyncAndOnlyChangesAreEmitted() =
        runTest {
            val sessionStorage = FakeSessionStorage()

            SessionCloudSyncGate(sessionStorage, FakeLocalDataImportStore()).observeIsEnabled().test {
                assertThat(awaitItem()).isFalse()
                sessionStorage.set(authInfo())
                assertThat(awaitItem()).isTrue()
                // A refreshed token is still a session: no second "on".
                sessionStorage.set(authInfo(accessToken = "refreshed"))
                sessionStorage.set(null)
                assertThat(awaitItem()).isFalse()
            }
        }

    @Test
    fun aPendingImportHoldsSyncOffUntilItLands() =
        runTest {
            val importStore = FakeLocalDataImportStore(initial = true)
            val gate = SessionCloudSyncGate(FakeSessionStorage(authInfo()), importStore)
            assertThat(gate.isEnabled()).isFalse()

            importStore.setPending(false)

            assertThat(gate.isEnabled()).isTrue()
        }
}

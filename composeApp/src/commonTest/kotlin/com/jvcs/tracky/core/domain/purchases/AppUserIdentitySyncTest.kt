package com.jvcs.tracky.core.domain.purchases

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import com.jvcs.tracky.core.domain.auth.FakeSessionStorage
import com.jvcs.tracky.core.domain.auth.authInfo
import com.jvcs.tracky.core.domain.identity.FakeAppUserIdStore
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** The RevenueCat customer follows the account, and an account created here keeps the local id. */
class AppUserIdentitySyncTest {

    private val sessionStorage = FakeSessionStorage()
    private val appUserIdStore = FakeAppUserIdStore(id = "local-id")
    private val repository = FakeProAccessRepository()

    // runCurrent, not advanceUntilIdle: the sync collects on backgroundScope, which
    // advanceUntilIdle does not wait for.
    private fun TestScope.startSync() {
        AppUserIdentitySync(sessionStorage, appUserIdStore, repository, backgroundScope).start()
        runCurrent()
    }

    @Test
    fun aLaunchWithoutASessionKeepsTheLocalId() =
        runTest {
            startSync()

            assertThat(repository.identities).isEmpty()
            assertThat(appUserIdStore.id).isEqualTo("local-id")
        }

    @Test
    fun anAccountRegisteredWithTheLocalIdChangesNothing() =
        runTest {
            startSync()

            sessionStorage.set(authInfo(userId = "local-id"))
            runCurrent()

            assertThat(repository.identities).isEmpty()
            assertThat(repository.syncCalls).isEqualTo(0)
        }

    @Test
    fun signingInToAnExistingAccountAdoptsItsIdAndMovesPurchases() =
        runTest {
            startSync()

            sessionStorage.set(authInfo(userId = "account-id"))
            runCurrent()

            assertThat(appUserIdStore.id).isEqualTo("account-id")
            assertThat(repository.identities).containsExactly("account-id")
            assertThat(repository.syncCalls).isEqualTo(1)
        }

    @Test
    fun aRestoredSessionWithTheStoredIdChangesNothing() =
        runTest {
            appUserIdStore.id = "account-id"
            sessionStorage.set(authInfo(userId = "account-id"))

            startSync()

            assertThat(repository.identities).isEmpty()
        }

    @Test
    fun aTokenRefreshChangesNothing() =
        runTest {
            startSync()

            sessionStorage.set(authInfo(userId = "account-id"))
            runCurrent()
            sessionStorage.set(authInfo(userId = "account-id", accessToken = "refreshed"))
            runCurrent()

            assertThat(repository.identities).containsExactly("account-id")
            assertThat(repository.syncCalls).isEqualTo(1)
        }

    @Test
    fun signingOutSwitchesToAFreshId() =
        runTest {
            startSync()

            sessionStorage.set(authInfo(userId = "local-id"))
            runCurrent()
            sessionStorage.set(null)
            runCurrent()

            assertThat(appUserIdStore.id).isNotEqualTo("local-id")
            assertThat(Uuid.parse(appUserIdStore.id).toString()).isEqualTo(appUserIdStore.id)
            assertThat(repository.identities).containsExactly(appUserIdStore.id)
        }

    @Test
    fun aFailedSwitchKeepsTheOldIdStoredAndIsRetried() =
        runTest {
            repository.identifyResults += Result.Error(DataError.Remote.NO_INTERNET)
            startSync()

            sessionStorage.set(authInfo(userId = "account-id"))
            runCurrent()

            // RevenueCat is still on the local customer, and the store says so.
            assertThat(appUserIdStore.id).isEqualTo("local-id")
            assertThat(repository.syncCalls).isEqualTo(0)

            advanceTimeBy(5.seconds)
            runCurrent()

            assertThat(repository.identities).containsExactly("account-id", "account-id")
            assertThat(appUserIdStore.id).isEqualTo("account-id")
            assertThat(repository.syncCalls).isEqualTo(1)
        }

    @Test
    fun aLogoutStoresTheFreshIdEvenBeforeTheSwitchSucceeds() =
        runTest {
            startSync()
            sessionStorage.set(authInfo(userId = "local-id"))
            runCurrent()
            repository.identifyResults += Result.Error(DataError.Remote.NO_INTERNET)

            sessionStorage.set(null)
            runCurrent()

            // Killed now, the next launch configures RevenueCat with the fresh id, not the old account's.
            val freshId = appUserIdStore.id
            assertThat(freshId).isNotEqualTo("local-id")

            advanceTimeBy(5.seconds)
            runCurrent()

            assertThat(repository.identities).containsExactly(freshId, freshId)
        }

    @Test
    fun aNewerSessionChangeAbandonsAPendingSwitch() =
        runTest {
            repository.identifyResults += Result.Error(DataError.Remote.NO_INTERNET)
            startSync()
            sessionStorage.set(authInfo(userId = "account-id"))
            runCurrent()

            sessionStorage.set(authInfo(userId = "local-id"))
            advanceTimeBy(60.seconds)
            runCurrent()

            assertThat(repository.identities).containsExactly("account-id")
            assertThat(appUserIdStore.id).isEqualTo("local-id")
        }

    @Test
    fun retriesBackOffFromFiveSecondsAndLevelOffAtFiveMinutes() =
        runTest {
            repeat(7) { repository.identifyResults += Result.Error(DataError.Remote.NO_INTERNET) }
            startSync()
            sessionStorage.set(authInfo(userId = "account-id"))
            runCurrent()
            val start = currentTime

            // 5 s, doubling each time, until the 320 s step is capped at 300 s.
            listOf(5, 15, 35, 75, 155, 315, 615).forEachIndexed { retry, dueSeconds ->
                val due = start + dueSeconds * 1_000L
                advanceTimeBy((due - 1 - currentTime).milliseconds)
                runCurrent()
                assertThat(repository.identities).hasSize(retry + 1)

                advanceTimeBy(1.milliseconds)
                runCurrent()
                assertThat(repository.identities).hasSize(retry + 2)
            }
            assertThat(appUserIdStore.id).isEqualTo("account-id")
        }
}

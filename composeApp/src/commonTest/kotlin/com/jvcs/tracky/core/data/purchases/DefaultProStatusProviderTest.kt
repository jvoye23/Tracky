package com.jvcs.tracky.core.data.purchases

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.jvcs.tracky.core.domain.auth.FakeSessionStorage
import com.jvcs.tracky.core.domain.auth.authInfo
import com.jvcs.tracky.core.domain.purchases.FakeEntitlementService
import com.jvcs.tracky.core.domain.purchases.FakeProAccessRepository
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * The store decides first; the server only for an account the store calls free, since the server
 * can grant Pro that RevenueCat never sees (grandfathered, trial, manual).
 */
internal class DefaultProStatusProviderTest {

    private val store = FakeProAccessRepository()
    private val server = FakeEntitlementService()

    private fun provider(signedIn: Boolean) =
        DefaultProStatusProvider(
            proAccessRepository = store,
            entitlementService = server,
            sessionStorage = FakeSessionStorage(if (signedIn) authInfo() else null),
        )

    @Test
    fun theStoreSaysPro_soItIsPro_withoutAskingTheServer() =
        runTest {
            store.result = Result.Success(true)

            assertThat(provider(signedIn = true).isPro()).isTrue()
            assertThat(server.currentCalls).isEqualTo(0)
        }

    @Test
    fun signedOutAndFreeInTheStore_isFree_withoutAskingTheServer() =
        runTest {
            store.result = Result.Success(false)
            server.currentResult = Result.Success(true)

            assertThat(provider(signedIn = false).isPro()).isFalse()
            assertThat(server.currentCalls).isEqualTo(0)
        }

    @Test
    fun signedInAndFreeInTheStore_butProOnTheServer_isPro() =
        runTest {
            store.result = Result.Success(false)
            server.currentResult = Result.Success(true)

            assertThat(provider(signedIn = true).isPro()).isTrue()
        }

    @Test
    fun signedInAndFreeEverywhere_isFree() =
        runTest {
            store.result = Result.Success(false)
            server.currentResult = Result.Success(false)

            assertThat(provider(signedIn = true).isPro()).isFalse()
        }

    @Test
    fun theStoreFailing_stillAsksTheServer() =
        runTest {
            store.result = Result.Error(DataError.Remote.NO_INTERNET)
            server.currentResult = Result.Success(true)

            assertThat(provider(signedIn = true).isPro()).isTrue()
        }

    @Test
    fun bothFailing_isFree() =
        runTest {
            store.result = Result.Error(DataError.Remote.NO_INTERNET)
            server.currentResult = Result.Error(DataError.Remote.NO_INTERNET)

            assertThat(provider(signedIn = true).isPro()).isFalse()
        }

    // proStatus: false only when every source asked answered free; otherwise the failure.

    @Test
    fun proStatus_signedInAndFreeEverywhere_isKnownFree() =
        runTest {
            store.result = Result.Success(false)
            server.currentResult = Result.Success(false)

            assertThat(provider(signedIn = true).proStatus()).isEqualTo(Result.Success(false))
        }

    @Test
    fun proStatus_theStoreFailingButTheServerSayingPro_isKnownPro() =
        runTest {
            store.result = Result.Error(DataError.Remote.NO_INTERNET)
            server.currentResult = Result.Success(true)

            assertThat(provider(signedIn = true).proStatus()).isEqualTo(Result.Success(true))
        }

    @Test
    fun proStatus_theServerFailingForAnAccountTheStoreCallsFree_isUnknown() =
        runTest {
            store.result = Result.Success(false)
            server.currentResult = Result.Error(DataError.Remote.SERVER_ERROR)

            assertThat(provider(signedIn = true).proStatus())
                .isEqualTo(Result.Error(DataError.Remote.SERVER_ERROR))
        }

    @Test
    fun proStatus_theStoreFailingAndTheServerSayingFree_isUnknown() =
        runTest {
            store.result = Result.Error(DataError.Remote.NO_INTERNET)
            server.currentResult = Result.Success(false)

            assertThat(provider(signedIn = true).proStatus())
                .isEqualTo(Result.Error(DataError.Remote.NO_INTERNET))
        }

    @Test
    fun proStatus_signedOutWithTheStoreFailing_isUnknown_withoutAskingTheServer() =
        runTest {
            store.result = Result.Error(DataError.Remote.NO_INTERNET)

            assertThat(provider(signedIn = false).proStatus())
                .isEqualTo(Result.Error(DataError.Remote.NO_INTERNET))
            assertThat(server.currentCalls).isEqualTo(0)
        }
}

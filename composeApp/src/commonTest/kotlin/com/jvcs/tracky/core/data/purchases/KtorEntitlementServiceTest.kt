package com.jvcs.tracky.core.data.purchases

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jvcs.tracky.core.data.networking.SentRequest
import com.jvcs.tracky.core.data.networking.mockHttpClient
import com.jvcs.tracky.core.data.networking.respondJson
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.FakeTimeProvider
import com.jvcs.tracky.core.domain.util.Result
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Instant

/**
 * The refresh asks the server to re-read the entitlement from RevenueCat
 * (Requirements/backend-entitlement-refresh-api.md §2) and reports whether it now says Pro. The
 * current read returns what the server has stored (Requirements/backend-pro-entitlement-api.md §2).
 */
internal class KtorEntitlementServiceTest {

    private val sent = mutableListOf<SentRequest>()

    private val clock = FakeTimeProvider(now = Instant.parse("2026-10-12T12:00:00Z"))

    private fun service(json: String = "{}", status: HttpStatusCode = HttpStatusCode.OK) =
        KtorEntitlementService(mockHttpClient(sent) { respondJson(json, status) }, clock)

    @Test
    fun refreshPostsToTheRefreshRoute() =
        runTest {
            service(PRO_LIFETIME).refresh()

            assertThat(sent.map { it.method to it.path })
                .isEqualTo(listOf(HttpMethod.Post to "/api/entitlement/refresh"))
        }

    @Test
    fun aLifetimeProAnswerIsPro() =
        runTest {
            assertThat(service(PRO_LIFETIME).refresh()).isEqualTo(Result.Success(true))
        }

    @Test
    fun aFreeAnswerIsNotPro() =
        runTest {
            val free = """{"entitlement":"free","entitlementExpiresAt":null,"isGrandfathered":false}"""

            assertThat(service(free).refresh()).isEqualTo(Result.Success(false))
        }

    @Test
    fun aGrandfatheredAccountIsProWhateverItsColumnSays() =
        runTest {
            val grandfathered = """{"entitlement":"free","entitlementExpiresAt":null,"isGrandfathered":true}"""

            assertThat(service(grandfathered).refresh()).isEqualTo(Result.Success(true))
        }

    @Test
    fun theRateLimitAndAnUnreachableRevenueCatAreErrors() =
        runTest {
            val results =
                listOf(HttpStatusCode.TooManyRequests, HttpStatusCode.ServiceUnavailable)
                    .map { status -> service(status = status).refresh() }

            assertThat(results).isEqualTo(
                listOf(
                    Result.Error(DataError.Remote.TOO_MANY_REQUESTS),
                    Result.Error(DataError.Remote.SERVICE_UNAVAILABLE),
                ),
            )
        }

    @Test
    fun currentReadsTheStoredEntitlement() =
        runTest {
            val result = service(PRO_LIFETIME).current()

            assertThat(sent.map { it.method to it.path }).isEqualTo(listOf(HttpMethod.Get to "/api/entitlement"))
            assertThat(result).isEqualTo(Result.Success(true))
        }

    @Test
    fun currentReadsFreeAndGrandfatheredLikeRefresh() =
        runTest {
            val free = """{"entitlement":"free","entitlementExpiresAt":null,"isGrandfathered":false}"""
            val grandfathered = """{"entitlement":"free","entitlementExpiresAt":null,"isGrandfathered":true}"""

            assertThat(listOf(service(free).current(), service(grandfathered).current()))
                .isEqualTo(listOf(Result.Success(false), Result.Success(true)))
        }

    @Test
    fun aProEntitlementPastItsExpiry_isNotPro() =
        runTest {
            // A lapsed trial: the server keeps the row at `pro` and evaluates the expiry on read.
            val trial = proUntil("2026-10-12T13:00:00Z", isGrandfathered = false)

            assertThat(service(trial).current()).isEqualTo(Result.Success(true))
            clock.now = Instant.parse("2026-10-12T13:00:00Z")
            assertThat(service(trial).current()).isEqualTo(Result.Success(false))
            assertThat(service(trial).refresh()).isEqualTo(Result.Success(false))
        }

    @Test
    fun aGrandfatheredAccountPastAnExpiry_isStillPro() =
        runTest {
            val lapsed = proUntil("2020-01-01T00:00:00Z", isGrandfathered = true)

            assertThat(service(lapsed).current()).isEqualTo(Result.Success(true))
        }

    @Test
    fun currentFailsOnAnError() =
        runTest {
            assertThat(service(status = HttpStatusCode.ServiceUnavailable).current())
                .isEqualTo(Result.Error(DataError.Remote.SERVICE_UNAVAILABLE))
        }

    private fun proUntil(expiresAt: String, isGrandfathered: Boolean) =
        """{"entitlement":"pro","entitlementExpiresAt":"$expiresAt","isGrandfathered":$isGrandfathered}"""

    private companion object {
        const val PRO_LIFETIME = """{"entitlement":"pro","entitlementExpiresAt":null,"isGrandfathered":false}"""
    }
}

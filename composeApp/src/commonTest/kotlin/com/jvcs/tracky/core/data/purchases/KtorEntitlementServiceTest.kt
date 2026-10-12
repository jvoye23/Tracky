package com.jvcs.tracky.core.data.purchases

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jvcs.tracky.core.data.networking.SentRequest
import com.jvcs.tracky.core.data.networking.mockHttpClient
import com.jvcs.tracky.core.data.networking.respondJson
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * The refresh asks the server to re-read the entitlement from RevenueCat
 * (Requirements/backend-entitlement-refresh-api.md §2) and reports whether it now says Pro.
 */
internal class KtorEntitlementServiceTest {

    private val sent = mutableListOf<SentRequest>()

    private fun service(json: String = "{}", status: HttpStatusCode = HttpStatusCode.OK) =
        KtorEntitlementService(mockHttpClient(sent) { respondJson(json, status) })

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

    private companion object {
        const val PRO_LIFETIME = """{"entitlement":"pro","entitlementExpiresAt":null,"isGrandfathered":false}"""
    }
}

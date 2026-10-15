package com.jvcs.tracky.core.data.purchases

import com.jvcs.tracky.core.data.dto.EntitlementDto
import com.jvcs.tracky.core.data.networking.constructRoute
import com.jvcs.tracky.core.data.networking.get
import com.jvcs.tracky.core.data.networking.safeCall
import com.jvcs.tracky.core.domain.purchases.EntitlementService
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.core.domain.util.map
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.url
import kotlin.time.Instant

/**
 * Talks to `GET /api/entitlement` (Requirements/backend-pro-entitlement-api.md §2) and
 * `POST /api/entitlement/refresh` (Requirements/backend-entitlement-refresh-api.md §2).
 */
class KtorEntitlementService(private val httpClient: HttpClient, private val timeProvider: TimeProvider) :
    EntitlementService {

    // The shared post helper needs a body, and this route takes none.
    override suspend fun refresh(): Result<Boolean, DataError.Remote> =
        safeCall<EntitlementDto> {
            httpClient.post { url(constructRoute(REFRESH_ROUTE)) }
        }.map { it.isPro() }

    override suspend fun current(): Result<Boolean, DataError.Remote> =
        httpClient.get<EntitlementDto>(route = ENTITLEMENT_ROUTE).map { it.isPro() }

    /**
     * The Pro rule of Requirements/backend-pro-entitlement-api.md §1. The server evaluates expiry on
     * read and never flips a lapsed row (§5), so a trial that ran out still comes back as `pro`
     * with a past expiry. A null expiry is lifetime; one the client cannot parse is left to the
     * server's word. A grandfathered account is Pro whatever its entitlement column says.
     */
    private fun EntitlementDto.isPro(): Boolean {
        if (isGrandfathered) return true
        if (entitlement != PRO) return false
        val expiresAt = entitlementExpiresAt?.let { Instant.parseOrNull(it) } ?: return true
        return expiresAt > timeProvider.nowInstant
    }

    private companion object {
        const val ENTITLEMENT_ROUTE = "/api/entitlement"
        const val REFRESH_ROUTE = "/api/entitlement/refresh"
        const val PRO = "pro"
    }
}

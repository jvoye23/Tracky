package com.jvcs.tracky.core.data.purchases

import com.jvcs.tracky.core.data.dto.EntitlementDto
import com.jvcs.tracky.core.data.networking.constructRoute
import com.jvcs.tracky.core.data.networking.safeCall
import com.jvcs.tracky.core.domain.purchases.EntitlementService
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.map
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.url

/** Talks to `POST /api/entitlement/refresh` (Requirements/backend-entitlement-refresh-api.md §2). */
class KtorEntitlementService(private val httpClient: HttpClient) : EntitlementService {

    // The shared post helper needs a body, and this route takes none.
    override suspend fun refresh(): Result<Boolean, DataError.Remote> =
        safeCall<EntitlementDto> {
            httpClient.post { url(constructRoute(REFRESH_ROUTE)) }
        }.map { it.isPro() }

    // A grandfathered account is Pro whatever its entitlement column says.
    private fun EntitlementDto.isPro(): Boolean = entitlement == PRO || isGrandfathered

    private companion object {
        const val REFRESH_ROUTE = "/api/entitlement/refresh"
        const val PRO = "pro"
    }
}

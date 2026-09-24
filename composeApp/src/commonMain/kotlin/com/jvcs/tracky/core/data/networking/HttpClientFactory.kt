package com.jvcs.tracky.core.data.networking

import co.touchlab.kermit.Logger
import com.jvcs.tracky.core.data.dto.AuthInfoDto
import com.jvcs.tracky.core.data.dto.requests.RefreshRequest
import com.jvcs.tracky.core.data.mappers.toDomain
import com.jvcs.tracky.core.domain.auth.SessionStorage
import com.jvcs.tracky.core.domain.util.onFailure
import com.jvcs.tracky.core.domain.util.onSuccess
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerAuthConfig
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.RefreshTokensParams
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json

class HttpClientFactory(private val sessionStorage: SessionStorage) {

    fun create(engine: HttpClientEngine): HttpClient =
        HttpClient(engine) {
            install(ContentNegotiation) {
                json(
                    json =
                        Json {
                            ignoreUnknownKeys = true
                        },
                )
            }
            install(Logging) {
                logger =
                    object : io.ktor.client.plugins.logging.Logger {
                        override fun log(message: String) {
                            Logger.withTag("HTTP").d(message)
                        }
                    }
                level = LogLevel.ALL
            }
            // No pingInterval here on purpose. The server already pings every thirty seconds and
            // drops a socket that misses two, and the plugin's own setting is not honoured by the
            // engines that own the WS protocol themselves (OkHttp, Darwin) — configuring it here
            // would buy a false sense of half-open detection rather than the real thing.
            install(WebSockets)
            defaultRequest {
                contentType(ContentType.Application.Json)
            }
            install(Auth) {
                bearer { loadAndRefreshFromSession() }
            }
        }

    private fun BearerAuthConfig.loadAndRefreshFromSession() {
        sendWithoutRequest { request ->
            !request.url.buildString().contains("/api/auth/")
        }
        loadTokens {
            sessionStorage.observeAuthInfo().firstOrNull()?.let {
                BearerTokens(
                    accessToken = it.accessToken,
                    refreshToken = it.refreshToken,
                )
            }
        }
        refreshTokens { refreshSession() }
    }

    /** Trades the stored refresh token for new tokens. Null, with the session cleared, when it can't. */
    private suspend fun RefreshTokensParams.refreshSession(): BearerTokens? {
        // Never refresh on 401 from /api/auth/* (e.g. wrong password on login)
        // — otherwise this would loop indefinitely.
        if (response.call.request.url.encodedPath
                .startsWith("/api/auth/")
        ) {
            return null
        }

        val authInfo = sessionStorage.observeAuthInfo().firstOrNull()
        if (authInfo?.refreshToken.isNullOrBlank()) {
            sessionStorage.set(null)
            return null
        }

        var bearerTokens: BearerTokens? = null
        client
            .post<RefreshRequest, AuthInfoDto>(
                route = "/api/auth/refresh",
                body = RefreshRequest(refreshToken = authInfo.refreshToken),
                builder = { markAsRefreshTokenRequest() },
            ).onSuccess { newAuthInfo ->
                val newAuthInfoDomain = newAuthInfo.toDomain()
                sessionStorage.set(newAuthInfoDomain)
                bearerTokens =
                    BearerTokens(
                        accessToken = newAuthInfo.accessToken,
                        refreshToken = newAuthInfo.refreshToken,
                    )
            }.onFailure {
                sessionStorage.set(null)
            }
        return bearerTokens
    }
}

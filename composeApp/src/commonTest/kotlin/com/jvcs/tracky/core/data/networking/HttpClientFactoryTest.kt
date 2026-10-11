package com.jvcs.tracky.core.data.networking

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.jvcs.tracky.core.domain.auth.FakeSessionStorage
import com.jvcs.tracky.core.domain.auth.authInfo
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** The app's one HTTP client: it signs requests with the session and renews the session on a 401. */
class HttpClientFactoryTest {

    private val requests = mutableListOf<HttpRequestData>()

    private val refreshed =
        """{"accessToken":"new-access","refreshToken":"new-refresh",""" +
            """"user":{"id":"user-1","email":"user@example.com","username":"user","hasVerifiedEmail":true}}"""

    private fun client(sessionStorage: FakeSessionStorage, refreshStatus: HttpStatusCode = HttpStatusCode.OK) =
        HttpClientFactory(sessionStorage).create(
            MockEngine { request ->
                requests += request
                val json = headersOf(HttpHeaders.ContentType, "application/json")
                when {
                    request.url.encodedPath == "/api/auth/refresh" -> {
                        respond(refreshed, refreshStatus, json)
                    }

                    request.headers[HttpHeaders.Authorization] == "Bearer new-access" -> {
                        respond(
                            "[]",
                            HttpStatusCode.OK,
                            json,
                        )
                    }

                    else -> {
                        respond("", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.WWWAuthenticate, "Bearer"))
                    }
                }
            },
        )

    private val HttpRequestData.bearer get() = headers[HttpHeaders.Authorization]

    @Test
    fun aRejectedTokenIsRenewedWithTheRefreshTokenAndTheRequestRetried() =
        runTest {
            val session = FakeSessionStorage(authInfo(accessToken = "old-access", refreshToken = "old-refresh"))

            val response = client(session).get("https://tracky.test/api/projects")

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            assertThat(requests.map { it.url.encodedPath to it.bearer }).containsExactly(
                "/api/projects" to "Bearer old-access",
                "/api/auth/refresh" to null,
                "/api/projects" to "Bearer new-access",
            )
            assertThat(session.current?.accessToken).isEqualTo("new-access")
            assertThat(session.current?.refreshToken).isEqualTo("new-refresh")
        }

    @Test
    fun aRefreshTheServerRefusesEndsTheSession() =
        runTest {
            val session = FakeSessionStorage(authInfo(accessToken = "old-access", refreshToken = "old-refresh"))

            val response =
                client(
                    session,
                    refreshStatus = HttpStatusCode.Unauthorized,
                ).get("https://tracky.test/api/projects")

            assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
            assertThat(session.current).isNull()
        }

    @Test
    fun aSessionWithoutARefreshTokenIsEndedWithoutAsking() =
        runTest {
            val session = FakeSessionStorage(authInfo(accessToken = "old-access", refreshToken = ""))

            client(session).get("https://tracky.test/api/projects")

            assertThat(requests.map { it.url.encodedPath }).containsExactly("/api/projects")
            assertThat(session.current).isNull()
        }

    @Test
    fun aRejectedLoginIsNeverAnsweredWithARefresh() =
        runTest {
            val session = FakeSessionStorage(authInfo(accessToken = "old-access", refreshToken = "old-refresh"))

            val response = client(session).post("https://tracky.test/api/auth/login")

            // A wrong password is a 401 too; refreshing on it would loop. (Ktor's bearer plugin does
            // retry the unsigned request once with the cached token before it gives up.)
            assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
            assertThat(requests.map { it.url.encodedPath }.distinct()).containsExactly("/api/auth/login")
            assertThat(requests.first().bearer).isNull()
            assertThat(session.current?.accessToken).isEqualTo("old-access")
        }
}

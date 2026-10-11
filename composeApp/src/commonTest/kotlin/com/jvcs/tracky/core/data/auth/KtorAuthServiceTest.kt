package com.jvcs.tracky.core.data.auth

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import com.jvcs.tracky.core.data.networking.SentRequest
import com.jvcs.tracky.core.data.networking.mockHttpClient
import com.jvcs.tracky.core.data.networking.respondJson
import com.jvcs.tracky.core.data.networking.respondNoContent
import com.jvcs.tracky.core.domain.identity.FakeAppUserIdStore
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.map
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

/**
 * Every auth route sends what the server needs. Sign-up also carries the app user id, so the server
 * creates the account with the RevenueCat customer's id (ADR 0005).
 */
internal class KtorAuthServiceTest {

    private val sent = mutableListOf<SentRequest>()

    private fun service(status: HttpStatusCode = HttpStatusCode.OK) =
        KtorAuthService(
            mockHttpClient(sent) { request ->
                when {
                    status != HttpStatusCode.OK -> respondJson("{}", status)

                    request.url.encodedPath.endsWith("login") ||
                        request.url.encodedPath.endsWith("register") ||
                        request.url.encodedPath.endsWith("google") ||
                        request.url.encodedPath.endsWith("apple") -> respondJson(AUTH_INFO_JSON)

                    else -> respondNoContent()
                }
            },
            appUserIdStore = FakeAppUserIdStore(id = "local-id"),
        )

    private fun sentJson(index: Int): JsonObject = Json.parseToJsonElement(sent[index].body).jsonObject

    @Test
    fun everySignInRouteReturnsTheSession() =
        runTest {
            val service = service()

            val results =
                listOf(
                    service.login("ada@example.com", "secret"),
                    service.register("ada@example.com", "Ada", "secret"),
                    service.loginWithGoogle("google-token"),
                    service.loginWithApple("apple-token"),
                ).map { result -> result.map { it.user.email } }

            assertThat(results).isEqualTo(List(4) { Result.Success("ada@example.com") })
            assertThat(sent.map { it.path })
                .isEqualTo(listOf("/api/auth/login", "/api/auth/register", "/api/auth/google", "/api/auth/apple"))
            assertThat(sent.first().body).contains("\"password\":\"secret\"")
            service.clearTokenCache()
        }

    @Test
    fun theAccountRoutesSendWhatTheServerNeeds() =
        runTest {
            val service = service()

            val results =
                listOf(
                    service.resendVerificationEmail("ada@example.com"),
                    service.verifyEmail("verify-token"),
                    service.forgotPassword("ada@example.com"),
                    service.resetPassword("NewSecret1!", "reset-token"),
                    service.logout("refresh-token"),
                )

            assertThat(results).isEqualTo(List(5) { Result.Success(Unit) })
            assertThat(sent.map { it.method to it.path }).isEqualTo(
                listOf(
                    HttpMethod.Post to "/api/auth/resend-verification",
                    HttpMethod.Get to "/api/auth/verify",
                    HttpMethod.Post to "/api/auth/forgot-password",
                    HttpMethod.Post to "/api/auth/reset-password",
                    HttpMethod.Post to "/api/auth/logout",
                ),
            )
            assertThat(sent[1].query).isEqualTo("token=verify-token")
        }

    @Test
    fun registerSendsTheAppUserId() =
        runTest {
            service().register(email = "ada@example.com", name = "Ada", password = "Secret@123")

            assertThat(sentJson(0)["id"]?.jsonPrimitive?.content).isEqualTo("local-id")
            assertThat(sentJson(0)["username"]?.jsonPrimitive?.content).isEqualTo("Ada")
        }

    @Test
    fun googleSignInSendsTheAppUserId() =
        runTest {
            service().loginWithGoogle(idToken = "google-token")

            assertThat(sentJson(0)["id"]?.jsonPrimitive?.content).isEqualTo("local-id")
            assertThat(sentJson(0)["idToken"]?.jsonPrimitive?.content).isEqualTo("google-token")
        }

    @Test
    fun appleSignInSendsTheAppUserId() =
        runTest {
            service().loginWithApple(idToken = "apple-token")

            assertThat(sentJson(0)["id"]?.jsonPrimitive?.content).isEqualTo("local-id")
        }

    @Test
    fun aRejectedLoginIsUnauthorized() =
        runTest {
            assertThat(service(HttpStatusCode.Unauthorized).login("ada@example.com", "wrong"))
                .isEqualTo(Result.Error(DataError.Remote.UNAUTHORIZED))
        }

    private companion object {
        const val AUTH_INFO_JSON =
            """{"accessToken":"a","refreshToken":"r","user":{"id":"u1","email":"ada@example.com","username":"Ada","hasVerifiedEmail":true}}"""
    }
}

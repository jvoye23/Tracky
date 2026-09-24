package com.jvcs.tracky.core.data.networking

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** What a data source sent: enough to assert on the route, the query and the JSON body. */
internal data class SentRequest(
    val method: HttpMethod,
    val path: String,
    val query: String,
    val body: String,
)

/**
 * An [HttpClient] configured like the app's, over a [MockEngine] that records every request and
 * answers with [respond].
 */
internal fun mockHttpClient(
    sent: MutableList<SentRequest> = mutableListOf(),
    respond: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): HttpClient =
    HttpClient(
        MockEngine { request ->
            sent +=
                SentRequest(
                    method = request.method,
                    path = request.url.encodedPath,
                    query = request.url.encodedQuery,
                    body = (request.body as? TextContent)?.text.orEmpty(),
                )
            respond(request)
        },
    ) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        defaultRequest { contentType(ContentType.Application.Json) }
        install(Auth) { bearer { loadTokens { BearerTokens("access", "refresh") } } }
    }

internal fun MockRequestHandleScope.respondJson(json: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(json, status, headersOf(HttpHeaders.ContentType, "application/json"))

internal fun MockRequestHandleScope.respondNoContent() = respond("", HttpStatusCode.NoContent)

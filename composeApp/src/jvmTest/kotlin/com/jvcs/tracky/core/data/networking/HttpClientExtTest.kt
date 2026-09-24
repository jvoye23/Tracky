package com.jvcs.tracky.core.data.networking

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jvcs.tracky.core.domain.util.DataError
import kotlin.test.Test

internal class HttpClientExtTest {

    @Test
    fun everyRouteShapeResolvesAgainstTheBaseUrl() {
        assertThat(constructRoute("/api/projects")).isEqualTo(ApiConfig.BASE_URL + "/api/projects")
        assertThat(constructRoute("api/projects")).isEqualTo(ApiConfig.BASE_URL + "/api/projects")
        assertThat(constructRoute(ApiConfig.BASE_URL + "/api/x")).isEqualTo(ApiConfig.BASE_URL + "/api/x")
        assertThat(constructRoute("https://elsewhere.example/api")).isEqualTo("https://elsewhere.example/api")
    }

    @Test
    fun eachStatusMapsToItsError() {
        val expected =
            mapOf(
                400 to DataError.Remote.BAD_REQUEST,
                401 to DataError.Remote.UNAUTHORIZED,
                403 to DataError.Remote.FORBIDDEN,
                404 to DataError.Remote.NOT_FOUND,
                408 to DataError.Remote.REQUEST_TIMEOUT,
                409 to DataError.Remote.CONFLICT,
                413 to DataError.Remote.PAYLOAD_TOO_LARGE,
                429 to DataError.Remote.TOO_MANY_REQUESTS,
                503 to DataError.Remote.SERVICE_UNAVAILABLE,
                500 to DataError.Remote.SERVER_ERROR,
                599 to DataError.Remote.SERVER_ERROR,
                418 to DataError.Remote.UNKNOWN,
            )

        assertThat(expected.mapValues { (status, _) -> httpStatusToRemoteError(status) }).isEqualTo(expected)
    }
}

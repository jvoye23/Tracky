package com.jvcs.tracky.core.data.networking

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.jvcs.tracky.core.data.networking.dto.HelloEnvelopeDto
import com.jvcs.tracky.core.data.realtime.KtorRealtimeChannel
import com.jvcs.tracky.core.data.realtime.RealtimeEnvelopeParser
import com.jvcs.tracky.core.domain.realtime.RealtimeEvent
import com.jvcs.tracky.core.domain.util.Result
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondBadRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test

internal class RealtimeUrlTest {

    @Test
    fun httpsBecomesWss() {
        assertThat(
            realtimeUrl("https://tracky.jv-coding-solutions.com:443"),
        ).isEqualTo("wss://tracky.jv-coding-solutions.com/api/realtime")
    }

    /** 443 is wss's default and drops out; a dev port is not and must survive. */
    @Test
    fun aNonDefaultPortSurvives() {
        assertThat(realtimeUrl("http://localhost:8080")).isEqualTo("ws://localhost:8080/api/realtime")
    }

    /**
     * ApiConfig.BASE_URL is generated from local.properties and is empty on a fresh clone or in CI.
     * Null means "realtime is unavailable", which keeps the connection idle rather than retrying
     * against a garbage URL forever.
     */
    @Test
    fun aBlankBaseUrlHasNoRealtimeAddress() {
        assertThat(realtimeUrl("")).isNull()
        assertThat(realtimeUrl("   ")).isNull()
    }

    @Test
    fun anUnexpectedSchemeIsRefusedRatherThanGuessedAt() {
        assertThat(realtimeUrl("ftp://example.com")).isNull()
    }

    @Test
    fun aBasePathOnTheRestUrlIsReplacedNotAppended() {
        assertThat(realtimeUrl("https://example.com/api/v1")).isEqualTo("wss://example.com/api/realtime")
    }
}

internal class RealtimeEnvelopeParserTest {

    private val parser = RealtimeEnvelopeParser(Json { ignoreUnknownKeys = true })

    @Test
    fun readsTheThreeKnownTypes() {
        assertThat(parser.parse("""{"type":"ready"}""")).isEqualTo(RealtimeEvent.Ready)
        assertThat(parser.parse("""{"type":"invalidate","cursor":84219}""")).isEqualTo(RealtimeEvent.Invalidate(84219))
        assertThat(
            parser.parse("""{"type":"timer","cursor":84219,"active":null}"""),
        ).isEqualTo(RealtimeEvent.Timer(84219))
    }

    /** The fat timer envelope carries rows we deliberately ignore; it must still parse. */
    @Test
    fun aTimerEnvelopeWithInlineStateStillParses() {
        val frame = """{"type":"timer","cursor":7,"active":{"intervalId":"i1","kind":"task"}}"""
        assertThat(parser.parse(frame)).isEqualTo(RealtimeEvent.Timer(7))
    }

    /**
     * The reason this is not a sealed @Serializable hierarchy. ignoreUnknownKeys covers an unknown
     * field, not an unknown discriminator value, so a fourth message type would otherwise make
     * every old client throw on it.
     */
    @Test
    fun anUnknownTypeIsCarriedRatherThanThrown() {
        val event = parser.parse("""{"type":"somethingNewInV2","cursor":1}""")
        assertThat(event).isEqualTo(RealtimeEvent.Unknown("somethingNewInV2"))
    }

    @Test
    fun unknownExtraFieldsAreIgnored() {
        assertThat(
            parser.parse("""{"type":"invalidate","cursor":3,"somethingNew":{"a":1}}"""),
        ).isEqualTo(RealtimeEvent.Invalidate(3))
    }

    /** A cursor we cannot read is not a reason to fail: we pull from our own anyway. */
    @Test
    fun aMissingOrUnreadableCursorDefaultsToZero() {
        assertThat(parser.parse("""{"type":"invalidate"}""")).isEqualTo(RealtimeEvent.Invalidate(0))
        assertThat(
            parser.parse("""{"type":"invalidate","cursor":"not-a-number"}"""),
        ).isEqualTo(RealtimeEvent.Invalidate(0))
    }

    /** Null, not an exception: a frame we cannot read must never drop a healthy connection. */
    @Test
    fun rubbishIsIgnoredRatherThanThrown() {
        assertThat(parser.parse("not json at all")).isNull()
        assertThat(parser.parse("")).isNull()
        assertThat(parser.parse("[1,2,3]")).isNull()
        assertThat(parser.parse("""{"cursor":5}""")).isNull()
    }

    @Test
    fun theHelloEnvelopeCarriesTheDeviceAndCursor() {
        val json =
            Json.encodeToString(
                HelloEnvelopeDto(deviceId = "d-1", cursor = 84213),
            )
        assertThat(json.contains(""""type":"hello""""), name = json).isTrue()
        assertThat(json.contains(""""deviceId":"d-1""""), name = json).isTrue()
        assertThat(json.contains(""""cursor":84213"""), name = json).isTrue()
    }
}

internal class KtorRealtimeChannelTest {

    /**
     * A device with no base URL configured must not open anything. An error rather than a throw
     * keeps the connection's retry loop in one shape, and it reads as "realtime is unavailable
     * here" rather than something to back off and retry forever.
     */
    @Test
    fun withoutAConfiguredUrlItRefusesToOpen() =
        runTest {
            // Never reached: the null url short-circuits before the client is touched.
            val client = HttpClient(MockEngine { respondBadRequest() })

            val result = KtorRealtimeChannel(httpClient = client, url = null).open()

            assertThat(result is Result.Error).isTrue()
        }
}

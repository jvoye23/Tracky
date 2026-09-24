package com.jvcs.tracky.core.data.networking

import com.jvcs.tracky.core.data.networking.dto.HelloEnvelopeDto
import com.jvcs.tracky.core.data.networking.dto.RealtimeEnvelopeParser
import com.jvcs.tracky.core.data.realtime.KtorRealtimeChannel
import com.jvcs.tracky.core.domain.realtime.RealtimeEvent
import com.jvcs.tracky.core.domain.util.Result
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondBadRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class RealtimeUrlTest {

    @Test
    fun httpsBecomesWss() {
        assertEquals(
            "wss://tracky.jv-coding-solutions.com/api/realtime",
            realtimeUrl("https://tracky.jv-coding-solutions.com:443"),
        )
    }

    /** 443 is wss's default and drops out; a dev port is not and must survive. */
    @Test
    fun aNonDefaultPortSurvives() {
        assertEquals("ws://localhost:8080/api/realtime", realtimeUrl("http://localhost:8080"))
    }

    /**
     * ApiConfig.BASE_URL is generated from local.properties and is empty on a fresh clone or in CI.
     * Null means "realtime is unavailable", which keeps the connection idle rather than retrying
     * against a garbage URL forever.
     */
    @Test
    fun aBlankBaseUrlHasNoRealtimeAddress() {
        assertNull(realtimeUrl(""))
        assertNull(realtimeUrl("   "))
    }

    @Test
    fun anUnexpectedSchemeIsRefusedRatherThanGuessedAt() {
        assertNull(realtimeUrl("ftp://example.com"))
    }

    @Test
    fun aBasePathOnTheRestUrlIsReplacedNotAppended() {
        assertEquals("wss://example.com/api/realtime", realtimeUrl("https://example.com/api/v1"))
    }
}

internal class RealtimeEnvelopeParserTest {

    private val parser = RealtimeEnvelopeParser(Json { ignoreUnknownKeys = true })

    @Test
    fun readsTheThreeKnownTypes() {
        assertEquals(RealtimeEvent.Ready, parser.parse("""{"type":"ready"}"""))
        assertEquals(
            RealtimeEvent.Invalidate(84219),
            parser.parse("""{"type":"invalidate","cursor":84219}"""),
        )
        assertEquals(
            RealtimeEvent.Timer(84219),
            parser.parse("""{"type":"timer","cursor":84219,"active":null}"""),
        )
    }

    /** The fat timer envelope carries rows we deliberately ignore; it must still parse. */
    @Test
    fun aTimerEnvelopeWithInlineStateStillParses() {
        val frame = """{"type":"timer","cursor":7,"active":{"intervalId":"i1","kind":"task"}}"""
        assertEquals(RealtimeEvent.Timer(7), parser.parse(frame))
    }

    /**
     * The reason this is not a sealed @Serializable hierarchy. ignoreUnknownKeys covers an unknown
     * field, not an unknown discriminator value, so a fourth message type would otherwise make
     * every old client throw on it.
     */
    @Test
    fun anUnknownTypeIsCarriedRatherThanThrown() {
        val event = parser.parse("""{"type":"somethingNewInV2","cursor":1}""")
        assertEquals(RealtimeEvent.Unknown("somethingNewInV2"), event)
    }

    @Test
    fun unknownExtraFieldsAreIgnored() {
        assertEquals(
            RealtimeEvent.Invalidate(3),
            parser.parse("""{"type":"invalidate","cursor":3,"somethingNew":{"a":1}}"""),
        )
    }

    /** A cursor we cannot read is not a reason to fail: we pull from our own anyway. */
    @Test
    fun aMissingOrUnreadableCursorDefaultsToZero() {
        assertEquals(RealtimeEvent.Invalidate(0), parser.parse("""{"type":"invalidate"}"""))
        assertEquals(
            RealtimeEvent.Invalidate(0),
            parser.parse("""{"type":"invalidate","cursor":"not-a-number"}"""),
        )
    }

    /** Null, not an exception: a frame we cannot read must never drop a healthy connection. */
    @Test
    fun rubbishIsIgnoredRatherThanThrown() {
        assertNull(parser.parse("not json at all"))
        assertNull(parser.parse(""))
        assertNull(parser.parse("[1,2,3]"))
        assertNull(parser.parse("""{"cursor":5}"""))
    }

    @Test
    fun theHelloEnvelopeCarriesTheDeviceAndCursor() {
        val json =
            Json.encodeToString(
                HelloEnvelopeDto(deviceId = "d-1", cursor = 84213),
            )
        assertTrue(json.contains(""""type":"hello""""), json)
        assertTrue(json.contains(""""deviceId":"d-1""""), json)
        assertTrue(json.contains(""""cursor":84213"""), json)
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

            assertTrue(result is Result.Error)
        }
}

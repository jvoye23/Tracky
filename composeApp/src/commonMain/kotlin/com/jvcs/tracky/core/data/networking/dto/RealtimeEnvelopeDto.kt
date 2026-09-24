@file:OptIn(ExperimentalSerializationApi::class)

package com.jvcs.tracky.core.data.networking.dto

import co.touchlab.kermit.Logger
import com.jvcs.tracky.core.domain.realtime.RealtimeEvent
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * The one thing this client sends: who it is and how far it has read.
 *
 * The cursor is `0` rather than absent when this device has never pulled — the server compares it
 * against its own maximum to decide whether to follow `ready` with an `invalidate`, and zero makes
 * that comparison true, which is what a never-pulled device wants.
 */
@Serializable
data class HelloEnvelopeDto(
    val deviceId: String,
    val cursor: Long,
    /**
     * `@EncodeDefault` is load-bearing: kotlinx omits a property that still holds its default, so
     * without it the handshake goes out with no `type` at all and the server cannot read it.
     */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val type: String = "hello",
)

/**
 * Turns a text frame into a [RealtimeEvent].
 *
 * Deliberately **not** a `@Serializable` sealed hierarchy with a class discriminator:
 * `ignoreUnknownKeys` covers an unknown *field*, not an unknown *discriminator value*, so the day
 * the backend adds a fourth message type every old client would start throwing on it. Reading the
 * `type` string off a [JsonObject] and dispatching costs a few lines and cannot fail that way.
 */
class RealtimeEnvelopeParser(private val json: Json) {

    /** Null means "could not read it" — log and carry on; never a reason to drop the socket. */
    fun parse(frame: String): RealtimeEvent? =
        try {
            val obj = json.parseToJsonElement(frame).jsonObject
            when (val type = obj["type"]?.jsonPrimitive?.contentOrNull) {
                "ready" -> RealtimeEvent.Ready
                "invalidate" -> RealtimeEvent.Invalidate(obj.cursor())
                "timer" -> RealtimeEvent.Timer(obj.cursor())
                null -> null
                else -> RealtimeEvent.Unknown(type)
            }
        } catch (exception: SerializationException) {
            Logger
                .withTag(
                    "RealtimeEnvelopeDto",
                ).w(exception) { "parse: ignored unreadable input (SerializationException)" }
            null
        } catch (exception: IllegalArgumentException) {
            Logger
                .withTag(
                    "RealtimeEnvelopeDto",
                ).w(exception) { "parse: ignored unreadable input (IllegalArgumentException)" }
            // jsonObject / jsonPrimitive throw this when the frame is well-formed JSON of the wrong
            // shape — a bare array, say. Same answer: ignore the frame, keep the connection.
            null
        }

    private fun JsonObject.cursor(): Long = this["cursor"]?.jsonPrimitive?.longOrNull ?: 0L
}

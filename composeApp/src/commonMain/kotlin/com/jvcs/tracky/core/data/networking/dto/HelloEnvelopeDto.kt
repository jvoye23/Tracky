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

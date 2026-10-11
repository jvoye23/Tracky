package com.jvcs.tracky.core.data.dto.requests

import kotlinx.serialization.Serializable

/** [id] is the app user id. The server uses it only when this login creates the account. */
@Serializable
data class SocialLoginRequest(val idToken: String, val id: String)

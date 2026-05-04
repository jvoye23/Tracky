package com.jvcs.tracky.core.data.dto.requests

import kotlinx.serialization.Serializable

@Serializable
data class SocialLoginRequest(
    val idToken: String,
)

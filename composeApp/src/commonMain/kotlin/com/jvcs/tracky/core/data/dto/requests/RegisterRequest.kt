package com.jvcs.tracky.core.data.dto.requests

import kotlinx.serialization.Serializable

/** [id] is the app user id, which becomes the account's id; see `AppUserIdStore`. */
@Serializable
data class RegisterRequest(
    val id: String,
    val email: String,
    val username: String,
    val password: String,
)

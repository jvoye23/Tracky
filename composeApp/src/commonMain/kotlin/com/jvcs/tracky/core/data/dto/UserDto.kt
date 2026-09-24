package com.jvcs.tracky.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val username: String,
    val hasVerifiedEmail: Boolean,
    val profilePictureUrl: String? = null,
)

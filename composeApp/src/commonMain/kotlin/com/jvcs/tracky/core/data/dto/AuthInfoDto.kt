package com.jvcs.tracky.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuthInfoDto(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto,
)

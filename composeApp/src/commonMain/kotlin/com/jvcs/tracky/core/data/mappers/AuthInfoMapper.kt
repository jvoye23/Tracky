package com.jvcs.tracky.core.data.mappers

import com.jvcs.tracky.core.data.dto.AuthInfoDto
import com.jvcs.tracky.core.data.dto.UserDto
import com.jvcs.tracky.core.domain.auth.AuthInfo
import com.jvcs.tracky.core.domain.auth.User

fun AuthInfoDto.toDomain(): AuthInfo =
    AuthInfo(
        accessToken = accessToken,
        refreshToken = refreshToken,
        user = user.toDomain(),
    )

fun UserDto.toDomain(): User =
    User(
        id = id,
        email = email,
        username = username,
        hasVerifiedEmail = hasVerifiedEmail,
        profilePictureUrl = profilePictureUrl,
    )

fun AuthInfo.toSerializable(): AuthInfoDto =
    AuthInfoDto(
        accessToken = accessToken,
        refreshToken = refreshToken,
        user = user.toSerializable(),
    )

fun User.toSerializable(): UserDto =
    UserDto(
        id = id,
        email = email,
        username = username,
        hasVerifiedEmail = hasVerifiedEmail,
        profilePictureUrl = profilePictureUrl,
    )

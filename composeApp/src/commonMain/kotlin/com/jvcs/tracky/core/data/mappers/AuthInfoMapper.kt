package com.jvcs.tracky.core.data.mappers

import com.jvcs.tracky.core.data.dto.AuthInfoSerializable
import com.jvcs.tracky.core.data.dto.UserSerializable
import com.jvcs.tracky.core.domain.auth.AuthInfo
import com.jvcs.tracky.core.domain.auth.User

fun AuthInfoSerializable.toDomain(): AuthInfo =
    AuthInfo(
        accessToken = accessToken,
        refreshToken = refreshToken,
        user = user.toDomain(),
    )

fun UserSerializable.toDomain(): User =
    User(
        id = id,
        email = email,
        username = username,
        hasVerifiedEmail = hasVerifiedEmail,
        profilePictureUrl = profilePictureUrl,
    )

fun AuthInfo.toSerializable(): AuthInfoSerializable =
    AuthInfoSerializable(
        accessToken = accessToken,
        refreshToken = refreshToken,
        user = user.toSerializable(),
    )

fun User.toSerializable(): UserSerializable =
    UserSerializable(
        id = id,
        email = email,
        username = username,
        hasVerifiedEmail = hasVerifiedEmail,
        profilePictureUrl = profilePictureUrl,
    )

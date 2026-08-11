package com.jvcs.tracky.core.domain.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A session tests control. Defaults to a logged-in user, because screens that combine their state
 * with [observeAuthInfo] render nothing at all while the session is null.
 */
internal class FakeSessionStorage(
    initial: AuthInfo? = authInfo()
) : SessionStorage {

    private val authInfoFlow = MutableStateFlow(initial)

    val current: AuthInfo? get() = authInfoFlow.value

    override fun observeAuthInfo(): Flow<AuthInfo?> = authInfoFlow

    override suspend fun set(info: AuthInfo?) {
        authInfoFlow.value = info
    }
}

internal fun authInfo(
    accessToken: String = "access-token",
    refreshToken: String = "refresh-token",
    user: User = user()
) = AuthInfo(
    accessToken = accessToken,
    refreshToken = refreshToken,
    user = user
)

internal fun user(
    id: String = "user-1",
    email: String = "user@example.com",
    username: String = "user",
    hasVerifiedEmail: Boolean = true
) = User(
    id = id,
    email = email,
    username = username,
    hasVerifiedEmail = hasVerifiedEmail
)

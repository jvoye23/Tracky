package com.jvcs.tracky.core.domain.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Holds the session in memory; [set] emits to every observer, like the DataStore one does.
 *
 * Starts signed out, which is how the app starts. Screens that combine their state with
 * [observeAuthInfo] render nothing while the session is null, so their tests pass `authInfo()`.
 */
class FakeSessionStorage(initial: AuthInfo? = null) : SessionStorage {

    private val authInfoFlow = MutableStateFlow(initial)

    val current: AuthInfo? get() = authInfoFlow.value

    override fun observeAuthInfo(): Flow<AuthInfo?> = authInfoFlow

    override suspend fun set(info: AuthInfo?) {
        authInfoFlow.value = info
    }
}

fun authInfo(
    userId: String = "user-1",
    accessToken: String = "access-token",
    refreshToken: String = "refresh-token",
    user: User = user(id = userId),
): AuthInfo =
    AuthInfo(
        accessToken = accessToken,
        refreshToken = refreshToken,
        user = user,
    )

fun user(
    id: String = "user-1",
    email: String = "user@example.com",
    username: String = "user",
    hasVerifiedEmail: Boolean = true,
): User =
    User(
        id = id,
        email = email,
        username = username,
        hasVerifiedEmail = hasVerifiedEmail,
    )

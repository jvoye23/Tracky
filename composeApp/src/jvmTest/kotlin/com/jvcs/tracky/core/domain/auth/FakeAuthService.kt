package com.jvcs.tracky.core.domain.auth

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result

/**
 * Records the auth calls a screen makes. Only logout is exercised today, so the credential
 * endpoints just succeed; give them recorders when a test needs them.
 */
internal class FakeAuthService : AuthService {

    var logoutResult: EmptyResult<DataError.Remote> = Result.Success(Unit)
    val logoutCalls = mutableListOf<String>()
    var clearTokenCacheCount = 0
        private set

    override suspend fun logout(refreshToken: String): EmptyResult<DataError.Remote> {
        logoutCalls += refreshToken
        return logoutResult
    }

    override fun clearTokenCache() {
        clearTokenCacheCount++
    }

    override suspend fun login(email: String, password: String): Result<AuthInfo, DataError.Remote> =
        Result.Success(authInfo())

    override suspend fun register(email: String, name: String, password: String): Result<AuthInfo, DataError.Remote> =
        Result.Success(authInfo())

    override suspend fun loginWithGoogle(idToken: String): Result<AuthInfo, DataError.Remote> =
        Result.Success(authInfo())

    override suspend fun loginWithApple(idToken: String): Result<AuthInfo, DataError.Remote> =
        Result.Success(authInfo())

    override suspend fun resendVerificationEmail(email: String): EmptyResult<DataError.Remote> = Result.Success(Unit)
    override suspend fun verifyEmail(token: String): EmptyResult<DataError.Remote> = Result.Success(Unit)
    override suspend fun forgotPassword(email: String): EmptyResult<DataError.Remote> = Result.Success(Unit)
    override suspend fun resetPassword(newPassword: String, token: String): EmptyResult<DataError.Remote> =
        Result.Success(Unit)
}

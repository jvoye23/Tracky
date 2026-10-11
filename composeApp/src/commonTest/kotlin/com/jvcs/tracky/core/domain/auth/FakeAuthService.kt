package com.jvcs.tracky.core.domain.auth

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result

/**
 * Records the auth calls a screen makes. The credential endpoints succeed unless a test sets
 * their result.
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

    var loginResult: Result<AuthInfo, DataError.Remote> = Result.Success(authInfo())
    val loginCalls = mutableListOf<Pair<String, String>>()

    override suspend fun login(email: String, password: String): Result<AuthInfo, DataError.Remote> {
        loginCalls += email to password
        return loginResult
    }

    var registerResult: Result<AuthInfo, DataError.Remote> = Result.Success(authInfo())
    val registerCalls = mutableListOf<Triple<String, String, String>>()

    override suspend fun register(
        email: String,
        name: String,
        password: String,
    ): Result<AuthInfo, DataError.Remote> {
        registerCalls += Triple(email, name, password)
        return registerResult
    }

    override suspend fun loginWithGoogle(idToken: String): Result<AuthInfo, DataError.Remote> =
        Result.Success(authInfo())

    override suspend fun loginWithApple(idToken: String): Result<AuthInfo, DataError.Remote> =
        Result.Success(authInfo())

    var resendVerificationEmailResult: EmptyResult<DataError.Remote> = Result.Success(Unit)
    val resendVerificationEmailCalls = mutableListOf<String>()

    override suspend fun resendVerificationEmail(email: String): EmptyResult<DataError.Remote> {
        resendVerificationEmailCalls += email
        return resendVerificationEmailResult
    }

    var verifyEmailResult: EmptyResult<DataError.Remote> = Result.Success(Unit)
    val verifyEmailCalls = mutableListOf<String>()

    override suspend fun verifyEmail(token: String): EmptyResult<DataError.Remote> {
        verifyEmailCalls += token
        return verifyEmailResult
    }

    var forgotPasswordResult: EmptyResult<DataError.Remote> = Result.Success(Unit)
    val forgotPasswordCalls = mutableListOf<String>()

    override suspend fun forgotPassword(email: String): EmptyResult<DataError.Remote> {
        forgotPasswordCalls += email
        return forgotPasswordResult
    }

    var resetPasswordResult: EmptyResult<DataError.Remote> = Result.Success(Unit)
    val resetPasswordCalls = mutableListOf<Pair<String, String>>()

    override suspend fun resetPassword(newPassword: String, token: String): EmptyResult<DataError.Remote> {
        resetPasswordCalls += newPassword to token
        return resetPasswordResult
    }
}

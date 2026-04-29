package com.jvcs.tracky.core.domain.auth

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result

interface AuthService {
    suspend fun login(email: String, password: String): Result<AuthInfo, DataError.Network>
    suspend fun register(email: String, name: String, password: String): Result<AuthInfo, DataError.Network>
    suspend fun loginWithGoogle(idToken: String): Result<AuthInfo, DataError.Network>
    suspend fun loginWithApple(idToken: String): Result<AuthInfo, DataError.Network>
    suspend fun resendVerificationEmail(email: String): EmptyResult<DataError.Network>
    suspend fun verifyEmail(token: String): EmptyResult<DataError.Network>
    suspend fun forgotPassword(email: String): EmptyResult<DataError.Network>
    suspend fun resetPassword(newPassword: String, token: String): EmptyResult<DataError.Network>
    suspend fun logout(refreshToken: String): EmptyResult<DataError.Network>
}

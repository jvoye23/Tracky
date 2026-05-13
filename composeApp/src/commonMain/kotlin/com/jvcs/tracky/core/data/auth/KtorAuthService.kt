package com.jvcs.tracky.core.data.auth

import com.jvcs.tracky.core.data.dto.AuthInfoSerializable
import com.jvcs.tracky.core.data.dto.requests.EmailRequest
import com.jvcs.tracky.core.data.dto.requests.LoginRequest
import com.jvcs.tracky.core.data.dto.requests.RefreshRequest
import com.jvcs.tracky.core.data.dto.requests.RegisterRequest
import com.jvcs.tracky.core.data.dto.requests.ResetPasswordRequest
import com.jvcs.tracky.core.data.dto.requests.SocialLoginRequest
import com.jvcs.tracky.core.data.mappers.toDomain
import com.jvcs.tracky.core.data.networking.get
import com.jvcs.tracky.core.data.networking.post
import com.jvcs.tracky.core.domain.auth.AuthInfo
import com.jvcs.tracky.core.domain.auth.AuthService
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.map
import com.jvcs.tracky.core.domain.util.onSuccess
import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.authProviders
import io.ktor.client.plugins.auth.providers.BearerAuthProvider

class KtorAuthService(
    private val httpClient: HttpClient
) : AuthService {

    override fun clearTokenCache() {
        httpClient.authProviders
            .filterIsInstance<BearerAuthProvider>()
            .firstOrNull()
            ?.clearToken()
    }

    override suspend fun login(
        email: String,
        password: String
    ): Result<AuthInfo, DataError.Network> {
        return httpClient.post<LoginRequest, AuthInfoSerializable>(
            route = "/api/auth/login",
            body = LoginRequest(email = email, password = password)
        ).map { it.toDomain() }
            .onSuccess { clearTokenCache() }
    }

    override suspend fun register(
        email: String,
        name: String,
        password: String
    ): Result<AuthInfo, DataError.Network> {
        return httpClient.post<RegisterRequest, AuthInfoSerializable>(
            route = "/api/auth/register",
            body = RegisterRequest(email = email, name = name, password = password)
        ).map { it.toDomain() }
            .onSuccess { clearTokenCache() }
    }

    override suspend fun loginWithGoogle(idToken: String): Result<AuthInfo, DataError.Network> {
        return httpClient.post<SocialLoginRequest, AuthInfoSerializable>(
            route = "/api/auth/google",
            body = SocialLoginRequest(idToken = idToken)
        ).map { it.toDomain() }
            .onSuccess { clearTokenCache() }
    }

    override suspend fun loginWithApple(idToken: String): Result<AuthInfo, DataError.Network> {
        return httpClient.post<SocialLoginRequest, AuthInfoSerializable>(
            route = "/api/auth/apple",
            body = SocialLoginRequest(idToken = idToken)
        ).map { it.toDomain() }
            .onSuccess { clearTokenCache() }
    }

    override suspend fun resendVerificationEmail(email: String): EmptyResult<DataError.Network> {
        return httpClient.post<EmailRequest, Unit>(
            route = "/api/auth/resend-verification",
            body = EmailRequest(email)
        )
    }

    override suspend fun verifyEmail(token: String): EmptyResult<DataError.Network> {
        return httpClient.get(
            route = "/api/auth/verify",
            queryParams = mapOf("token" to token)
        )
    }

    override suspend fun forgotPassword(email: String): EmptyResult<DataError.Network> {
        return httpClient.post<EmailRequest, Unit>(
            route = "/api/auth/forgot-password",
            body = EmailRequest(email)
        )
    }

    override suspend fun resetPassword(
        newPassword: String,
        token: String
    ): EmptyResult<DataError.Network> {
        return httpClient.post<ResetPasswordRequest, Unit>(
            route = "/api/auth/reset-password",
            body = ResetPasswordRequest(newPassword = newPassword, token = token)
        )
    }

    override suspend fun logout(refreshToken: String): EmptyResult<DataError.Network> {
        return httpClient.post<RefreshRequest, Unit>(
            route = "/api/auth/logout",
            body = RefreshRequest(refreshToken)
        )
    }
}

package com.jvcs.tracky.core.domain.auth

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result

expect class SocialAuthProvider() {
    suspend fun signInWithGoogle(): Result<String, DataError.Network>
    suspend fun signInWithApple(): Result<String, DataError.Network>
}

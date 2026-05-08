package com.jvcs.tracky.core.domain.auth

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result

actual class SocialAuthProvider {
    actual suspend fun signInWithGoogle(): Result<String, DataError.Network> {
        // TODO: integrate desktop Google sign-in (browser flow) and return the Google ID token.
        return Result.Error(DataError.Network.SERVER_ERROR)
    }

    actual suspend fun signInWithApple(): Result<String, DataError.Network> {
        // TODO: integrate desktop Sign in with Apple (browser flow) and return the identity token.
        return Result.Error(DataError.Network.SERVER_ERROR)
    }
}

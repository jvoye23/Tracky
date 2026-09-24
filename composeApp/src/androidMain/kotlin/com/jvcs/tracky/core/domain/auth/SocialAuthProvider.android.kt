package com.jvcs.tracky.core.domain.auth

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result

actual class SocialAuthProvider {

    actual suspend fun signInWithGoogle(): Result<String, DataError.Remote> {
        // Not implemented yet (#144): integrate Credential Manager / Google Sign-In SDK and return the Google ID token.
        return Result.Error(DataError.Remote.SERVER_ERROR)
    }

    actual suspend fun signInWithApple(): Result<String, DataError.Remote> {
        // Not implemented yet (#144): integrate Sign in with Apple via web flow on Android.
        return Result.Error(DataError.Remote.SERVER_ERROR)
    }
}

package com.jvcs.tracky.core.domain.lifecycle

import kotlinx.coroutines.flow.Flow

/**
 * Emits whether the app is currently in the foreground. Implemented per platform (Android
 * ProcessLifecycleOwner, iOS UIApplication notifications, JVM always-foreground stub).
 */
expect class AppLifecycleObserver {
    val isInForeground: Flow<Boolean>
}

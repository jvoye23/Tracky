package com.jvcs.tracky.core.domain.lifecycle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

actual class AppLifecycleObserver {
    // Desktop is treated as always-foreground for now.
    actual val isInForeground: Flow<Boolean> = flowOf(true)
}

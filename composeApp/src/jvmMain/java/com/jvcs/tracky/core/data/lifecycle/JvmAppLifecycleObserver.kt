package com.jvcs.tracky.core.data.lifecycle

import com.jvcs.tracky.core.domain.lifecycle.AppLifecycleObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class JvmAppLifecycleObserver : AppLifecycleObserver {

    // Desktop is treated as always-foreground for now.
    override val isInForeground: Flow<Boolean> = flowOf(true)
}

package com.jvcs.tracky.core.domain.lifecycle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** In the foreground by default, as a single value that then completes, like the JVM implementation. */
class FakeAppLifecycleObserver(override val isInForeground: Flow<Boolean> = flowOf(true)) : AppLifecycleObserver

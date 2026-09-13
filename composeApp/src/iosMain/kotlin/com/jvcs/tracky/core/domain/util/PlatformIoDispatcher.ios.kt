package com.jvcs.tracky.core.domain.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// Native has no IO pool. Default is the documented substitute, and every caller either serialises
// itself with limitedParallelism(1) or is a one-shot read.
actual val platformIoDispatcher: CoroutineDispatcher = Dispatchers.Default

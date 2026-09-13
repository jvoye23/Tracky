package com.jvcs.tracky.core.domain.util

import kotlinx.coroutines.CoroutineDispatcher

/**
 * The dispatcher for blocking work - database writes, file reads.
 *
 * platformIoDispatcher exists because Dispatchers.IO is JVM-only: on Kotlin/Native it is internal, so naming it in commonMain breaks
 * the iOS build. Native has no thread pool sized for blocking calls either, because it has no
 * blocking calls to size for - Dispatchers.Default is the intended target there.
 */
expect val platformIoDispatcher: CoroutineDispatcher

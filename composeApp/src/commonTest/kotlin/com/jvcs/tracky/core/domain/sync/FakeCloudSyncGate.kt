package com.jvcs.tracky.core.domain.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A gate a test can open and close. Open by default: most data-layer tests are about what happens
 * once sync is on, and the ones about the free tier close it explicitly.
 */
class FakeCloudSyncGate(isEnabled: Boolean = true) : CloudSyncGate {

    val enabled = MutableStateFlow(isEnabled)

    override fun observeIsEnabled(): Flow<Boolean> = enabled

    override suspend fun isEnabled(): Boolean = enabled.value
}

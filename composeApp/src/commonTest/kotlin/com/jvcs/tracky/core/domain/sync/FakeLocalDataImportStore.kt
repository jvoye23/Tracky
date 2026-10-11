package com.jvcs.tracky.core.domain.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeLocalDataImportStore(initial: Boolean = false) : LocalDataImportStore {

    private val pending = MutableStateFlow(initial)

    val isPending: Boolean get() = pending.value

    override fun observeIsPending(): Flow<Boolean> = pending

    override suspend fun setPending(isPending: Boolean) {
        pending.value = isPending
    }

    val refused = MutableStateFlow<List<String>>(emptyList())

    override fun observeRefusedProjects(): Flow<List<String>> = refused

    override suspend fun setRefusedProjects(titles: List<String>) {
        refused.value = titles
    }
}

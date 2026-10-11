package com.jvcs.tracky.core.domain.identity

/** Holds the id in memory. */
class FakeAppUserIdStore(var id: String = "local-id") : AppUserIdStore {

    override suspend fun get(): String = id

    override suspend fun replace(id: String) {
        this.id = id
    }
}

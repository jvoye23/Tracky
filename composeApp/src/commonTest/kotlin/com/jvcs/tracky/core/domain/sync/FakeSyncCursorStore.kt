package com.jvcs.tracky.core.domain.sync

/** An in-memory cursor, with [clearCount] so a test can assert logout actually reached it. */
internal class FakeSyncCursorStore(private var cursor: Long? = null) : SyncCursorStore {

    var clearCount = 0
        private set

    override suspend fun cursor(): Long? = cursor

    override suspend fun setCursor(cursor: Long) {
        if (this.cursor == null || cursor > this.cursor!!) this.cursor = cursor
    }

    override suspend fun clear() {
        clearCount++
        cursor = null
    }
}

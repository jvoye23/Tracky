package com.jvcs.tracky.core.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.jvcs.tracky.core.domain.sync.SyncCursorStore
import kotlinx.coroutines.flow.first

/**
 * Keeps the cursor in the same `tracky_prefs` store as the session and the device id.
 *
 * Not in Room: it describes the *state of syncing*, not user data, and it has to survive
 * `deleteAllProjects()` being wrong about it — a cursor living in a table that logout truncates
 * would be cleared by accident rather than on purpose.
 */
class DataStoreSyncCursorStore(
    private val dataStore: DataStore<Preferences>
) : SyncCursorStore {

    private val cursorKey = longPreferencesKey(KEY_SYNC_CURSOR)

    override suspend fun cursor(): Long? = dataStore.data.first()[cursorKey]

    override suspend fun setCursor(cursor: Long) {
        dataStore.edit { prefs ->
            // Never go backwards. Two pulls racing would otherwise let the slower one, holding an
            // older cursor, rewind the faster one and replay a change set that already landed.
            val current = prefs[cursorKey]
            if (current == null || cursor > current) prefs[cursorKey] = cursor
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(cursorKey) }
    }

    private companion object {
        const val KEY_SYNC_CURSOR = "KEY_SYNC_CURSOR"
    }
}

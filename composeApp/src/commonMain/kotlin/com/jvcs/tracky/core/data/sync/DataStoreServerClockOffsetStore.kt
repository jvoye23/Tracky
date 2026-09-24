package com.jvcs.tracky.core.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.jvcs.tracky.core.domain.util.ServerClockOffsetStore
import kotlinx.coroutines.flow.first

/**
 * The measured clock offset, in the same `tracky_prefs` store as the session and the cursor.
 *
 * Persisted so a cold start with no network still renders another device's timer correctly:
 * the skew between two physical clocks does not change meaningfully between launches, so last
 * session's measurement is a far better guess than assuming zero.
 */
class DataStoreServerClockOffsetStore(private val dataStore: DataStore<Preferences>) : ServerClockOffsetStore {

    private val offsetKey = longPreferencesKey(KEY_SERVER_CLOCK_OFFSET_MS)

    override suspend fun offsetMillis(): Long? = dataStore.data.first()[offsetKey]

    override suspend fun setOffsetMillis(millis: Long) {
        dataStore.edit { it[offsetKey] = millis }
    }

    private companion object {
        const val KEY_SERVER_CLOCK_OFFSET_MS = "KEY_SERVER_CLOCK_OFFSET_MS"
    }
}

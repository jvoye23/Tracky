package com.jvcs.tracky.core.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.jvcs.tracky.core.domain.sync.LocalDataImportStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Keeps the flag in the same `tracky_prefs` store as the session, and for the same reason the sync
 * cursor lives there rather than in Room: it describes the state of syncing, not user data.
 */
class DataStoreLocalDataImportStore(private val dataStore: DataStore<Preferences>) : LocalDataImportStore {

    private val pendingKey = booleanPreferencesKey(KEY_LOCAL_DATA_IMPORT_PENDING)
    private val refusedKey = stringSetPreferencesKey(KEY_LOCAL_DATA_IMPORT_REFUSED)

    override fun observeIsPending(): Flow<Boolean> =
        dataStore.data
            .map { prefs -> prefs[pendingKey] ?: false }
            .distinctUntilChanged()

    override suspend fun setPending(isPending: Boolean) {
        dataStore.edit { it[pendingKey] = isPending }
    }

    override fun observeRefusedProjects(): Flow<List<String>> =
        dataStore.data
            .map { prefs -> prefs[refusedKey].orEmpty().sorted() }
            .distinctUntilChanged()

    override suspend fun setRefusedProjects(titles: List<String>) {
        dataStore.edit { it[refusedKey] = titles.toSet() }
    }

    private companion object {
        const val KEY_LOCAL_DATA_IMPORT_PENDING = "KEY_LOCAL_DATA_IMPORT_PENDING"
        const val KEY_LOCAL_DATA_IMPORT_REFUSED = "KEY_LOCAL_DATA_IMPORT_REFUSED"
    }
}

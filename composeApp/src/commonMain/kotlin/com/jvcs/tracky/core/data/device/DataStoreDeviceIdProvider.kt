package com.jvcs.tracky.core.data.device

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jvcs.tracky.core.domain.device.DeviceIdProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

/**
 * Keeps the device id in the same `tracky_prefs` store the session lives in.
 *
 * Not in Room: the id has to be readable before the database is usable, and it must survive
 * `deleteAllProjects()` on logout, which the project tables do not.
 */
class DataStoreDeviceIdProvider(
    private val dataStore: DataStore<Preferences>
) : DeviceIdProvider {

    private val deviceIdKey = stringPreferencesKey(KEY_DEVICE_ID)

    // Two callers racing on first launch would otherwise mint two ids and persist whichever wrote
    // last, after both had already handed a different one to their caller.
    private val mutex = Mutex()
    private var cached: String? = null

    override suspend fun deviceId(): String {
        cached?.let { return it }
        return mutex.withLock {
            cached?.let { return@withLock it }
            val id = dataStore.data.first()[deviceIdKey] ?: mint()
            cached = id
            id
        }
    }

    private suspend fun mint(): String {
        val minted = Uuid.random().toString()
        // Re-read inside edit: another process (the Android sync worker runs in the same one, but
        // the widget host does not) may have written a value since the read above.
        var winner = minted
        dataStore.edit { prefs ->
            val existing = prefs[deviceIdKey]
            if (existing != null) {
                winner = existing
            } else {
                prefs[deviceIdKey] = minted
            }
        }
        return winner
    }

    private companion object {
        const val KEY_DEVICE_ID = "KEY_DEVICE_ID"
    }
}

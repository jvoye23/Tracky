package com.jvcs.tracky.core.data.identity

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jvcs.tracky.core.domain.identity.AppUserIdStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

/**
 * Keeps the app user id in `tracky_prefs`, next to the session.
 *
 * It is not stored in Room, because it is read at app start to configure RevenueCat, before the
 * database is in use. Logout's `deleteAllProjects()` must not touch it; [replace] swaps it instead.
 */
class DataStoreAppUserIdStore(private val dataStore: DataStore<Preferences>) : AppUserIdStore {

    private val appUserIdKey = stringPreferencesKey(KEY_APP_USER_ID)

    // Two callers racing on first launch would otherwise mint two ids and persist whichever wrote
    // last, after both had already handed a different one to their caller.
    private val mutex = Mutex()
    private var cached: String? = null

    override suspend fun get(): String {
        cached?.let { return it }
        return mutex.withLock {
            cached?.let { return@withLock it }
            val id = dataStore.data.first()[appUserIdKey] ?: mint()
            cached = id
            id
        }
    }

    override suspend fun replace(id: String) {
        mutex.withLock {
            dataStore.edit { it[appUserIdKey] = id }
            cached = id
        }
    }

    private suspend fun mint(): String {
        val minted = Uuid.random().toString()
        // Re-read inside edit, in case another writer stored a value since the read above.
        var winner = minted
        dataStore.edit { prefs ->
            val existing = prefs[appUserIdKey]
            if (existing != null) {
                winner = existing
            } else {
                prefs[appUserIdKey] = minted
            }
        }
        return winner
    }

    private companion object {
        const val KEY_APP_USER_ID = "KEY_APP_USER_ID"
    }
}

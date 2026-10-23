package com.jvcs.tracky.features.project.data.project

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.jvcs.tracky.features.project.domain.project.FirstProjectStore

/**
 * Keeps the claim in the same `tracky_prefs` store as the session. Read and set in one edit, which
 * DataStore serializes, so two projects saved at once still yield a single claim.
 */
class DataStoreFirstProjectStore(private val dataStore: DataStore<Preferences>) : FirstProjectStore {

    private val claimedKey = booleanPreferencesKey(KEY_FIRST_PROJECT_CLAIMED)

    override suspend fun claimFirst(): Boolean {
        var claimed = false
        dataStore.edit { prefs ->
            claimed = prefs[claimedKey] != true
            prefs[claimedKey] = true
        }
        return claimed
    }

    private companion object {
        const val KEY_FIRST_PROJECT_CLAIMED = "KEY_FIRST_PROJECT_CLAIMED"
    }
}

package com.jvcs.tracky.features.onboarding.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jvcs.tracky.features.onboarding.domain.OnboardingStatus
import com.jvcs.tracky.features.onboarding.domain.OnboardingStatusStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Keeps the status in the same `tracky_prefs` store as the session.
 *
 * Stored by name, so reordering the enum cannot change what an installed app reads back. A name this
 * build does not know reads as [OnboardingStatus.NOT_STARTED]: showing onboarding again is
 * recoverable, opening on the wrong screen is not.
 */
class DataStoreOnboardingStatusStore(private val dataStore: DataStore<Preferences>) : OnboardingStatusStore {

    private val statusKey = stringPreferencesKey(KEY_ONBOARDING_STATUS)

    override fun observe(): Flow<OnboardingStatus> =
        dataStore.data
            .map { prefs ->
                OnboardingStatus.entries.firstOrNull { it.name == prefs[statusKey] } ?: OnboardingStatus.NOT_STARTED
            }.distinctUntilChanged()

    override suspend fun set(status: OnboardingStatus) {
        dataStore.edit { it[statusKey] = status.name }
    }

    private companion object {
        const val KEY_ONBOARDING_STATUS = "KEY_ONBOARDING_STATUS"
    }
}

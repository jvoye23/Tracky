package com.jvcs.tracky.core.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import co.touchlab.kermit.Logger
import com.jvcs.tracky.core.data.dto.AuthInfoDto
import com.jvcs.tracky.core.data.mappers.toDomain
import com.jvcs.tracky.core.data.mappers.toSerializable
import com.jvcs.tracky.core.domain.auth.AuthInfo
import com.jvcs.tracky.core.domain.auth.SessionStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class DataStoreSessionStorage(private val dataStore: DataStore<Preferences>) : SessionStorage {

    private val authInfoKey = stringPreferencesKey("KEY_AUTH_INFO")
    private val json = Json { ignoreUnknownKeys = true }

    override fun observeAuthInfo(): Flow<AuthInfo?> =
        dataStore.data.map { preferences ->
            preferences[authInfoKey]?.let {
                try {
                    json.decodeFromString<AuthInfoDto>(it).toDomain()
                } catch (exception: IllegalArgumentException) {
                    Logger.withTag("DataStoreSessionStorage").w(exception) {
                        "observeAuthInfo: ignored unreadable input (IllegalArgumentException)"
                    }
                    null
                }
            }
        }

    override suspend fun set(info: AuthInfo?) {
        if (info == null) {
            dataStore.edit { it.remove(authInfoKey) }
            return
        }
        val serialized = json.encodeToString(AuthInfoDto.serializer(), info.toSerializable())
        dataStore.edit { prefs -> prefs[authInfoKey] = serialized }
    }
}

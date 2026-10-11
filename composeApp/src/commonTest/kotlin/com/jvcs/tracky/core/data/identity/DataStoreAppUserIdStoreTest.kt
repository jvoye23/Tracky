package com.jvcs.tracky.core.data.identity

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.uuid.Uuid

/** The id is minted once, survives a restart, and changes only when told to. */
class DataStoreAppUserIdStoreTest {

    private val dataStore = InMemoryPreferencesDataStore()

    @Test
    fun theFirstReadMintsAUuid() =
        runTest {
            val id = DataStoreAppUserIdStore(dataStore).get()

            assertThat(Uuid.parse(id).toString()).isEqualTo(id)
        }

    @Test
    fun theIdSurvivesARestart() =
        runTest {
            val first = DataStoreAppUserIdStore(dataStore).get()

            val afterRestart = DataStoreAppUserIdStore(dataStore).get()

            assertThat(afterRestart).isEqualTo(first)
        }

    @Test
    fun concurrentFirstReadsAgreeOnOneId() =
        runTest {
            val store = DataStoreAppUserIdStore(dataStore)

            val ids = List(10) { async { store.get() } }.awaitAll()

            assertThat(ids.toSet().size).isEqualTo(1)
        }

    @Test
    fun replaceAdoptsTheAccountIdAndPersistsIt() =
        runTest {
            DataStoreAppUserIdStore(dataStore).apply { get() }.replace("account-id")

            assertThat(DataStoreAppUserIdStore(dataStore).get()).isEqualTo("account-id")
        }

    @Test
    fun replaceOverridesTheCachedId() =
        runTest {
            val store = DataStoreAppUserIdStore(dataStore)
            val before = store.get()

            store.replace("fresh-id")

            assertThat(store.get()).isNotEqualTo(before)
            assertThat(store.get()).isEqualTo("fresh-id")
        }

    @Test
    fun anIdWrittenBetweenTheReadAndTheMintWins() =
        runTest {
            // Another writer stored an id after this store's read came back empty.
            dataStore.edit { it[stringPreferencesKey("KEY_APP_USER_ID")] = "written-meanwhile" }

            val id = DataStoreAppUserIdStore(StaleFirstRead(dataStore)).get()

            assertThat(id).isEqualTo("written-meanwhile")
            assertThat(dataStore.data.first()[stringPreferencesKey("KEY_APP_USER_ID")]).isEqualTo("written-meanwhile")
        }
}

/** Answers the first read with an empty snapshot, as if the write it races landed just after it. */
private class StaleFirstRead(private val delegate: DataStore<Preferences>) : DataStore<Preferences> {
    private var served = false

    override val data: Flow<Preferences> =
        flow {
            if (!served) {
                served = true
                emit(emptyPreferences())
            } else {
                emitAll(delegate.data)
            }
        }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        delegate.updateData(transform)
}

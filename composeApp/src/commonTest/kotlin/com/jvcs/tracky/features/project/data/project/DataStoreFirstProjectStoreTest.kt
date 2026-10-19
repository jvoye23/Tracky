package com.jvcs.tracky.features.project.data.project

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.jvcs.tracky.core.data.createTestDataStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

internal class DataStoreFirstProjectStoreTest {

    @Test
    fun onlyTheFirstClaimSucceeds() =
        runTest {
            val store = DataStoreFirstProjectStore(createTestDataStore())

            assertThat(store.claimFirst()).isTrue()
            assertThat(store.claimFirst()).isFalse()
        }

    @Test
    fun theClaimSurvivesAFreshStore() =
        runTest {
            val dataStore = createTestDataStore()
            DataStoreFirstProjectStore(dataStore).claimFirst()

            // What a process restart sees: nothing cached, only what was persisted.
            assertThat(DataStoreFirstProjectStore(dataStore).claimFirst()).isFalse()
        }

    @Test
    fun concurrentClaimsHaveExactlyOneWinner() =
        runTest {
            val store = DataStoreFirstProjectStore(createTestDataStore())

            val claims = List(5) { async { store.claimFirst() } }.awaitAll()

            assertThat(claims).containsExactlyInAnyOrder(true, false, false, false, false)
        }
}

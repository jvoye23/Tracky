package com.jvcs.tracky.core.data.sync

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.jvcs.tracky.core.data.createTestDataStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

internal class DataStoreSyncCursorStoreTest {

    @Test
    fun aDeviceThatHasNeverPulledHasNoCursor() =
        runTest {
            assertThat(DataStoreSyncCursorStore(createTestDataStore()).cursor()).isNull()
        }

    @Test
    fun theCursorRoundTrips() =
        runTest {
            val store = DataStoreSyncCursorStore(createTestDataStore())

            store.setCursor(84_213)

            assertThat(store.cursor()).isEqualTo(84_213L)
        }

    @Test
    fun aFreshStoreReadsThePersistedCursorBack() =
        runTest {
            val dataStore = createTestDataStore()
            DataStoreSyncCursorStore(dataStore).setCursor(84_213)

            // No in-memory cache to fall back on, so this is what survives a process restart. A
            // cursor that reset on launch would make every cold start a full resync.
            assertThat(DataStoreSyncCursorStore(dataStore).cursor()).isEqualTo(84_213L)
        }

    @Test
    fun theCursorNeverGoesBackwards() =
        runTest {
            val store = DataStoreSyncCursorStore(createTestDataStore())
            store.setCursor(84_213)

            store.setCursor(84_000)

            // Two pulls racing: the slower one holds an older cursor, and letting it rewind would
            // replay a change set that already landed.
            assertThat(store.cursor()).isEqualTo(84_213L)
        }

    @Test
    fun concurrentAdvancesSettleOnTheHighest() =
        runTest {
            val store = DataStoreSyncCursorStore(createTestDataStore())

            (1..16).map { async { store.setCursor(it.toLong()) } }.awaitAll()

            assertThat(store.cursor()).isEqualTo(16L)
        }

    @Test
    fun clearingForgetsIt() =
        runTest {
            val store = DataStoreSyncCursorStore(createTestDataStore())
            store.setCursor(84_213)

            store.clear()

            // Logout. The next account has its own feed, and a stale cursor would skip everything
            // below that sequence number.
            assertThat(store.cursor()).isNull()
        }

    @Test
    fun aClearedStoreAcceptsALowerCursorAgain() =
        runTest {
            val store = DataStoreSyncCursorStore(createTestDataStore())
            store.setCursor(84_213)
            store.clear()

            store.setCursor(7)

            // The no-going-backwards guard must not outlive the cursor it was guarding, or the next
            // account could never sync at all.
            assertThat(store.cursor()).isEqualTo(7L)
        }
}

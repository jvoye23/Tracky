package com.jvcs.tracky.core.data.sync

import com.jvcs.tracky.core.data.createTestDataStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class DataStoreSyncCursorStoreTest {

    @Test
    fun aDeviceThatHasNeverPulledHasNoCursor() =
        runTest {
            assertNull(DataStoreSyncCursorStore(createTestDataStore()).cursor())
        }

    @Test
    fun theCursorRoundTrips() =
        runTest {
            val store = DataStoreSyncCursorStore(createTestDataStore())

            store.setCursor(84_213)

            assertEquals(84_213L, store.cursor())
        }

    @Test
    fun aFreshStoreReadsThePersistedCursorBack() =
        runTest {
            val dataStore = createTestDataStore()
            DataStoreSyncCursorStore(dataStore).setCursor(84_213)

            // No in-memory cache to fall back on, so this is what survives a process restart. A
            // cursor that reset on launch would make every cold start a full resync.
            assertEquals(84_213L, DataStoreSyncCursorStore(dataStore).cursor())
        }

    @Test
    fun theCursorNeverGoesBackwards() =
        runTest {
            val store = DataStoreSyncCursorStore(createTestDataStore())
            store.setCursor(84_213)

            store.setCursor(84_000)

            // Two pulls racing: the slower one holds an older cursor, and letting it rewind would
            // replay a change set that already landed.
            assertEquals(84_213L, store.cursor())
        }

    @Test
    fun concurrentAdvancesSettleOnTheHighest() =
        runTest {
            val store = DataStoreSyncCursorStore(createTestDataStore())

            (1..16).map { async { store.setCursor(it.toLong()) } }.awaitAll()

            assertEquals(16L, store.cursor())
        }

    @Test
    fun clearingForgetsIt() =
        runTest {
            val store = DataStoreSyncCursorStore(createTestDataStore())
            store.setCursor(84_213)

            store.clear()

            // Logout. The next account has its own feed, and a stale cursor would skip everything
            // below that sequence number.
            assertNull(store.cursor())
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
            assertEquals(7L, store.cursor())
        }
}

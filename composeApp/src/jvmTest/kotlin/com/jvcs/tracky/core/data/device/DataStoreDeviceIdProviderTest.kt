package com.jvcs.tracky.core.data.device

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.jvcs.tracky.core.data.createTestDataStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.uuid.Uuid

internal class DataStoreDeviceIdProviderTest {

    @Test
    fun theFirstCallMintsAUuid() =
        runTest {
            val provider = DataStoreDeviceIdProvider(createTestDataStore())

            val id = provider.deviceId()

            // Parses as a UUID, which is what the backend's `deviceId` validation expects.
            assertThat(Uuid.parse(id).toString()).isEqualTo(id)
        }

    @Test
    fun theSameInstanceReturnsTheSameIdEveryTime() =
        runTest {
            val provider = DataStoreDeviceIdProvider(createTestDataStore())

            assertThat(provider.deviceId()).isEqualTo(provider.deviceId())
        }

    @Test
    fun aFreshProviderReadsTheMintedIdBackRatherThanMintingAgain() =
        runTest {
            val store = createTestDataStore()
            val first = DataStoreDeviceIdProvider(store).deviceId()

            // A second provider has an empty cache, so the only way it can agree is by reading the
            // persisted value — which is what survives a process restart.
            assertThat(DataStoreDeviceIdProvider(store).deviceId()).isEqualTo(first)
        }

    @Test
    fun twoStoresAreTwoDevices() =
        runTest {
            val one = DataStoreDeviceIdProvider(createTestDataStore()).deviceId()
            val other = DataStoreDeviceIdProvider(createTestDataStore()).deviceId()

            assertThat(one != other).isTrue()
        }

    @Test
    fun concurrentFirstCallsAllSeeOneId() =
        runTest {
            val provider = DataStoreDeviceIdProvider(createTestDataStore())

            val ids = (1..16).map { async { provider.deviceId() } }.awaitAll()

            // A mint that raced would hand different callers different ids and persist only one of
            // them, leaving intervals stamped with an id no later launch recognises as its own.
            assertThat(ids.toSet().size).isEqualTo(1)
        }

    @Test
    fun concurrentFirstCallsOnSeparateProvidersAlsoSeeOneId() =
        runTest {
            val store = createTestDataStore()

            // No shared in-memory mutex here — this is the double-mint that only the re-read inside
            // `edit` can prevent.
            val ids = (1..8).map { async { DataStoreDeviceIdProvider(store).deviceId() } }.awaitAll()

            assertThat(ids.toSet().size).isEqualTo(1)
        }
}

package com.jvcs.tracky.core.data.purchases

import com.jvcs.tracky.core.domain.identity.AppUserIdStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Configures RevenueCat with the app user id from [AppUserIdStore], without blocking app start.
 *
 * The id has to be known *before* configuring: configured without one, RevenueCat starts an
 * anonymous customer, and the server cannot tie that customer's purchase to the account the user
 * registers later (ADR 0005). Reading the id is a DataStore read, so it suspends. Instead of blocking
 * the main thread on it, everything that uses `Purchases` waits for [awaitConfigured].
 */
class RevenueCatConfigurator(
    private val appUserIdStore: AppUserIdStore,
    private val applicationScope: CoroutineScope,
) {

    private val configured = CompletableDeferred<Unit>()
    private var started = false

    /** Call once at app start, right after Koin is up. */
    fun start() {
        if (started) return
        started = true

        applicationScope.launch {
            val appUserId = appUserIdStore.get()
            // On the main thread, where the SDK expects to set up its singleton.
            withContext(Dispatchers.Main) { configureRevenueCat(appUserId) }
            configured.complete(Unit)
        }
    }

    /** Suspends until RevenueCat is configured; returns at once afterwards. */
    suspend fun awaitConfigured() {
        configured.await()
    }
}

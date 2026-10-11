package com.jvcs.tracky.core.domain.identity

/**
 * The id of the user on this device: the RevenueCat customer id and, once an account exists, the
 * Tracky user id. It is one value for both.
 *
 * It exists before any account does. Onboarding sells Pro first and asks the user to sign up
 * afterwards, possibly months later. So the purchase needs an id to land on that the account can
 * take over unchanged. The client mints it on first launch and sends it with the sign-up, and the
 * server creates the account with it.
 *
 * Unlike the device id, this follows the user rather than the installation. Signing in to an
 * existing account adopts that account's id, and signing out switches to a fresh one, so the next
 * person's purchase is not credited to the previous account.
 */
interface AppUserIdStore {

    /**
     * The current id, minting and persisting one on first call. Safe to call concurrently; every
     * caller in a process gets the same value.
     */
    suspend fun get(): String

    /**
     * Stores [id]: the id of an existing account that just signed in, or a fresh one after the
     * session ended. See `AppUserIdentitySync` for when that happens relative to RevenueCat's switch.
     */
    suspend fun replace(id: String)
}
